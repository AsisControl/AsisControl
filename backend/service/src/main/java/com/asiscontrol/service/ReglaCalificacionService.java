package com.asiscontrol.service;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.ReglaCalificacionDto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.ReglaCalificacion;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.ReglaCalificacionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReglaCalificacionService {
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ReglaCalificacionRepository reglas;
    private final GradoAcademicoRepository grados;
    private final AsignacionCursoRepository asignaciones;
    private final ModuloAccessGuard acceso;
    private final AuditoriaService auditoria;
    private final EntityManager em;

    public ReglaCalificacionService(ReglaCalificacionRepository reglas, GradoAcademicoRepository grados,
            AsignacionCursoRepository asignaciones, ModuloAccessGuard acceso, AuditoriaService auditoria,
            EntityManager em) {
        this.reglas = reglas;
        this.grados = grados;
        this.asignaciones = asignaciones;
        this.acceso = acceso;
        this.auditoria = auditoria;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<ReglaCalificacionDto.Regla> historial(Long curso, Long grado) {
        return reglas.findByCursoIdAndGradoIdOrderByNumeroVersionDesc(curso, grado).stream()
                .map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReglaCalificacionDto.Regla> vigentePorAsignacion(Long id) {
        var asignacion = asignaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "Asignación no encontrada"));
        acceso.verificarAsignacionDocente(asignacion);
        return vigente(asignacion).map(this::respuesta);
    }

    Optional<ReglaCalificacion> vigente(AsignacionCurso asignacion) {
        var seccion = asignacion.getSeccion();
        return grados.findByAnioAcademicoAnioAndNumero(seccion.getAnioAcademico(), seccion.getGrado())
                .flatMap(grado -> vigente(asignacion.getCurso().getId(), grado.getId()));
    }

    Optional<ReglaCalificacion> vigenteParaLibreta(AsignacionCurso asignacion) {
        bloquearCurso(asignacion.getCurso().getId());
        return vigente(asignacion);
    }

    @Transactional
    public ReglaCalificacionDto.Regla guardarBorrador(ReglaCalificacionDto.Borrador request) {
        validarTipos(request.componentes());
        var curso = bloquearCurso(request.idCurso());
        var grado = gradoDelCurso(request.idGrado(), curso);
        var historial = reglas.findByCursoIdAndGradoIdOrderByNumeroVersionDesc(curso.getId(), grado.getId());
        var regla = historial.stream().filter(item -> item.getEstado() == ReglaCalificacion.Estado.BORRADOR)
                .findFirst().orElse(null);
        String anterior = null;
        if (regla == null) {
            if (request.version() != null) throw versionDesactualizada();
            regla = new ReglaCalificacion();
            regla.setCurso(curso);
            regla.setGrado(grado);
            regla.setNumeroVersion(historial.isEmpty() ? 1 : historial.getFirst().getNumeroVersion() + 1);
        } else {
            verificarVersion(regla, request.version());
            anterior = describir(regla);
        }
        regla.setFormula(request.formula());
        regla.getComponentes().clear();
        for (var componente : request.componentes()) {
            regla.getComponentes().put(componente.tipoEvaluacion(),
                    new ReglaCalificacion.Componente(componente.nombre().trim(), componente.peso()));
        }
        var guardada = reglas.saveAndFlush(regla);
        var id = guardada.getId();
        auditar("GUARDAR_BORRADOR_REGLA_CALIFICACION", id, anterior, describir(guardada));
        return respuesta(guardada);
    }

    @Transactional
    public ReglaCalificacionDto.Regla activar(Long id, long version) {
        var regla = reglas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REGLA_NO_ENCONTRADA", "Regla no encontrada"));
        bloquearCurso(regla.getCurso().getId());
        em.refresh(regla);
        gradoDelCurso(regla.getGrado().getId(), regla.getCurso());
        verificarVersion(regla, version);
        if (regla.getEstado() != ReglaCalificacion.Estado.BORRADOR) {
            throw new BusinessRuleException("REGLA_NO_EDITABLE", "Solo se puede activar un borrador");
        }
        validarSuma(componentes(regla));
        var anterior = vigente(regla.getCurso().getId(), regla.getGrado().getId()).orElse(null);
        if (anterior != null) anterior.setEstado(ReglaCalificacion.Estado.HISTORICA);
        regla.setEstado(ReglaCalificacion.Estado.VIGENTE);
        em.flush();
        auditar("ACTIVAR_REGLA_CALIFICACION", id, anterior == null ? null : "versión " + anterior.getNumeroVersion(),
                describir(regla));
        return respuesta(regla);
    }

    public ReglaCalificacionDto.Resultado vistaPrevia(ReglaCalificacionDto.VistaPrevia request) {
        validarTipos(request.componentes());
        validarSuma(request.componentes());
        return calcular(request.componentes(), request.muestra());
    }

    record NotaCompetencia(Competencia competencia, ReglaCalificacionDto.Muestra nota) {}

    List<CalificacionDto.ConsolidadoResponse> consolidar(ReglaCalificacion regla, Long asignacion,
                                                         List<NotaCompetencia> notas) {
        var porCompetencia = new LinkedHashMap<Long, List<NotaCompetencia>>();
        for (var nota : notas) {
            porCompetencia.computeIfAbsent(nota.competencia().getId(), id -> new ArrayList<>()).add(nota);
        }
        var componentes = componentes(regla);
        return porCompetencia.values().stream().map(items -> {
            var competencia = items.getFirst().competencia();
            var resultado = calcular(componentes, items.stream().map(NotaCompetencia::nota).toList());
            return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                    competencia.getNombre(), resultado.promedioPonderado(), resultado.nivelConsolidado(),
                    resultado.evaluacionesConsideradas(), asignacion, regla.getNumeroVersion(),
                    resultado.componentesFaltantes());
        }).sorted(Comparator.comparing(CalificacionDto.ConsolidadoResponse::nombreCompetencia, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ReglaCalificacionDto.Resultado calcular(List<ReglaCalificacionDto.Componente> componentes,
            List<ReglaCalificacionDto.Muestra> muestra) {
        Map<TipoEvaluacion, List<NivelLogro>> niveles = new EnumMap<>(TipoEvaluacion.class);
        for (var nota : muestra) {
            if (nota.nivel() != null) niveles.computeIfAbsent(nota.tipoEvaluacion(), tipo -> new ArrayList<>()).add(nota.nivel());
        }
        var faltantes = new ArrayList<String>();
        BigDecimal total = BigDecimal.ZERO;
        int cantidad = 0;
        for (var componente : componentes) {
            var valores = niveles.get(componente.tipoEvaluacion());
            if (valores == null) {
                faltantes.add(componente.nombre());
                continue;
            }
            cantidad += valores.size();
            int suma = valores.stream().mapToInt(NivelLogro::getValor).sum();
            total = total.add(BigDecimal.valueOf(suma).multiply(componente.peso())
                    .divide(BigDecimal.valueOf(valores.size()), 10, RoundingMode.HALF_UP));
        }
        if (!faltantes.isEmpty()) return new ReglaCalificacionDto.Resultado(null, null, cantidad, faltantes);
        BigDecimal promedio = total.divide(CIEN, 2, RoundingMode.HALF_UP);
        return new ReglaCalificacionDto.Resultado(promedio, NivelLogro.desdePromedio(promedio.doubleValue()), cantidad, List.of());
    }

    private Optional<ReglaCalificacion> vigente(Long curso, Long grado) {
        return reglas.findByCursoIdAndGradoIdAndEstado(curso, grado, ReglaCalificacion.Estado.VIGENTE);
    }

    private Curso bloquearCurso(Long id) {
        var curso = em.find(Curso.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (curso == null) throw new ResourceNotFoundException("CURSO_NO_ENCONTRADO", "Curso no encontrado");
        return curso;
    }

    private GradoAcademico gradoDelCurso(Long id, Curso curso) {
        var grado = grados.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GRADO_NO_ENCONTRADO", "Grado no encontrado"));
        if (!grado.isActivo() || !grado.getAnioAcademico().isActivo() || grado.getCursosPlan().keySet().stream().noneMatch(c -> c.getId().equals(curso.getId()))) {
            throw new BusinessRuleException("CURSO_FUERA_DEL_PLAN", "El curso debe pertenecer al plan del año y grado seleccionados");
        }
        return grado;
    }

    private void validarTipos(List<ReglaCalificacionDto.Componente> componentes) {
        if (componentes.stream().map(ReglaCalificacionDto.Componente::tipoEvaluacion).distinct().count() != componentes.size()) {
            throw new BusinessRuleException("COMPONENTE_DUPLICADO", "Cada tipo de evaluación puede aparecer una sola vez");
        }
    }

    private void validarSuma(List<ReglaCalificacionDto.Componente> componentes) {
        var suma = componentes.stream().map(ReglaCalificacionDto.Componente::peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(CIEN) != 0) {
            throw new BusinessRuleException("PESOS_INVALIDOS", "Los pesos deben sumar 100 %. Total actual: " + suma.stripTrailingZeros().toPlainString() + " %.");
        }
    }

    private void verificarVersion(ReglaCalificacion regla, Long version) {
        if (version == null || regla.getVersion() != version) throw versionDesactualizada();
    }

    private ConflictException versionDesactualizada() {
        return new ConflictException("VERSION_DESACTUALIZADA", "La regla cambió. Actualiza los datos y vuelve a intentarlo");
    }

    private ReglaCalificacionDto.Regla respuesta(ReglaCalificacion regla) {
        return new ReglaCalificacionDto.Regla(regla.getId(), regla.getCurso().getId(), regla.getGrado().getId(),
                regla.getNumeroVersion(), regla.getEstado(), regla.getFormula(), componentes(regla), regla.getVersion());
    }

    private List<ReglaCalificacionDto.Componente> componentes(ReglaCalificacion regla) {
        return regla.getComponentes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(item -> new ReglaCalificacionDto.Componente(item.getKey(), item.getValue().getNombre(), item.getValue().getPeso()))
                .toList();
    }

    private String describir(ReglaCalificacion regla) {
        return "versión=" + regla.getNumeroVersion() + ", fórmula=" + regla.getFormula() + ", componentes=" + componentes(regla);
    }

    private void auditar(String accion, Long id, String antes, String despues) {
        auditoria.registrar(accion, ModuloAuditoria.ACADEMICO, "ReglaCalificacion", id.toString(),
                ResultadoAuditoria.EXITOSO, null, antes, despues);
    }
}
