package com.asiscontrol.service;


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
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public CalificacionService(
            CalificacionRepository calificacionRepository,
            EvaluacionRepository evaluacionRepository,
            AlumnoRepository alumnoRepository,
            CompetenciaRepository competenciaRepository,
            MatriculaRepository matriculaRepository,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
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

    @Transactional
    public void eliminar(Long id) {
        Calificacion calificacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(
                calificacion.getEvaluacion().getAsignacionCurso());
        if (calificacion.getEvaluacion().getEstado() != EstadoEvaluacion.PUBLICADA) {
            throw new BusinessRuleException(
                    "CALIFICACION_NO_ELIMINABLE",
                    "Las calificaciones solo se pueden modificar mientras la evaluacion esta publicada"
            );
        }
        calificacion.setActivo(false);
        calificacionRepository.save(calificacion);
        auditoria.registrar("ELIMINAR_CALIFICACION", "ACADEMICO", "Calificacion",
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
        if (evaluacion.getEstado() != EstadoEvaluacion.PUBLICADA) {
            throw new BusinessRuleException(
                    "EVALUACION_NO_ADMITE_CALIFICACIONES",
                    "La evaluacion debe estar publicada para registrar calificaciones"
            );
        }
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());

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
        auditoria.registrar(nueva ? "REGISTRAR_CALIFICACION" : "ACTUALIZAR_CALIFICACION", "ACADEMICO", "Calificacion",
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
                calificacion.getFechaActualizacion()
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
