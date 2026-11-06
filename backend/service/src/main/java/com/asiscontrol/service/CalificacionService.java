package com.asiscontrol.service;


import jakarta.persistence.EntityManager;
import com.asiscontrol.repository.CorreccionCalificacionRepository;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.CorreccionCalificacion;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.CalificacionDto;
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

    private final CalificacionRepository calificacionRepository;
    private final EvaluacionRepository evaluacionRepository;
    private final AlumnoRepository alumnoRepository;
    private final CompetenciaRepository competenciaRepository;
    private final MatriculaRepository matriculaRepository;
    private final CorreccionCalificacionRepository correccionRepository;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public CalificacionService(
            CalificacionRepository calificacionRepository,
            EvaluacionRepository evaluacionRepository,
            AlumnoRepository alumnoRepository,
            CompetenciaRepository competenciaRepository,
            MatriculaRepository matriculaRepository,
            CorreccionCalificacionRepository correccionRepository,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.correccionRepository = correccionRepository;
        this.entityManager = entityManager;
        this.calificacionRepository = calificacionRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.alumnoRepository = alumnoRepository;
        this.competenciaRepository = competenciaRepository;
        this.matriculaRepository = matriculaRepository;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
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
        return toResponse(registrarInterno(request));
    }

    @Transactional
    public List<CalificacionDto.Response> registrarLote(CalificacionDto.CargaLoteRequest request) {
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
            resultados.add(toResponse(registrarInterno(item)));
        }
        return resultados;
    }

    /**
     * US-50: corrige niveles ya publicados. Cada cambio deja su version anterior con motivo, autor
     * y fecha; el consolidado usa el nivel vigente, de modo que se actualiza sin mas pasos.
     */
    @Transactional
    public List<CalificacionDto.Response> corregir(CalificacionDto.CorreccionRequest request) {
        String motivo = request.motivo().trim();
        String autor = entityManager.find(Persona.class, accessGuard.personaIdActual()).nombreCompleto();
        Instant ahora = Instant.now();
        List<CalificacionDto.Response> resultados = new ArrayList<>();
        Set<Long> procesadas = new HashSet<>();
        for (CalificacionDto.CorreccionItem item : request.correcciones()) {
            if (!procesadas.add(item.idCalificacion())) {
                throw new ConflictException(
                        "CALIFICACION_DUPLICADA_EN_LOTE",
                        "La correccion incluye mas de una vez la calificacion " + item.idCalificacion()
                );
            }
            resultados.add(toResponse(corregirInterno(item, motivo, autor, ahora)));
        }
        return resultados;
    }

    private Calificacion corregirInterno(
            CalificacionDto.CorreccionItem item, String motivo, String autor, Instant fecha) {
        Calificacion calificacion = buscarActiva(item.idCalificacion());
        Evaluacion evaluacion = calificacion.getEvaluacion();
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
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
        if (anterior == item.nivelLogro()) {
            throw new BusinessRuleException(
                    "CORRECCION_SIN_CAMBIOS",
                    "El nuevo nivel debe ser distinto del nivel vigente"
            );
        }
        CorreccionCalificacion correccion = new CorreccionCalificacion();
        correccion.setCalificacion(calificacion);
        correccion.setNumeroVersion(correccionRepository.countByCalificacionId(calificacion.getId()) + 1);
        correccion.setNivelAnterior(anterior);
        correccion.setNivelNuevo(item.nivelLogro());
        correccion.setMotivo(motivo);
        correccion.setAutor(autor);
        correccion.setFechaCorreccion(fecha);
        correccionRepository.save(correccion);
        calificacion.setNivelLogro(item.nivelLogro());
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
                        item.getFechaCorreccion()))
                .toList();
    }

    @Transactional
    public void eliminar(Long id) {
        Calificacion calificacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(
                calificacion.getEvaluacion().getAsignacionCurso());
        if (calificacion.getEvaluacion().getEstado() != EstadoEvaluacion.BORRADOR) {
            throw new BusinessRuleException(
                    "CALIFICACION_NO_ELIMINABLE",
                    "Las calificaciones solo se pueden quitar mientras la evaluacion esta en borrador"
            );
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

        List<Calificacion> calificaciones = calificacionRepository
                .buscarParaConsolidado(idAlumno, idAsignacionCurso);
        Map<Long, AcumuladoCompetencia> acumulados = new HashMap<>();

        for (Calificacion calificacion : calificaciones) {
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

        return acumulados.values().stream()
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
                .sorted((a, b) -> a.nombreCompetencia().compareToIgnoreCase(b.nombreCompetencia()))
                .toList();
    }

    private Calificacion registrarInterno(CalificacionDto.Request request) {
        Evaluacion evaluacion = evaluacionRepository.findByIdAndActivoTrue(request.idEvaluacion())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVALUACION_NO_ENCONTRADA",
                        "No se encontro la evaluacion con id " + request.idEvaluacion()
                ));
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
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
        String anterior = null;
        if (!nueva) {
            anterior = "nivelLogro=" + calificacion.getNivelLogro() + ",observacion=" + calificacion.getObservacion();
        }
        calificacion.setEvaluacion(evaluacion);
        calificacion.setAlumno(alumno);
        calificacion.setCompetencia(competencia);
        calificacion.setActivo(true);
        calificacion.setNivelLogro(request.nivelLogro());
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
