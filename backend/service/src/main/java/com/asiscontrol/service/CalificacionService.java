package com.asiscontrol.service;


import jakarta.persistence.EntityManager;
import com.asiscontrol.repository.CorreccionCalificacionRepository;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.CorreccionCalificacion;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.ReglaCalificacionDto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.ReglaCalificacion;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Calificacion;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.CalificacionRepository;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import com.asiscontrol.repository.MatriculaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CalificacionService {
    private final FlujoEntregaAcademica flujoEntrega;

    private final CalificacionRepository calificacionRepository;
    private final EvaluacionRepository evaluacionRepository;
    private final AlumnoRepository alumnoRepository;
    private final CompetenciaRepository competenciaRepository;
    private final MatriculaRepository matriculaRepository;
    private final CorreccionCalificacionRepository correccionRepository;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final AvisosAcademicos avisos;
    private final ReglaCalificacionService reglas;
    private final EscalaInstitucionalService escalas;
    private final PlanEstudiosService planes;

    public CalificacionService(
            CalificacionRepository calificacionRepository,
            EvaluacionRepository evaluacionRepository,
            AlumnoRepository alumnoRepository,
            CompetenciaRepository competenciaRepository,
            MatriculaRepository matriculaRepository,
            CorreccionCalificacionRepository correccionRepository,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria, AvisosAcademicos avisos, ReglaCalificacionService reglas,
            EscalaInstitucionalService escalas, PlanEstudiosService planes, FlujoEntregaAcademica flujoEntrega) {
        this.flujoEntrega = flujoEntrega;
        this.correccionRepository = correccionRepository;
        this.entityManager = entityManager;
        this.calificacionRepository = calificacionRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.alumnoRepository = alumnoRepository;
        this.competenciaRepository = competenciaRepository;
        this.matriculaRepository = matriculaRepository;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.reglas = reglas;
        this.escalas = escalas;
        this.planes = planes;
    }

    @Transactional(readOnly = true)
    public Page<CalificacionDto.Response> listar(
            Long idEvaluacion,
            Long idAlumno,
            Long idCompetencia,
            Long personaDocenteId,
            Pageable pageable
    ) {
        return calificacionRepository.buscar(
                        idEvaluacion,
                        idAlumno,
                        idCompetencia,
                        personaDocenteId,
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CalificacionDto.Response obtener(Long id) {
        return toResponse(buscarActiva(id));
    }

    @Transactional
    public CalificacionDto.Response registrar(CalificacionDto.Request request) {
        return toResponse(registrarInterno(request, null));
    }

    @Transactional
    public List<CalificacionDto.Response> registrarLote(CalificacionDto.CargaLoteRequest request) {
        return registrarLote(request, null);
    }

    List<CalificacionDto.Response> registrarLote(CalificacionDto.CargaLoteRequest request, Long tarea) {
        Set<String> claves = new HashSet<>();
        for (CalificacionDto.Request item : request.calificaciones()) {
            String clave = item.idEvaluacion() + ":" + item.idAlumno() + ":" + item.idCompetencia();
            if (!claves.add(clave)) {
                throw new ConflictException(
                        "CALIFICACION_DUPLICADA_EN_LOTE",
                        "El lote contiene mas de una calificacion para la combinacion " + clave
                );
            }
        }

        List<CalificacionDto.Response> resultados = new ArrayList<>();
        for (CalificacionDto.Request item : request.calificaciones()) {
            resultados.add(toResponse(registrarInterno(item, tarea)));
        }
        return resultados;
    }

    /**
     * US-50: corrige niveles ya publicados. Cada cambio deja su version anterior con motivo, autor
     * y fecha; el consolidado usa el nivel vigente, de modo que se actualiza sin mas pasos.
     */
    @Transactional
    public List<CalificacionDto.Response> corregir(CalificacionDto.CorreccionRequest request) {
        return corregir(request, null);
    }

    List<CalificacionDto.Response> corregir(CalificacionDto.CorreccionRequest request, Long tarea) {
        String motivo = request.motivo().trim();
        String autor = entityManager.find(Persona.class, accessGuard.personaIdActual()).nombreCompleto();
        Instant ahora = Instant.now();
        List<CalificacionDto.Response> resultados = new ArrayList<>();
        Set<Long> procesadas = new HashSet<>();
        record ResultadoAvisado(Long evaluacion, Long alumno) {}
        var notificados = new HashSet<ResultadoAvisado>();
        for (CalificacionDto.CorreccionItem item : request.correcciones()) {
            if (!procesadas.add(item.idCalificacion())) {
                throw new ConflictException(
                        "CALIFICACION_DUPLICADA_EN_LOTE",
                        "La correccion incluye mas de una vez la calificacion " + item.idCalificacion()
                );
            }
            var corregida = corregirInterno(item, motivo, autor, ahora, tarea);
            var evaluacion = corregida.getEvaluacion();
            if (notificados.add(new ResultadoAvisado(evaluacion.getId(), corregida.getAlumno().getId()))) {
                avisos.enviar(corregida.getAlumno(), "Resultado actualizado", evaluacion.getTitulo());
            }
            resultados.add(toResponse(corregida));
        }
        return resultados;
    }

    private Calificacion corregirInterno(
            CalificacionDto.CorreccionItem item, String motivo, String autor, Instant fecha, Long tarea) {
        Calificacion calificacion = buscarActiva(item.idCalificacion());
        Evaluacion evaluacion = calificacion.getEvaluacion();
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        flujoEntrega.verificar(evaluacion, tarea);
        if (evaluacion.getEstado() != EstadoEvaluacion.PUBLICADA) {
            throw new BusinessRuleException(
                    "CALIFICACION_NO_CORREGIBLE",
                    "Solo se corrigen calificaciones de una evaluacion publicada"
            );
        }
        if (calificacion.getVersion() != item.version()) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA",
                    "La calificacion fue modificada por otro usuario; vuelva a cargarla"
            );
        }
        NivelLogro anterior = calificacion.getNivelLogro();
        var notaAnterior = calificacion.getNotaNumerica();
        if ((notaAnterior == null) != (item.notaNumerica() == null)) {
            throw new BusinessRuleException("FORMATO_CORRECCION_INCOMPATIBLE",
                    "Corrige el resultado en el formato con el que fue publicado.");
        }
        var nivelNuevo = notaAnterior == null ? item.nivelLogro()
                : EscalaInstitucionalService.nivel(evaluacion.getEscalaInstitucional(), item.notaNumerica());
        if (notaAnterior == null ? anterior == nivelNuevo : notaAnterior.compareTo(item.notaNumerica()) == 0) {
            throw new BusinessRuleException(
                    "CORRECCION_SIN_CAMBIOS",
                    "La calificación nueva debe ser distinta de la vigente"
            );
        }
        CorreccionCalificacion correccion = new CorreccionCalificacion();
        correccion.setCalificacion(calificacion);
        correccion.setNumeroVersion(correccionRepository.countByCalificacionId(calificacion.getId()) + 1);
        correccion.setNivelAnterior(anterior);
        correccion.setNivelNuevo(nivelNuevo);
        correccion.setNotaAnterior(notaAnterior);
        correccion.setNotaNueva(item.notaNumerica());
        correccion.setMotivo(motivo);
        correccion.setAutor(autor);
        correccion.setFechaCorreccion(fecha);
        correccionRepository.save(correccion);
        calificacion.setNivelLogro(nivelNuevo);
        calificacion.setNotaNumerica(item.notaNumerica());
        Calificacion saved = calificacionRepository.saveAndFlush(calificacion);
        auditoria.registrar("CORREGIR_CALIFICACION", ModuloAuditoria.ACADEMICO, "Calificacion",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, motivo,
                "nivelLogro=" + anterior,
                "nivelLogro=" + saved.getNivelLogro()
                        + ",competencia=" + saved.getCompetencia().getNombre()
                        + ",alumnoId=" + saved.getAlumno().getId()
                        + ",version=" + correccion.getNumeroVersion());
        return saved;
    }

    /** US-50: historial de una calificacion: valores, motivos, autores y fechas. */
    @Transactional(readOnly = true)
    public List<CalificacionDto.CorreccionResponse> correcciones(Long id) {
        buscarActiva(id);
        return correccionRepository.findAllByCalificacionIdOrderByNumeroVersionAsc(id).stream()
                .map(item -> new CalificacionDto.CorreccionResponse(
                        item.getNumeroVersion(),
                        item.getNivelAnterior(),
                        item.getNivelNuevo(),
                        item.getMotivo(),
                        item.getAutor(),
                        item.getFechaCorreccion(), item.getNotaAnterior(), item.getNotaNueva()))
                .toList();
    }

    @Transactional
    public void eliminar(Long id) {
        eliminar(id, null, null);
    }

    void eliminar(Long id, Long tarea, Long version) {
        Calificacion calificacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(
                calificacion.getEvaluacion().getAsignacionCurso());
        flujoEntrega.verificar(calificacion.getEvaluacion(), tarea);
        if (calificacion.getEvaluacion().getEstado() != EstadoEvaluacion.BORRADOR) {
            throw new BusinessRuleException(
                    "CALIFICACION_NO_ELIMINABLE",
                    "Las calificaciones solo se pueden quitar mientras la evaluacion esta en borrador"
            );
        }
        if (version != null && !version.equals(calificacion.getVersion())) {
            throw new ConflictException("VERSION_DESACTUALIZADA", "La calificación cambió. Actualiza el seguimiento.");
        }
        calificacion.setActivo(false);
        calificacionRepository.save(calificacion);
        auditoria.registrar("ELIMINAR_CALIFICACION", ModuloAuditoria.ACADEMICO, "Calificacion",
                id.toString(), ResultadoAuditoria.EXITOSO, null, null,
                "nivelLogro=" + calificacion.getNivelLogro() + ",observacion=" + calificacion.getObservacion());    }

    @Transactional(readOnly = true)
    public List<CalificacionDto.ConsolidadoResponse> obtenerConsolidado(
            Long idAlumno,
            Long idAsignacionCurso
    ) {
        alumnoRepository.findByIdAndActivoTrue(idAlumno)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ALUMNO_NO_ENCONTRADO",
                        "No se encontro el alumno con id " + idAlumno
                ));

        Long docente = accessGuard.restriccionDocentePersonaId();
        List<Calificacion> calificaciones = calificacionRepository.buscarParaConsolidado(idAlumno, idAsignacionCurso)
                .stream().filter(nota -> docente == null || docente.equals(nota.getEvaluacion()
                        .getAsignacionCurso().getDocente().getPersona().getId())).toList();
        if (idAsignacionCurso != null) {
            var asignacion = entityManager.find(AsignacionCurso.class, idAsignacionCurso);
            if (asignacion != null && tieneMatricula(idAlumno, asignacion)) {
                accessGuard.verificarConsultaInforme(asignacion, idAlumno);
                var regla = reglas.vigente(asignacion);
                if (regla.isPresent()) return consolidarConRegla(idAlumno, asignacion, regla.get());
            }
            return consolidarHistorico(calificaciones);
        }
        var asignaciones = new java.util.LinkedHashMap<Long, AsignacionCurso>();
        for (var calificacion : calificaciones) {
            var asignacion = calificacion.getEvaluacion().getAsignacionCurso();
            asignaciones.putIfAbsent(asignacion.getId(), asignacion);
        }
        var aplicables = new HashMap<Long, ReglaCalificacion>();
        for (var asignacion : asignaciones.values()) {
            reglas.vigente(asignacion).ifPresent(regla -> aplicables.put(asignacion.getId(), regla));
        }
        if (aplicables.isEmpty()) return consolidarHistorico(calificaciones);
        var resultados = new ArrayList<>(consolidarHistorico(calificaciones.stream()
                .filter(nota -> !aplicables.containsKey(nota.getEvaluacion().getAsignacionCurso().getId())).toList()));
        for (var asignacion : asignaciones.values()) {
            var regla = aplicables.get(asignacion.getId());
            if (regla != null) resultados.addAll(consolidarConRegla(idAlumno, asignacion, regla));
        }
        return resultados;
    }

    private boolean tieneMatricula(Long alumno, AsignacionCurso asignacion) {
        return matriculaRepository.estuvoEnSeccion(alumno, asignacion.getSeccion().getId());
    }

    private List<CalificacionDto.ConsolidadoResponse> consolidarConRegla(Long alumno, AsignacionCurso asignacion,
                                                                       ReglaCalificacion regla) {
        if (regla.getFormula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1) {
            throw new BusinessRuleException("PERIODO_REQUERIDO", "Selecciona un período académico para consolidar.");
        }
        var muestras = entityManager.createQuery("""
                select competencia, e.tipoEvaluacion, nota.nivelLogro, nota.notaNumerica, e.modeloCualitativo
                from Evaluacion e join e.competencias competencia
                left join Calificacion nota on nota.evaluacion = e and nota.competencia = competencia
                    and nota.alumno.id = :alumno and nota.activo = true
                where e.activo = true and e.cuentaResultado = true and e.asignacionCurso.id = :asignacion
                and e.estado in (com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                                 com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA)
                """, Object[].class).setParameter("asignacion", asignacion.getId()).setParameter("alumno", alumno)
                .getResultList().stream().map(fila -> new ReglaCalificacionService.NotaCompetencia(
                        (Competencia) fila[0], new ReglaCalificacionDto.Muestra(
                        (com.asiscontrol.entity.enums.TipoEvaluacion) fila[1], (NivelLogro) fila[2]),
                        (BigDecimal) fila[3], (Boolean) fila[4])).toList();
        var resultados = new ArrayList<CalificacionDto.ConsolidadoResponse>();
        var calculados = reglas.consolidar(regla, asignacion.getId(), muestras);
        var ids = new java.util.HashSet<Long>();
        for (var resultado : calculados) {
            ids.add(resultado.idCompetencia());
            resultados.add(new CalificacionDto.ConsolidadoResponse(resultado.idCompetencia(), resultado.codigoCompetencia(),
                    resultado.nombreCompetencia(), resultado.promedioPonderado(), resultado.nivelConsolidado(),
                    resultado.evaluacionesConsideradas(), resultado.idAsignacionCurso(), resultado.numeroVersionRegla(),
                    resultado.componentesFaltantes(), resultado.numeroVersionEscala(),
                    resultado.nivelConsolidado() == null ? "PENDIENTE" : "CONSOLIDADA"));
        }
        for (var competencia : planes.competenciasDeAsignacion(asignacion)) {
            if (!ids.contains(competencia.getId())) {
                resultados.add(new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                        competencia.getNombre(), null, null, 0, asignacion.getId(), regla.getNumeroVersion(),
                        List.of(), null, "NO_EVALUADA"));
            }
        }
        return resultados;
    }

    private List<CalificacionDto.ConsolidadoResponse> consolidarHistorico(List<Calificacion> calificaciones) {
        Map<Long, AcumuladoCompetencia> acumulados = new HashMap<>();
        var numericas = new java.util.LinkedHashMap<Long, Competencia>();
        for (var nota : calificaciones) {
            if (nota.getNotaNumerica() != null || nota.getEvaluacion().isModeloCualitativo())
                numericas.put(nota.getCompetencia().getId(), nota.getCompetencia());
        }

        for (Calificacion calificacion : calificaciones) {
            if (numericas.containsKey(calificacion.getCompetencia().getId())) continue;
            BigDecimal peso = calificacion.getEvaluacion().getPonderacionPorcentaje();
            if (peso == null || peso.signum() <= 0) {
                peso = BigDecimal.ONE;
            }
            Competencia competencia = calificacion.getCompetencia();
            AcumuladoCompetencia acumulado = acumulados.computeIfAbsent(
                    competencia.getId(),
                    id -> new AcumuladoCompetencia(competencia)
            );
            acumulado.sumaPonderada = acumulado.sumaPonderada.add(
                    BigDecimal.valueOf(calificacion.getNivelLogro().getValor()).multiply(peso)
            );
            acumulado.sumaPesos = acumulado.sumaPesos.add(peso);
            acumulado.cantidad++;
        }

        var resultados = acumulados.values().stream()
                .map(acumulado -> {
                    BigDecimal promedio = acumulado.sumaPonderada
                            .divide(acumulado.sumaPesos, 2, RoundingMode.HALF_UP);
                    return new CalificacionDto.ConsolidadoResponse(
                            acumulado.competencia.getId(),
                            acumulado.competencia.getCodigo(),
                            acumulado.competencia.getNombre(),
                            promedio,
                            NivelLogro.desdePromedio(promedio.doubleValue()),
                            acumulado.cantidad
                    );
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        numericas.values().forEach(competencia -> resultados.add(new CalificacionDto.ConsolidadoResponse(
                competencia.getId(), competencia.getCodigo(), competencia.getNombre(), null, null, 0,
                null, null, List.of("Regla de ponderación vigente"), null)));
        return resultados.stream().sorted((a, b) -> a.nombreCompetencia().compareToIgnoreCase(b.nombreCompetencia())).toList();
    }

    private Calificacion registrarInterno(CalificacionDto.Request request, Long tarea) {
        Evaluacion evaluacion = evaluacionRepository.buscarActivaParaEscritura(request.idEvaluacion())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVALUACION_NO_ENCONTRADA",
                        "No se encontro la evaluacion con id " + request.idEvaluacion()
                ));
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        flujoEntrega.verificar(evaluacion, tarea);
        if (evaluacion.isModeloCualitativo() && request.notaNumerica() != null) {
            throw new BusinessRuleException("NIVEL_CUALITATIVO_REQUERIDO", "Selecciona un nivel AD, A, B o C.");
        }
        // US-18: lo publicado no se edita directamente; se corrige con motivo (US-50).
        if (evaluacion.getEstado() == EstadoEvaluacion.PUBLICADA) {
            throw new BusinessRuleException(
                    "CALIFICACION_PUBLICADA",
                    "La evaluacion esta publicada: use la correccion con motivo"
            );
        }
        if (evaluacion.getEstado() != EstadoEvaluacion.BORRADOR) {
            throw new BusinessRuleException(
                    "EVALUACION_NO_ADMITE_CALIFICACIONES",
                    "Solo se registran calificaciones mientras la evaluacion esta en borrador"
            );
        }

        Alumno alumno = alumnoRepository.findByIdAndActivoTrue(request.idAlumno())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ALUMNO_NO_ENCONTRADO",
                        "No se encontro el alumno con id " + request.idAlumno()
                ));
        if (!matriculaRepository.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(
                alumno.getId(),
                evaluacion.getAsignacionCurso().getSeccion().getId(),
                com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA)) {
            throw new BusinessRuleException(
                    "ALUMNO_NO_MATRICULADO_EN_SECCION",
                    "El alumno no posee una matrícula activa en la sección evaluada"
            );
        }
        Competencia competencia = competenciaRepository.findByIdAndActivoTrue(request.idCompetencia())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COMPETENCIA_NO_ENCONTRADA",
                        "No se encontro la competencia con id " + request.idCompetencia()
                ));
        boolean incluida = evaluacion.getCompetencias().stream()
                .anyMatch(item -> item.getId().equals(competencia.getId()));
        if (!incluida) {
            throw new BusinessRuleException(
                    "COMPETENCIA_NO_EVALUADA",
                    "La competencia no forma parte de la evaluacion indicada"
            );
        }

        Calificacion calificacion = calificacionRepository
                .findByEvaluacionIdAndAlumnoIdAndCompetenciaId(
                        evaluacion.getId(),
                        alumno.getId(),
                        competencia.getId()
                )
                .orElseGet(Calificacion::new);
        boolean nueva = calificacion.getId() == null;
        if (request.versionEsperada() != null
                && request.versionEsperada() != (nueva || !calificacion.isActivo() ? -1L : calificacion.getVersion())) {
            throw new ConflictException("VERSION_DESACTUALIZADA", "La calificación cambió. Actualiza el seguimiento.");
        }
        if (calificacion.getNotaNumerica() != null && request.notaNumerica() == null) {
            throw new BusinessRuleException("NOTA_NUMERICA_REQUERIDA", "Esta calificación requiere una nota numérica.");
        }
        String anterior = null;
        if (!nueva) {
            anterior = "nivelLogro=" + calificacion.getNivelLogro() + ",observacion=" + calificacion.getObservacion();
        }
        calificacion.setEvaluacion(evaluacion);
        calificacion.setAlumno(alumno);
        calificacion.setCompetencia(competencia);
        calificacion.setActivo(true);
        calificacion.setNotaNumerica(request.notaNumerica());
        calificacion.setNivelLogro(request.notaNumerica() == null ? request.nivelLogro()
                : EscalaInstitucionalService.nivel(escalas.exigirVigente(), request.notaNumerica()));
        calificacion.setObservacion(limpiar(request.observacion()));
        if (nueva) {
            calificacion.setFechaRegistro(Instant.now());
        }
        Calificacion saved = calificacionRepository.save(calificacion);
        auditoria.registrar(nueva ? "REGISTRAR_CALIFICACION" : "ACTUALIZAR_CALIFICACION", ModuloAuditoria.ACADEMICO, "Calificacion",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, anterior,
                "nivelLogro=" + saved.getNivelLogro() + ",observacion=" + saved.getObservacion());
        return saved;
    }

    private Calificacion buscarActiva(Long id) {
        return calificacionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CALIFICACION_NO_ENCONTRADA",
                        "No se encontro la calificacion con id " + id
                ));
    }

    private CalificacionDto.Response toResponse(Calificacion calificacion) {
        return new CalificacionDto.Response(
                calificacion.getId(),
                calificacion.getEvaluacion().getId(),
                calificacion.getEvaluacion().getTitulo(),
                calificacion.getAlumno().getId(),
                calificacion.getCompetencia().getId(),
                calificacion.getCompetencia().getNombre(),
                calificacion.getNivelLogro(),
                calificacion.getObservacion(),
                calificacion.getFechaRegistro(),
                calificacion.getFechaActualizacion(),
                calificacion.getVersion()
        );
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static final class AcumuladoCompetencia {
        private final Competencia competencia;
        private BigDecimal sumaPonderada = BigDecimal.ZERO;
        private BigDecimal sumaPesos = BigDecimal.ZERO;
        private int cantidad;

        private AcumuladoCompetencia(Competencia competencia) {
            this.competencia = competencia;
        }
    }
}
