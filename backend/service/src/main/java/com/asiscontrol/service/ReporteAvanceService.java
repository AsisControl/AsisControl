package com.asiscontrol.service;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.InformeDisponibleDto;
import com.asiscontrol.dto.ReglaCalificacionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.asiscontrol.dto.ReporteAvanceDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.*;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReporteAvanceService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private record GrupoPeso(Long competencia, TipoEvaluacion tipo) {}
    private final AlumnoRepository alumnos;
    private final AsignacionCursoRepository asignaciones;
    private final PeriodoAcademicoRepository periodos;
    private final MatriculaRepository matriculas;
    private final ModuloAccessGuard acceso;
    private final PlanEstudiosService planes;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<CalificacionDto.ConsolidadoResponse>> CONSOLIDADO = new TypeReference<>() {};
    private static final TypeReference<Map<Long, String>> CONCLUSIONES = new TypeReference<>() {};
    private final EntityManager em;
    private final ReglaCalificacionService reglas;
    private final EscalaInstitucionalService escalas;
    private final EvidenciaPedagogicaRepository evidencias;
    private final AuditoriaService auditoria;

    public ReporteAvanceService(AlumnoRepository alumnos, AsignacionCursoRepository asignaciones,
            PeriodoAcademicoRepository periodos, MatriculaRepository matriculas,
            ModuloAccessGuard acceso, PlanEstudiosService planes, EntityManager em, ReglaCalificacionService reglas,
            EscalaInstitucionalService escalas, EvidenciaPedagogicaRepository evidencias, AuditoriaService auditoria) {
        this.alumnos = alumnos;
        this.asignaciones = asignaciones;
        this.periodos = periodos;
        this.matriculas = matriculas;
        this.acceso = acceso;
        this.planes = planes;
        this.em = em;
        this.reglas = reglas;
        this.escalas = escalas;
        this.evidencias = evidencias;
        this.auditoria = auditoria;
    }

    public record ResultadoPeriodo(int informesProcesados, List<InformeDisponibleDto> informes) { }
    public record EstadoAlumno(Long idAlumno, String alumno, boolean borrador, boolean publicada, boolean revisado) { }
    public record EstadoPeriodo(List<EstadoAlumno> alumnos, boolean publicable, boolean desactualizada,
                                String impedimento) { }

    @Transactional(readOnly = true)
    public EstadoPeriodo estadoPeriodo(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        var periodo = periodo(periodoId, asignacion);
        var regla = reglas.vigente(asignacion).orElse(null);
        var alumnosPeriodo = new ArrayList<EstadoAlumno>();
        boolean desactualizada = false;
        boolean tieneBorradores = false;
        String impedimento = null;
        for (var alumno : estudiantes(asignacion)) {
            var actual = ultimo(alumno.getId(), curso, periodoId, false).orElse(null);
            var publicada = ultimo(alumno.getId(), curso, periodoId, true).orElse(null);
            boolean borrador = actual != null && actual.getEstado() == EstadoInformeProgreso.BORRADOR;
            alumnosPeriodo.add(new EstadoAlumno(alumno.getId(), nombre(alumno.getPersona()), borrador, publicada != null, borrador && actual.isRevisado()));
            if (publicada != null && !desactualizada) {
                try {
                    validarRegla(publicada, regla);
                    validarVigencia(publicada, asignacion, periodo, regla);
                } catch (BusinessRuleException e) {
                    desactualizada = true;
                }
            }
            if (borrador) {
                tieneBorradores = true;
                try {
                    validarBorrador(actual, asignacion, periodo, regla);
                } catch (BusinessRuleException e) {
                    if (impedimento == null) impedimento = e.getMessage();
                }
            }
        }
        if (tieneBorradores && impedimento == null) {
            if (alumnosPeriodo.stream().anyMatch(a -> !a.borrador() && !a.publicada())) {
                impedimento = "Genera borradores para todos los estudiantes antes de publicar.";
            }
        }
        return new EstadoPeriodo(alumnosPeriodo, tieneBorradores && impedimento == null, desactualizada, impedimento);
    }

    /** Genera o reemplaza solamente borradores. Una version publicada nunca se modifica. */
    @Transactional
    public ResultadoPeriodo generarPeriodo(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        // Serializa la asignacion de numeros de version, incluso cuando aun no existe un informe.
        em.lock(asignacion, LockModeType.PESSIMISTIC_WRITE);
        var periodo = periodo(periodoId, asignacion);
        var estudiantes = estudiantes(asignacion);
        var regla = reglas.vigenteParaLibreta(asignacion).orElse(null);
        for (var alumno : estudiantes) generar(alumno, asignacion, periodo, regla);
        return new ResultadoPeriodo(estudiantes.size(), estudiantes.stream().map(a ->
                new InformeDisponibleDto(a.getId(), nombre(a.getPersona()), curso,
                        asignacion.nombreCurso(), asignacion.getSeccion().getAnioAcademico(),
                        periodoId, periodo.getNombre())).toList());
    }

    /** Publica todas las libretas en una unica transaccion y conserva las versiones anteriores. */
    @Transactional
    public ResultadoPeriodo publicarPeriodo(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        em.lock(asignacion, LockModeType.PESSIMISTIC_WRITE);
        var periodo = periodo(periodoId, asignacion);
        var estudiantes = estudiantes(asignacion);
        var regla = reglas.vigenteParaLibreta(asignacion).orElse(null);
        var pendientes = new ArrayList<InformeProgreso>();
        for (var alumno : estudiantes) {
            var informe = ultimo(alumno.getId(), curso, periodoId, false)
                    .orElseThrow(() -> new BusinessRuleException("INFORME_BORRADOR_REQUERIDO", "Genera los borradores antes de publicar."));
            if (informe.getEstado() == EstadoInformeProgreso.PUBLICADA) continue;
            validarBorrador(informe, asignacion, periodo, regla);
            pendientes.add(informe);
        }
        var fecha = Instant.now();
        for (var informe : pendientes) {
            informe.setRevisado(true);
            informe.setEstado(EstadoInformeProgreso.PUBLICADA);
            auditoria.registrar("PUBLICAR_INFORME_PROGRESO", ModuloAuditoria.ACADEMICO, "InformeProgreso",
                    informe.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                    "versión=" + informe.getNumeroVersion());
            informe.setFechaPublicacion(fecha);
        }
        return new ResultadoPeriodo(pendientes.size(), List.of());
    }

    public record RevisionRequest(@jakarta.validation.constraints.NotNull Long version) {}

    @Transactional
    public ReporteAvanceDto revisarBorrador(Long alumno, Long curso, Long periodoId, RevisionRequest request) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        em.lock(asignacion, LockModeType.PESSIMISTIC_WRITE);
        var periodo = periodo(periodoId, asignacion);
        var informe = ultimo(alumno, curso, periodoId, false)
                .filter(item -> item.getEstado() == EstadoInformeProgreso.BORRADOR)
                .orElseThrow(() -> new ResourceNotFoundException("INFORME_NO_ENCONTRADO", "No hay un borrador disponible."));
        if (!Objects.equals(request.version(), informe.getVersion())) {
            throw new com.asiscontrol.exception.ConflictException("INFORME_DESACTUALIZADO", "Vuelve a abrir el borrador antes de revisarlo.");
        }
        validarBorrador(informe, asignacion, periodo, reglas.vigenteParaLibreta(asignacion).orElse(null));
        if (!informe.isRevisado()) {
            informe.setRevisado(true);
            auditoria.registrar("REVISAR_INFORME_PROGRESO", ModuloAuditoria.ACADEMICO, "InformeProgreso",
                    informe.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                    "versión=" + informe.getNumeroVersion());
        }
        em.flush();
        return prepararBorrador(alumno, curso, periodoId);
    }

    private Map<Long, String> conclusiones(InformeProgreso informe) {
        if (informe.getConclusionesDescriptivas() == null) return Map.of();
        try {
            return JSON.readValue(informe.getConclusionesDescriptivas(), CONCLUSIONES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron leer las conclusiones de la libreta " + informe.getId(), e);
        }
    }

    private void validarBorrador(InformeProgreso informe, AsignacionCurso asignacion, PeriodoAcademico periodo,
                                 ReglaCalificacion regla) {
        validarRegla(informe, regla);
        validarVigencia(informe, asignacion, periodo, regla);
    }

    private void validarRegla(InformeProgreso informe, ReglaCalificacion vigente) {
        Long guardada = informe.getReglaCalificacion() == null ? null : informe.getReglaCalificacion().getId();
        if (!Objects.equals(guardada, vigente == null ? null : vigente.getId())) {
            throw new BusinessRuleException("INFORME_REGLA_DESACTUALIZADA",
                    "La regla de calificación cambió. Genera un nuevo borrador para usar la versión vigente");
        }
    }

    private List<CalificacionDto.ConsolidadoResponse> consolidadoGuardado(InformeProgreso informe) {
        if (informe.getConsolidadoRegla() == null) return List.of();
        try {
            return JSON.readValue(informe.getConsolidadoRegla(), CONSOLIDADO);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el consolidado de la libreta " + informe.getId(), e);
        }
    }

    private String serializarConsolidado(List<CalificacionDto.ConsolidadoResponse> consolidado) {
        try {
            return JSON.writeValueAsString(consolidado);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el consolidado de la libreta", e);
        }
    }

    private List<Alumno> estudiantes(AsignacionCurso asignacion) {
        var estudiantes = em.createQuery("""
                select distinct m.alumno from Matricula m where m.activo = true
                and m.alumno.activo = true and m.seccion.id = :seccion
                and m.estado in (com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                                 com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA)
                """, Alumno.class).setParameter("seccion", asignacion.getSeccion().getId()).getResultList();
        if (estudiantes.isEmpty()) throw new BusinessRuleException("INFORME_SIN_ALUMNOS", "No hay alumnos matriculados en esta seccion");
        return estudiantes;
    }

    private InformeProgreso generar(Alumno alumno, AsignacionCurso asignacion, PeriodoAcademico periodo,
                                    ReglaCalificacion regla) {
        var captura = capturar(alumno, asignacion, periodo, regla);
        var anterior = ultimo(alumno.getId(), asignacion.getId(), periodo.getId(), false).orElse(null);
        InformeProgreso informe;
        if (anterior != null && anterior.getEstado() == EstadoInformeProgreso.BORRADOR
                && anterior.getConclusionesDescriptivas() == null) {
            informe = anterior;
            detalles(informe).forEach(em::remove);
            em.flush(); // Libera las claves unicas antes de insertar los nuevos detalles.
        } else {
            informe = new InformeProgreso();
            informe.setAlumno(alumno);
            informe.setAsignacionCurso(asignacion);
            informe.setPeriodoAcademico(periodo);
            informe.setNumeroVersion(anterior == null ? 1 : anterior.getNumeroVersion() + 1);
            informe.setDocente(asignacion.getDocente());
            em.persist(informe);
        }
        informe.setDocente(asignacion.getDocente());
        informe.setReglaCalificacion(regla);
        informe.setEscalaInstitucional(captura.escala());
        informe.setRevisado(false);
        for (var detalle : captura.filas()) {
            detalle.setInforme(informe);
            em.persist(detalle);
        }
        informe.setConsolidadoRegla(serializarConsolidado(captura.consolidado()));
        return informe;
    }

    private record Captura(List<DetalleInformeProgreso> filas,
                           List<CalificacionDto.ConsolidadoResponse> consolidado, EscalaInstitucional escala) {}

    private Captura capturar(Alumno alumno, AsignacionCurso asignacion, PeriodoAcademico periodo,
                             ReglaCalificacion regla) {
        return capturar(alumno, asignacion, periodo, regla, false);
    }

    private Captura capturar(Alumno alumno, AsignacionCurso asignacion, PeriodoAcademico periodo,
                             ReglaCalificacion regla, boolean incluirSinPeso) {
        return capturar(alumno, asignacion, periodo, regla, incluirSinPeso, null);
    }

    private Captura capturar(Alumno alumno, AsignacionCurso asignacion, PeriodoAcademico periodo,
                             ReglaCalificacion regla, boolean incluirSinPeso, EscalaInstitucional escalaFijada) {
        var pruebas = evaluacionesDelPeriodo(asignacion, periodo, incluirSinPeso);
        boolean dinamica = regla != null && regla.getFormula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1;
        boolean contextoNuevo = dinamica || pruebas.stream().anyMatch(Evaluacion::isModeloCualitativo);
        var tareasComprometidas = contextoNuevo ? em.createQuery("""
                select t.evaluacionAcademica.id, t.nombre from Tarea t where t.activo = true
                and t.asignacionCurso.id = :curso and t.evaluacionAcademica.cuentaResultado = true
                and (t.fechaPublicacion is null or t.fechaPublicacion <= :ahora)
                and t.estadoPublicacion in (com.asiscontrol.entity.enums.EstadoPublicacionTarea.PUBLICADA,
                    com.asiscontrol.entity.enums.EstadoPublicacionTarea.CERRADA,
                    com.asiscontrol.entity.enums.EstadoPublicacionTarea.ARCHIVADA)
                """, Object[].class).setParameter("curso", asignacion.getId())
                .setParameter("ahora", java.time.LocalDateTime.now()).getResultList().stream()
                .collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (String) fila[1], (nombre, otro) -> nombre))
                : Map.<Long, String>of();
        var publicadas = pruebas.stream().filter(prueba -> prueba.getEstado() == EstadoEvaluacion.PUBLICADA
                || prueba.getEstado() == EstadoEvaluacion.CERRADA || tareasComprometidas.containsKey(prueba.getId())).toList();
        var notas = em.createQuery("""
                select c from Calificacion c where c.activo = true and c.alumno.id = :alumno
                and c.evaluacion.asignacionCurso.id = :curso and c.evaluacion.activo = true
                and (:incluirSinPeso = true or c.evaluacion.cuentaResultado = true)
                and c.evaluacion.fechaEvaluacion between :inicio and :fin
                and c.evaluacion.estado in (com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                        com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA)
                """, Calificacion.class).setParameter("alumno", alumno.getId()).setParameter("curso", asignacion.getId())
                .setParameter("incluirSinPeso", incluirSinPeso)
                .setParameter("inicio", periodo.getFechaInicio()).setParameter("fin", periodo.getFechaFin())
                .getResultList().stream().collect(Collectors.toMap(c -> c.getEvaluacion().getId() + ":" +
                        c.getCompetencia().getId(), c -> c));
        var escala = escalaFijada != null ? escalaFijada
                : notas.values().stream().anyMatch(nota -> nota.getEvaluacion().isCuentaResultado() && nota.getNotaNumerica() != null)
                ? escalas.vigente().orElse(null) : null;
        var filas = new ArrayList<DetalleInformeProgreso>();
        var cantidades = new HashMap<GrupoPeso, Long>();
        for (var prueba : publicadas) for (var competencia : prueba.getCompetencias()) {
            var nota = notas.get(prueba.getId() + ":" + competencia.getId());
            var fila = new DetalleInformeProgreso();
            fila.setEvaluacion(prueba);
            fila.setCompetencia(competencia);
            fila.setCalificacion(nota);
            fila.setNombreCompetencia(competencia.getNombre());
            fila.setTituloEvaluacion(prueba.getEstado() == EstadoEvaluacion.BORRADOR
                    ? tareasComprometidas.get(prueba.getId()) : prueba.getTitulo());
            fila.setFechaEvaluacion(prueba.getFechaEvaluacion());
            fila.setPonderacionPorcentaje(prueba.getPonderacionPorcentaje());
            fila.setEstadoEvaluacion(prueba.getEstado().name());
            if (nota != null) {
                fila.setNotaNumerica(nota.getNotaNumerica());
                fila.setNivelLogro(nota.getNivelLogro());
                fila.setObservacion(nota.getObservacion());
            }
            if (prueba.isCuentaResultado() && (dinamica || nota != null && nota.getNivelLogro() != null))
                cantidades.merge(new GrupoPeso(competencia.getId(), prueba.getTipoEvaluacion()), 1L, Long::sum);
            filas.add(fila);
        }
        for (var fila : filas) {
            if (!fila.getEvaluacion().isCuentaResultado()) fila.setPesoResultado("Sin peso");
            else if (regla != null) fila.setPesoResultado(pesoResultado(regla, fila, cantidades));
        }
        var muestras = filas.stream().filter(fila -> fila.getEvaluacion().isCuentaResultado()).map(fila -> new ReglaCalificacionService.NotaCompetencia(fila.getCompetencia(),
                new ReglaCalificacionDto.Muestra(fila.getEvaluacion().getTipoEvaluacion(), fila.getNivelLogro()),
                fila.getNotaNumerica(), fila.getEvaluacion().isModeloCualitativo())).toList();
        var calculados = regla == null ? List.<CalificacionDto.ConsolidadoResponse>of()
                : reglas.consolidar(regla, asignacion.getId(), muestras, escala);
        var porCompetencia = calculados.stream().collect(Collectors.toMap(CalificacionDto.ConsolidadoResponse::idCompetencia, item -> item));
        var actividades = (contextoNuevo ? publicadas : pruebas).stream().filter(Evaluacion::isCuentaResultado)
                .filter(prueba -> !dinamica || regla.getComponentes().containsKey(prueba.getTipoEvaluacion()))
                .flatMap(prueba -> prueba.getCompetencias().stream())
                .collect(Collectors.toMap(Competencia::getId, item -> item, (a, b) -> a));
        var catalogo = new LinkedHashMap<Long, Competencia>();
        planes.competenciasDeAsignacion(asignacion).forEach(competencia -> catalogo.put(competencia.getId(), competencia));
        actividades.forEach(catalogo::putIfAbsent);
        var consolidado = catalogo.values().stream().map(competencia -> {
            var resultado = porCompetencia.get(competencia.getId());
            boolean tieneActividad = actividades.containsKey(competencia.getId());
            boolean tieneNotas = muestras.stream().anyMatch(item -> item.competencia().getId().equals(competencia.getId())
                    && item.nota().nivel() != null);
            String estado = resultado != null && resultado.nivelConsolidado() != null ? "CONSOLIDADA"
                    : tieneActividad ? "PENDIENTE" : "NO_EVALUADA";
            var faltantes = new ArrayList<String>();
            if (tieneActividad && !tieneNotas) faltantes.add("Sin calificaciones publicadas");
            if (dinamica) filas.stream().filter(fila -> fila.getCompetencia().getId().equals(competencia.getId())
                    && fila.getEvaluacion().isCuentaResultado() && fila.getNivelLogro() == null
                    && regla.getComponentes().containsKey(fila.getEvaluacion().getTipoEvaluacion()))
                    .map(DetalleInformeProgreso::getTituloEvaluacion).distinct().forEach(faltantes::add);
            if (resultado != null && !resultado.componentesFaltantes().isEmpty())
                faltantes.add("Faltan resultados de " + String.join(", ", resultado.componentesFaltantes()));
            if (regla == null && (tieneNotas || contextoNuevo && tieneActividad)) faltantes.add("Regla de ponderación vigente");
            return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(), competencia.getNombre(),
                    resultado == null ? null : resultado.promedioPonderado(), resultado == null ? null : resultado.nivelConsolidado(),
                    resultado == null ? 0 : resultado.evaluacionesConsideradas(), asignacion.getId(),
                    regla == null ? null : regla.getNumeroVersion(), faltantes,
                    resultado == null ? null : resultado.numeroVersionEscala(), estado);
        }).sorted(Comparator.comparing(CalificacionDto.ConsolidadoResponse::nombreCompetencia, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CalificacionDto.ConsolidadoResponse::idCompetencia)).toList();
        return new Captura(filas, consolidado, escala);
    }

    record ConsolidadoRiesgo(CalificacionDto.ConsolidadoResponse resultado, boolean tieneEvaluacionPublicada) {}

    List<ConsolidadoRiesgo> consolidadoRiesgo(Alumno alumno, AsignacionCurso asignacion, PeriodoAcademico periodo,
                                           boolean usarVersionPublicada) {
        var publicada = usarVersionPublicada ? ultimo(alumno.getId(), asignacion.getId(), periodo.getId(), true)
                .filter(informe -> informe.getReglaCalificacion() != null && (informe.getEscalaInstitucional() != null
                        || informe.getReglaCalificacion().getFormula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1))
                .orElse(null) : null;
        var captura = publicada == null ? capturar(alumno, asignacion, periodo, reglas.vigente(asignacion).orElse(null))
                : capturar(alumno, asignacion, periodo, publicada.getReglaCalificacion(), false, publicada.getEscalaInstitucional());
        var competenciasPublicadas = captura.filas().stream().map(fila -> fila.getCompetencia().getId())
                .collect(Collectors.toSet());
        return captura.consolidado().stream()
                .map(item -> new ConsolidadoRiesgo(item, competenciasPublicadas.contains(item.idCompetencia()))).toList();
    }

    private String pesoResultado(ReglaCalificacion regla, DetalleInformeProgreso fila, Map<GrupoPeso, Long> cantidades) {
        var tipo = fila.getEvaluacion().getTipoEvaluacion();
        var componente = regla.getComponentes().get(tipo);
        if (componente == null) return "Sin peso";
        if (regla.getFormula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1) {
            long cantidad = cantidades.getOrDefault(new GrupoPeso(fila.getCompetencia().getId(), tipo), 0L);
            if (cantidad == 0) return "Pendiente";
            var total = regla.getComponentes().entrySet().stream()
                    .filter(item -> cantidades.containsKey(new GrupoPeso(fila.getCompetencia().getId(), item.getKey())))
                    .map(item -> item.getValue().getPeso()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            return ReglaCalificacionService.pesoNormalizado(componente.getPeso(), total, cantidad)
                    .setScale(4, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " %";
        }
        if (fila.getNivelLogro() == null) return "Pendiente";
        var peso = ReglaCalificacionService.pesoEfectivo(componente.getPeso(),
                cantidades.getOrDefault(new GrupoPeso(fila.getCompetencia().getId(), tipo), 0L));
        return peso.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " %";
    }

    private List<Evaluacion> evaluacionesDelPeriodo(AsignacionCurso asignacion, PeriodoAcademico periodo, boolean incluirSinPeso) {
        return em.createQuery("""
                select distinct e from Evaluacion e left join fetch e.competencias
                where e.activo = true and (:incluirSinPeso = true or e.cuentaResultado = true) and e.asignacionCurso.id = :curso
                and e.fechaEvaluacion between :inicio and :fin
                and e.estado <> com.asiscontrol.entity.enums.EstadoEvaluacion.ANULADA
                order by e.fechaEvaluacion, e.id
                """, Evaluacion.class).setParameter("curso", asignacion.getId()).setParameter("incluirSinPeso", incluirSinPeso)
                .setParameter("inicio", periodo.getFechaInicio()).setParameter("fin", periodo.getFechaFin()).getResultList();
    }

    private void validarVigencia(InformeProgreso informe, AsignacionCurso asignacion,
                                 PeriodoAcademico periodo, ReglaCalificacion regla) {
        var actual = capturar(informe.getAlumno(), asignacion, periodo, regla);
        var actuales = actual.filas().stream().map(this::contenido).collect(Collectors.toSet());
        var anteriores = detalles(informe).stream().map(this::contenido).collect(Collectors.toSet());
        if (!actuales.equals(anteriores) || !actual.consolidado().equals(consolidadoGuardado(informe))
                || !Objects.equals(idEscala(actual.escala()), idEscala(informe.getEscalaInstitucional()))) {
            throw new BusinessRuleException("INFORME_DESACTUALIZADO",
                    "Las evaluaciones, notas o competencias cambiaron. Genera y revisa un nuevo borrador.");
        }
    }

    private Long idEscala(EscalaInstitucional escala) {
        return escala == null ? null : escala.getId();
    }

    private record ContenidoFila(Long evaluacion, Long competencia, Long calificacion, String nombre, String titulo,
            java.time.LocalDate fecha, String peso, NivelLogro nivel, java.math.BigDecimal nota, String observacion,
            String estado, java.math.BigDecimal ponderacion) {}

    private ContenidoFila contenido(DetalleInformeProgreso fila) {
        return new ContenidoFila(fila.getEvaluacion().getId(), fila.getCompetencia().getId(),
                fila.getCalificacion() == null ? null : fila.getCalificacion().getId(),
                fila.getNombreCompetencia(), fila.getTituloEvaluacion(), fila.getFechaEvaluacion(),
                fila.getPesoResultado(), fila.getNivelLogro(), fila.getNotaNumerica() == null ? null : fila.getNotaNumerica().stripTrailingZeros(),
                fila.getObservacion(), fila.getEstadoEvaluacion(), fila.getPonderacionPorcentaje().stripTrailingZeros());
    }

    @Transactional(readOnly = true)
    public List<InformeDisponibleDto> disponibles() {
        return em.createQuery("""
                select i from InformeProgreso i
                where i.activo = true and i.estado = com.asiscontrol.entity.enums.EstadoInformeProgreso.PUBLICADA
                and i.alumno.activo = true and i.asignacionCurso.activo = true
                and i.periodoAcademico.activo = true and i.periodoAcademico.anioAcademico.activo = true
                and (:docente is null or i.asignacionCurso.docente.persona.id = :docente)
                and (:alumno is null or i.alumno.persona.id = :alumno)
                and (:apoderado is null or exists (select v.id from AlumnoApoderado v
                    where v.activo = true and v.apoderado.activo = true and v.alumno = i.alumno
                    and v.apoderado.persona.id = :apoderado))
                and exists (select m.id from Matricula m where m.activo = true and m.alumno = i.alumno
                    and (m.seccion = i.asignacionCurso.seccion or exists (select h.id
                        from HistorialSeccionMatricula h where h.activo = true and h.matricula = m
                        and h.seccionAnterior = i.asignacionCurso.seccion))
                    and m.estado in (com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                                     com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA))
                order by i.alumno.id, i.asignacionCurso.id, i.periodoAcademico.id, i.numeroVersion desc
                """, InformeProgreso.class)
                .setParameter("docente", acceso.restriccionDocentePersonaId())
                .setParameter("alumno", acceso.restriccionAlumnoPersonaId())
                .setParameter("apoderado", acceso.restriccionApoderadoPersonaId()).getResultList().stream()
                .map(i -> new InformeDisponibleDto(i.getAlumno().getId(), nombre(i.getAlumno().getPersona()),
                        i.getAsignacionCurso().getId(), i.getAsignacionCurso().nombreCurso(),
                        i.getAsignacionCurso().getSeccion().getAnioAcademico(), i.getPeriodoAcademico().getId(),
                        i.getPeriodoAcademico().getNombre())).distinct().toList();
    }

    @Transactional(readOnly = true)
    public List<EvaluacionDtoParcial> evaluacionesParciales(Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        return evaluacionesDelPeriodo(asignacion, periodo(periodoId, asignacion), true).stream()
                .filter(prueba -> (prueba.getEstado() == EstadoEvaluacion.PUBLICADA
                        || prueba.getEstado() == EstadoEvaluacion.CERRADA) && !prueba.getCompetencias().isEmpty())
                .map(prueba -> new EvaluacionDtoParcial(prueba.getId(), prueba.getTitulo(),
                        prueba.getTipoEvaluacion().name(), prueba.getFechaEvaluacion(), prueba.getCompetencias().stream()
                                .map(c -> new CompetenciaDtoParcial(c.getId(), c.getNombre())).toList())).toList();
    }

    public record CompetenciaDtoParcial(Long id, String nombre) {}
    public record EvaluacionDtoParcial(Long id, String nombre, String tipo, java.time.LocalDate fecha,
                                       List<CompetenciaDtoParcial> competencias) {}

    @Transactional(readOnly = true)
    public void validarFilaParcial(Long alumno, Long curso, Long periodoId, Long evaluacion, Long competencia) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        var periodo = periodo(periodoId, asignacion);
        acceso.verificarConsultaInforme(asignacion, alumno);
        if (estudiantes(asignacion).stream().noneMatch(item -> item.getId().equals(alumno))
                || evaluacionesDelPeriodo(asignacion, periodo, true).stream().noneMatch(item ->
                    item.getId().equals(evaluacion) && (item.getEstado() == EstadoEvaluacion.PUBLICADA
                    || item.getEstado() == EstadoEvaluacion.CERRADA) && item.getCompetencias().stream()
                    .anyMatch(c -> c.getId().equals(competencia)))) {
            throw new ResourceNotFoundException("FILA_INFORME_NO_ENCONTRADA", "La evaluación y la competencia no pertenecen al informe parcial.");
        }
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto prepararParcial(Long alumnoId, Long curso, Long periodoId, List<Long> evaluaciones, List<Long> competencias) {
        var asignacion = asignacion(curso);
        acceso.verificarAsignacionDocente(asignacion);
        var periodo = periodo(periodoId, asignacion);
        var alumno = estudiantes(asignacion).stream().filter(item -> item.getId().equals(alumnoId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("ALUMNO_CURSO_INVALIDO", "El estudiante no pertenece a esta sección."));
        var regla = reglas.vigente(asignacion).orElse(null);
        var captura = capturar(alumno, asignacion, periodo, regla, true);
        var disponibles = captura.filas().stream()
                .filter(fila -> !"BORRADOR".equals(fila.getEstadoEvaluacion()))
                .map(fila -> fila.getEvaluacion().getId()).collect(Collectors.toSet());
        if (evaluaciones.isEmpty() || !disponibles.containsAll(evaluaciones)) {
            throw new BusinessRuleException("SELECCION_PDF_INVALIDA", "Selecciona evaluaciones publicadas de este curso y período.");
        }
        var seleccionadas = new HashSet<>(evaluaciones);
        var elegidas = competencias == null ? null : new HashSet<>(competencias);
        var conSustento = evidencias.filasParciales(curso, alumnoId, periodoId, seleccionadas).stream()
                .map(fila -> fila.getEvaluacion() + ":" + fila.getCompetencia()).collect(Collectors.toSet());
        var filas = captura.filas().stream().filter(fila -> seleccionadas.contains(fila.getEvaluacion().getId()))
                .filter(fila -> elegidas == null || elegidas.contains(fila.getCompetencia().getId()))
                .map(fila -> filaDto(fila, 0L, conSustento.contains(fila.getEvaluacion().getId() + ":" + fila.getCompetencia().getId()), regla)).toList();
        if (filas.isEmpty() || !filas.stream().map(ReporteAvanceDto.Fila::idEvaluacion).collect(Collectors.toSet()).containsAll(seleccionadas)
                || (elegidas != null && !filas.stream().map(ReporteAvanceDto.Fila::idCompetencia).collect(Collectors.toSet()).containsAll(elegidas))) {
            throw new BusinessRuleException("SELECCION_PDF_INVALIDA", "Selecciona competencias con evaluaciones publicadas de este curso y período.");
        }
        var seccion = asignacion.getSeccion();
        return new ReporteAvanceDto(nombre(alumno.getPersona()), alumno.getCodigoAlumno(), asignacion.nombreCurso(),
                nombre(asignacion.getDocente().getPersona()), seccion.getGrado(), seccion.getNombre(),
                seccion.getAnioAcademico(), periodo.getNombre(), FECHA.format(periodo.getFechaInicio()) + " - " +
                FECHA.format(periodo.getFechaFin()), Instant.now(), filas,
                regla == null ? null : new ReglaCalificacionDto.Aplicada(regla.getId(), regla.getNumeroVersion(), regla.getFormula()),
                List.of(), null, Map.of(), List.of());
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto preparar(Long alumnoId, Long curso, Long periodoId) {
        return preparar(alumnoId, curso, periodoId, false);
    }

    @Transactional(readOnly = true)
    public List<CalificacionDto.ConsolidadoResponse> consultarConsolidado(Long alumnoId, Long curso, Long periodoId) {
        var asignacion = asignacion(curso);
        acceso.verificarConsultaInforme(asignacion, alumnoId);
        acceso.verificarAsignacionDocente(asignacion);
        var periodo = periodo(periodoId, asignacion);
        if (periodo.getEstado() == com.asiscontrol.entity.enums.EstadoPeriodoAcademico.CERRADO) {
            var publicada = ultimo(alumnoId, curso, periodoId, true);
            if (publicada.isPresent()) return consolidadoGuardado(publicada.orElseThrow());
        }
        var alumno = estudiantes(asignacion).stream().filter(item -> item.getId().equals(alumnoId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("ALUMNO_CURSO_INVALIDO", "El estudiante no pertenece a esta sección."));
        return capturar(alumno, asignacion, periodo, reglas.vigente(asignacion).orElse(null)).consolidado();
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto prepararBorrador(Long alumnoId, Long curso, Long periodoId) {
        return preparar(alumnoId, curso, periodoId, true);
    }

    private ReporteAvanceDto preparar(Long alumnoId, Long curso, Long periodoId, boolean borrador) {
        var asignacion = asignacion(curso);
        acceso.verificarConsultaInforme(asignacion, alumnoId);
        if (borrador) acceso.verificarAsignacionDocente(asignacion);
        var alumno = alumnos.findByIdAndActivoTrue(alumnoId)
                .orElseThrow(() -> new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "Alumno no encontrado"));
        var periodo = periodo(periodoId, asignacion);
        var seccion = asignacion.getSeccion();
        if (!matriculas.estuvoEnSeccion(alumnoId, seccion.getId())) {
            throw new BusinessRuleException("ALUMNO_CURSO_INVALIDO", "El alumno no tiene matricula vigente o finalizada en esta seccion");
        }
        var informe = ultimo(alumnoId, curso, periodoId, !borrador)
                .orElseThrow(() -> new ResourceNotFoundException("INFORME_NO_ENCONTRADO", "No hay un informe disponible para este periodo"));
        Set<Long> conSustento = asignacion.getDocente().isActivo()
                ? evidencias.filasConSustento(informe.getId()) : Set.of();
        var filas = detalles(informe).stream()
                .map(fila -> filaDto(fila, informe.getId(), conSustento.contains(fila.getId()), informe.getReglaCalificacion())).toList();
        return new ReporteAvanceDto(nombre(alumno.getPersona()), alumno.getCodigoAlumno(),
                asignacion.nombreCurso(), nombre(informe.getDocente().getPersona()),
                seccion.getGrado(), seccion.getNombre(), seccion.getAnioAcademico(),
                periodo.getNombre() + " | Version " + informe.getNumeroVersion() + " | " + informe.getEstado(),
                FECHA.format(periodo.getFechaInicio()) + " - " + FECHA.format(periodo.getFechaFin()), Instant.now(), filas,
                reglaAplicada(informe), consolidadoGuardado(informe), borrador ? informe.getVersion() : null,
                borrador ? Map.of() : conclusiones(informe), List.of());
    }

    private ReporteAvanceDto.Fila filaDto(DetalleInformeProgreso fila, Long informeId, boolean tieneSustento,
                                         ReglaCalificacion regla) {
        var componente = regla != null && fila.getEvaluacion().isCuentaResultado()
                ? regla.getComponentes().get(fila.getEvaluacion().getTipoEvaluacion()) : null;
        String peso = fila.getPesoResultado();
        if (peso == null) {
            boolean historica = informeId > 0 && fila.getEstadoEvaluacion() == null;
            peso = historica ? fila.getPonderacionPorcentaje().stripTrailingZeros().toPlainString() + " %" : "Pendiente";
        }
        return new ReporteAvanceDto.Fila(informeId, fila.getId() == null ? 0L : fila.getId(),
                fila.getEvaluacion().getId(), fila.getCompetencia().getId(),
                fila.getCalificacion() == null ? null : fila.getCalificacion().getId(), fila.getNombreCompetencia(),
                fila.getTituloEvaluacion(), FECHA.format(fila.getFechaEvaluacion()),
                fila.getNivelLogro() == null ? "Sin registrar" : fila.getNivelLogro().name(), peso,
                fila.getObservacion() == null || fila.getObservacion().isBlank() ? "Sin observaciones" : fila.getObservacion(),
                fila.getEvaluacion().getTipoEvaluacion().name(),
                Objects.requireNonNullElse(fila.getEstadoEvaluacion(), fila.getEvaluacion().getEstado().name()), tieneSustento,
                informeId == 0L && fila.getEvaluacion().getEscalaInstitucional() != null
                        ? fila.getEvaluacion().getEscalaInstitucional().getNumeroVersion() : null, fila.getNotaNumerica(),
                componente == null ? null : componente.getPeso());
    }

    private ReglaCalificacionDto.Aplicada reglaAplicada(InformeProgreso informe) {
        var regla = informe.getReglaCalificacion();
        return regla == null ? null : new ReglaCalificacionDto.Aplicada(regla.getId(), regla.getNumeroVersion(), regla.getFormula());
    }

    private Optional<InformeProgreso> ultimo(Long alumno, Long curso, Long periodo, boolean publicado) {
        return em.createQuery("""
                select i from InformeProgreso i where i.activo = true and i.alumno.id = :alumno
                and i.asignacionCurso.id = :curso and i.periodoAcademico.id = :periodo
                and (:publicado = false or i.estado = com.asiscontrol.entity.enums.EstadoInformeProgreso.PUBLICADA)
                order by i.numeroVersion desc
                """, InformeProgreso.class).setParameter("alumno", alumno).setParameter("curso", curso)
                .setParameter("periodo", periodo).setParameter("publicado", publicado).setMaxResults(1).getResultStream().findFirst();
    }

    private List<DetalleInformeProgreso> detalles(InformeProgreso informe) {
        return em.createQuery("select d from DetalleInformeProgreso d where d.informe = :informe and d.activo = true order by d.nombreCompetencia, d.fechaEvaluacion, d.id",
                DetalleInformeProgreso.class).setParameter("informe", informe).getResultList();
    }

    private AsignacionCurso asignacion(Long id) {
        return asignaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "Curso no encontrado"));
    }

    private PeriodoAcademico periodo(Long id, AsignacionCurso asignacion) {
        var periodo = periodos.findById(id).filter(p -> p.isActivo() && p.getAnioAcademico().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "Periodo no encontrado"));
        if (periodo.getAnioAcademico().getAnio() != asignacion.getSeccion().getAnioAcademico()) {
            throw new BusinessRuleException("PERIODO_CURSO_INVALIDO", "El periodo no pertenece al año del curso");
        }
        return periodo;
    }

    private String nombre(Persona persona) {
        return Stream.of(persona.getNombres(), persona.getApellidoPaterno(), persona.getApellidoMaterno())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" "));
    }
}
