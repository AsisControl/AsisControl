package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.academico.HistorialHorarioDtos.CampoHistorial;
import com.asiscontrol.dto.academico.HistorialHorarioDtos.FiltroHistorialHorario;
import com.asiscontrol.dto.academico.HistorialHorarioDtos.HistorialHorarioResponse;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.AulaRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * US-27: historial de la seccion Horarios. No tiene tabla propia: lee RegistroAuditoria, que ninguna
 * ruta permite editar ni eliminar, y traduce sus valores "clave=valor" a campos.
 */
@Service
@Transactional(readOnly = true)
public class HistorialHorarioService {

    private static final String ENTIDAD_HORARIO = "Horario";
    private static final String ENTIDAD_ASIGNACION = "AsignacionCurso";
    private static final String CAMPO_ASIGNACION_ID = "asignacionCursoId";
    private static final String CAMPO_AULA_ID = "aulaId";
    private static final String CAMPO_SECCION_ID = "seccionId";
    private static final String CAMPO_DOCENTE_ID = "docenteId";
    private static final String CAMPO_ANIO = "anio";
    private static final String CAMPO_CURSO = "curso";
    private static final String CAMPO_SECCION = "seccion";
    private static final String CAMPO_DOCENTE = "docente";
    private static final String CAMPO_AULA = "aula";
    private static final List<String> CAMPOS_HORARIO = List.of(
            CAMPO_CURSO, CAMPO_SECCION, CAMPO_DOCENTE, CAMPO_AULA, "dia", "horaInicio", "horaFin");
    private static final List<String> CAMPOS_ASIGNACION =
            List.of(CAMPO_DOCENTE, CAMPO_CURSO, CAMPO_SECCION, CAMPO_ANIO, "activo");
    private static final ZoneId ZONA_INSTITUCION = ZoneId.of("America/Lima");
    private static final int TAMANIO_MAXIMO = 100;
    /** Solo separa ante "clave=": un curso puede llamarse "Ciencia, Tecnologia y Ambiente". */
    private static final Pattern SEPARADOR_CAMPOS = Pattern.compile(",(?=[A-Za-z]+=)");

    private final RegistroAuditoriaRepository auditoriaRepository;
    private final AsignacionCursoRepository asignacionRepository;
    private final AulaRepository aulaRepository;
    private final AuditoriaService auditoria;

    public HistorialHorarioService(
            RegistroAuditoriaRepository auditoriaRepository,
            AsignacionCursoRepository asignacionRepository,
            AulaRepository aulaRepository,
            AuditoriaService auditoria) {
        this.auditoriaRepository = auditoriaRepository;
        this.asignacionRepository = asignacionRepository;
        this.aulaRepository = aulaRepository;
        this.auditoria = auditoria;
    }

    public PageResponse<HistorialHorarioResponse> listar(
            FiltroHistorialHorario filtro, int page, int size) {
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw new BusinessRuleException(
                    "FECHAS_INVALIDAS", "La fecha final debe ser igual o posterior a la inicial");
        }
        PageRequest pagina = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), TAMANIO_MAXIMO),
                Sort.by(Sort.Direction.DESC, "fechaHora", "id"));
        Page<RegistroAuditoria> registros =
                auditoriaRepository.findAll(especificacion(filtro), pagina);
        Page<ValoresRegistro> filas = registros.map(ValoresRegistro::leer);
        completarBloquesAntiguos(filas.getContent());
        Map<String, String> nombres = auditoria.nombresDeActores(registros);
        return PageResponse.from(filas, fila -> mapear(fila, nombres));
    }

    private Specification<RegistroAuditoria> especificacion(FiltroHistorialHorario filtro) {
        List<String> clavesContexto = clavesDeContexto(filtro);
        List<String> clavesAula = filtro.aulaId() == null
                ? List.of()
                : List.of(clave(CAMPO_AULA_ID, filtro.aulaId()));
        List<String> clavesAsignacion = clavesContexto.isEmpty()
                ? List.of()
                : asignacionRepository
                        .buscarIdsPorContexto(
                                filtro.anioAcademico(), filtro.seccionId(), filtro.docenteId())
                        .stream()
                        .map(id -> clave(CAMPO_ASIGNACION_ID, id))
                        .toList();
        return (root, query, builder) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(builder.equal(root.get("resultado"), ResultadoAuditoria.EXITOSO));
            condiciones.add(root.get("entidad").in(ENTIDAD_HORARIO, ENTIDAD_ASIGNACION));
            if (filtro.desde() != null) {
                condiciones.add(builder.greaterThanOrEqualTo(
                        root.get("fechaHora"),
                        filtro.desde().atStartOfDay(ZONA_INSTITUCION).toInstant()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(builder.lessThan(
                        root.get("fechaHora"),
                        filtro.hasta().plusDays(1).atStartOfDay(ZONA_INSTITUCION).toInstant()));
            }
            if (!clavesContexto.isEmpty() || !clavesAula.isEmpty()) {
                Predicate bloqueAntiguo = esBloqueAntiguo(root, builder);
                condiciones.add(builder.or(
                        coincide(root.get("nuevoValor"), clavesContexto, clavesAula, clavesAsignacion,
                                bloqueAntiguo, builder),
                        coincide(root.get("valorAnterior"), clavesContexto, clavesAula, clavesAsignacion,
                                bloqueAntiguo, builder)));
            }
            return builder.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private List<String> clavesDeContexto(FiltroHistorialHorario filtro) {
        List<String> claves = new ArrayList<>();
        if (filtro.anioAcademico() != null) {
            claves.add(clave(CAMPO_ANIO, filtro.anioAcademico()));
        }
        if (filtro.seccionId() != null) {
            claves.add(clave(CAMPO_SECCION_ID, filtro.seccionId()));
        }
        if (filtro.docenteId() != null) {
            claves.add(clave(CAMPO_DOCENTE_ID, filtro.docenteId()));
        }
        return claves;
    }

    /** La coma final evita que "seccionId=1" coincida con "seccionId=12". */
    private static String clave(String campo, Object contenido) {
        return campo + "=" + contenido + ",";
    }

    /** Antes de US-27 el bloque solo guardaba los ids de asignacion y aula, sin seccion ni docente. */
    private Predicate esBloqueAntiguo(Root<RegistroAuditoria> root, CriteriaBuilder builder) {
        Expression<String> valores =
                builder.coalesce(root.<String>get("nuevoValor"), root.get("valorAnterior"));
        return builder.and(
                builder.equal(root.get("entidad"), ENTIDAD_HORARIO),
                builder.notLike(valores, "%" + CAMPO_SECCION_ID + "=%"));
    }

    /**
     * Compara una sola columna para no mezclar, por ejemplo, la seccion del valor anterior con el
     * docente del valor nuevo. Los bloques antiguos se ubican por su asignacion actual.
     */
    private Predicate coincide(
            Expression<String> columna,
            List<String> clavesContexto,
            List<String> clavesAula,
            List<String> clavesAsignacion,
            Predicate bloqueAntiguo,
            CriteriaBuilder builder) {
        Predicate valorCompleto = contieneTodas(
                columna, Stream.concat(clavesContexto.stream(), clavesAula.stream()).toList(), builder);
        if (clavesContexto.isEmpty()) {
            return valorCompleto;
        }
        Predicate valorAntiguo = builder.and(
                bloqueAntiguo,
                contieneTodas(columna, clavesAula, builder),
                builder.or(clavesAsignacion.stream()
                        .map(clave -> contiene(columna, clave, builder))
                        .toArray(Predicate[]::new)));
        return builder.or(valorCompleto, valorAntiguo);
    }

    private Predicate contieneTodas(
            Expression<String> columna, List<String> claves, CriteriaBuilder builder) {
        return builder.and(claves.stream()
                .map(clave -> contiene(columna, clave, builder))
                .toArray(Predicate[]::new));
    }

    private Predicate contiene(Expression<String> columna, String clave, CriteriaBuilder builder) {
        return builder.like(columna, "%" + clave + "%");
    }

    /** Agrega a los bloques antiguos los nombres actuales; las fotos nuevas ya los traen. */
    private void completarBloquesAntiguos(List<ValoresRegistro> filas) {
        List<Map<String, String>> antiguos = filas.stream()
                .flatMap(fila -> Stream.of(fila.anterior(), fila.nuevo()))
                .filter(valores -> valores.containsKey(CAMPO_ASIGNACION_ID)
                        && !valores.containsKey(CAMPO_CURSO))
                .toList();
        if (antiguos.isEmpty()) {
            return;
        }
        Map<Long, AsignacionCurso> asignaciones = asignacionRepository
                .findAllByIdIn(idsDe(antiguos, CAMPO_ASIGNACION_ID))
                .stream()
                .collect(Collectors.toMap(AsignacionCurso::getId, Function.identity()));
        Map<Long, Aula> aulas = aulaRepository
                .findAllById(idsDe(antiguos, CAMPO_AULA_ID))
                .stream()
                .collect(Collectors.toMap(Aula::getId, Function.identity()));
        for (Map<String, String> valores : antiguos) {
            Optional.ofNullable(asignaciones.get(Long.valueOf(valores.get(CAMPO_ASIGNACION_ID))))
                    .ifPresent(asignacion -> {
                        valores.put(CAMPO_CURSO, asignacion.getCurso().getNombre());
                        valores.put(
                                CAMPO_SECCION,
                                AsignacionCursoService.etiquetaSeccion(asignacion.getSeccion()));
                        valores.put(CAMPO_DOCENTE, asignacion.getDocente().getPersona().nombreCompleto());
                    });
            Optional.ofNullable(valores.get(CAMPO_AULA_ID))
                    .map(id -> aulas.get(Long.valueOf(id)))
                    .ifPresent(aula -> valores.put(CAMPO_AULA, aula.getNombre()));
        }
    }

    private static List<Long> idsDe(List<Map<String, String>> valores, String campo) {
        return valores.stream()
                .map(mapa -> mapa.get(campo))
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .distinct()
                .toList();
    }

    private HistorialHorarioResponse mapear(ValoresRegistro fila, Map<String, String> nombres) {
        RegistroAuditoria registro = fila.registro();
        List<String> campos = ENTIDAD_HORARIO.equals(registro.getEntidad())
                ? CAMPOS_HORARIO
                : CAMPOS_ASIGNACION;
        return new HistorialHorarioResponse(
                registro.getId(),
                registro.getFechaHora(),
                registro.getActorIdentificador(),
                nombres.get(registro.getActorIdentificador()),
                registro.getRolActor() == null ? null : registro.getRolActor().getNombre(),
                registro.getAccion(),
                registro.getEntidad(),
                registro.getRegistroId(),
                registro.getMotivo(),
                campos.stream()
                        .map(campo -> new CampoHistorial(
                                campo, fila.anterior().get(campo), fila.nuevo().get(campo)))
                        .filter(campo -> campo.anterior() != null || campo.nuevo() != null)
                        .toList());
    }

    /** Registro de auditoria con sus valores ya separados por campo. */
    private record ValoresRegistro(
            RegistroAuditoria registro, Map<String, String> anterior, Map<String, String> nuevo) {

        static ValoresRegistro leer(RegistroAuditoria registro) {
            return new ValoresRegistro(
                    registro, separar(registro.getValorAnterior()), separar(registro.getNuevoValor()));
        }

        private static Map<String, String> separar(String valores) {
            Map<String, String> campos = new LinkedHashMap<>();
            if (valores == null) {
                return campos;
            }
            for (String campo : SEPARADOR_CAMPOS.split(valores)) {
                int igual = campo.indexOf('=');
                if (igual > 0) {
                    campos.put(campo.substring(0, igual), campo.substring(igual + 1));
                }
            }
            return campos;
        }
    }
}
