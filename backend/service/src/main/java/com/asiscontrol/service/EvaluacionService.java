package com.asiscontrol.service;


import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.CompetenciaDto;
import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvaluacionService {

    private final EvaluacionRepository evaluacionRepository;
    private final AsignacionCursoRepository asignacionCursoRepository;
    private final CompetenciaRepository competenciaRepository;
    private final ModuloAccessGuard accessGuard;

    public EvaluacionService(
            EvaluacionRepository evaluacionRepository,
            AsignacionCursoRepository asignacionCursoRepository,
            CompetenciaRepository competenciaRepository,
            ModuloAccessGuard accessGuard
    ) {
        this.evaluacionRepository = evaluacionRepository;
        this.asignacionCursoRepository = asignacionCursoRepository;
        this.competenciaRepository = competenciaRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public Page<EvaluacionDto.Response> listar(
            EstadoEvaluacion estado,
            Long idAsignacionCurso,
            String busqueda,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        String filtro = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        return evaluacionRepository.buscar(
                        estado,
                        idAsignacionCurso,
                        filtro,
                        personaDocenteId,
                        personaAlumnoId,
                        personaApoderadoId,
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EvaluacionDto.Response obtener(Long id) {
        return toResponse(buscarActiva(id));
    }

    @Transactional
    public EvaluacionDto.Response crear(EvaluacionDto.Request request) {
        Evaluacion evaluacion = new Evaluacion();
        aplicarDatosEditables(evaluacion, request);
        evaluacion.setEstado(EstadoEvaluacion.BORRADOR);
        return toResponse(evaluacionRepository.save(evaluacion));
    }

    @Transactional
    public EvaluacionDto.Response actualizar(Long id, EvaluacionDto.Request request) {
        Evaluacion evaluacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        if (evaluacion.getEstado() != EstadoEvaluacion.BORRADOR) {
            throw new BusinessRuleException(
                    "EVALUACION_NO_EDITABLE",
                    "Solo una evaluacion en borrador puede modificar sus datos principales"
            );
        }
        aplicarDatosEditables(evaluacion, request);
        return toResponse(evaluacionRepository.save(evaluacion));
    }

    @Transactional
    public EvaluacionDto.Response cambiarEstado(Long id, EstadoEvaluacion estadoDestino) {
        Evaluacion evaluacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        EstadoEvaluacion estadoActual = evaluacion.getEstado();
        if (estadoActual == estadoDestino) {
            return toResponse(evaluacion);
        }
        validarTransicion(evaluacion, estadoDestino);

        evaluacion.setEstado(estadoDestino);
        if (estadoDestino == EstadoEvaluacion.PUBLICADA) {
            evaluacion.setFechaPublicacion(Instant.now());
            evaluacion.setFechaCierre(null);
        } else if (estadoDestino == EstadoEvaluacion.CERRADA) {
            evaluacion.setFechaCierre(Instant.now());
        }
        return toResponse(evaluacionRepository.save(evaluacion));
    }

    @Transactional
    public void eliminar(Long id) {
        Evaluacion evaluacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        if (evaluacion.getEstado() != EstadoEvaluacion.BORRADOR
                && evaluacion.getEstado() != EstadoEvaluacion.ANULADA) {
            throw new BusinessRuleException(
                    "EVALUACION_NO_ELIMINABLE",
                    "Solo se puede eliminar logicamente una evaluacion en borrador o anulada"
            );
        }
        evaluacion.setActivo(false);
        evaluacionRepository.save(evaluacion);
    }

    private void aplicarDatosEditables(Evaluacion evaluacion, EvaluacionDto.Request request) {
        AsignacionCurso asignacion = asignacionCursoRepository.findByIdAndActivoTrue(request.idAsignacionCurso())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASIGNACION_CURSO_NO_ENCONTRADA",
                        "No se encontro la asignacion de curso con id " + request.idAsignacionCurso()
                ));
        accessGuard.verificarAsignacionDocente(asignacion);
        Set<Long> idsUnicos = new LinkedHashSet<>(request.idsCompetencia());
        List<Competencia> competencias = competenciaRepository.findAllByIdInAndActivoTrue(idsUnicos);
        if (competencias.size() != idsUnicos.size()) {
            throw new ResourceNotFoundException(
                    "COMPETENCIA_NO_ENCONTRADA",
                    "Una o mas competencias no existen o estan inactivas"
            );
        }

        evaluacion.setAsignacionCurso(asignacion);
        evaluacion.setTitulo(request.titulo().trim());
        evaluacion.setDescripcion(limpiar(request.descripcion()));
        evaluacion.setTipoEvaluacion(request.tipoEvaluacion());
        evaluacion.setPeriodoEvaluacion(request.periodoEvaluacion());
        evaluacion.setFechaEvaluacion(request.fechaEvaluacion());
        evaluacion.setPonderacionPorcentaje(request.ponderacionPorcentaje());
        evaluacion.setCompetencias(new LinkedHashSet<>(competencias));
    }

    private void validarTransicion(Evaluacion evaluacion, EstadoEvaluacion destino) {
        EstadoEvaluacion actual = evaluacion.getEstado();
        boolean permitida = switch (actual) {
            case BORRADOR -> destino == EstadoEvaluacion.PUBLICADA || destino == EstadoEvaluacion.ANULADA;
            case PUBLICADA -> destino == EstadoEvaluacion.CERRADA || destino == EstadoEvaluacion.ANULADA;
            case CERRADA, ANULADA -> false;
        };
        if (!permitida) {
            throw new BusinessRuleException(
                    "TRANSICION_EVALUACION_INVALIDA",
                    "No se puede cambiar una evaluacion de " + actual + " a " + destino
            );
        }
        if (destino == EstadoEvaluacion.PUBLICADA && evaluacion.getCompetencias().isEmpty()) {
            throw new BusinessRuleException(
                    "EVALUACION_SIN_COMPETENCIAS",
                    "La evaluacion debe incluir al menos una competencia antes de publicarse"
            );
        }
    }

    private Evaluacion buscarActiva(Long id) {
        return evaluacionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVALUACION_NO_ENCONTRADA",
                        "No se encontro la evaluacion con id " + id
                ));
    }

    private EvaluacionDto.Response toResponse(Evaluacion evaluacion) {
        Set<CompetenciaDto.Response> competencias = evaluacion.getCompetencias().stream()
                .map(competencia -> new CompetenciaDto.Response(
                        competencia.getId(),
                        competencia.getCodigo(),
                        competencia.getNombre(),
                        competencia.getDescripcion(),
                        competencia.isActivo(),
                        competencia.getFechaCreacion(),
                        competencia.getFechaActualizacion()
                ))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new EvaluacionDto.Response(
                evaluacion.getId(),
                evaluacion.getAsignacionCurso().getId(),
                evaluacion.getTitulo(),
                evaluacion.getDescripcion(),
                evaluacion.getTipoEvaluacion(),
                evaluacion.getPeriodoEvaluacion(),
                evaluacion.getEstado(),
                evaluacion.getFechaEvaluacion(),
                evaluacion.getPonderacionPorcentaje(),
                competencias,
                evaluacion.getFechaPublicacion(),
                evaluacion.getFechaCierre(),
                evaluacion.getFechaCreacion(),
                evaluacion.getFechaActualizacion()
        );
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
