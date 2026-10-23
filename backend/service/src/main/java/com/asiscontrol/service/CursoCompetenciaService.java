package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCursoCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CursoCompetenciaResponse;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.CursoCompetencia;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.CursoCompetenciaRepository;
import com.asiscontrol.repository.CursoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CursoCompetenciaService {

    public record ResultadoAsociacion(CursoCompetenciaResponse relacion, boolean creada) {}

    private final CursoCompetenciaRepository cursoCompetenciaRepository;
    private final CursoRepository cursoRepository;
    private final CompetenciaRepository competenciaRepository;

    public CursoCompetenciaService(
            CursoCompetenciaRepository cursoCompetenciaRepository,
            CursoRepository cursoRepository,
            CompetenciaRepository competenciaRepository) {
        this.cursoCompetenciaRepository = cursoCompetenciaRepository;
        this.cursoRepository = cursoRepository;
        this.competenciaRepository = competenciaRepository;
    }

    @Transactional
    public ResultadoAsociacion crear(CrearCursoCompetenciaRequest request) {
        Curso curso = buscarCurso(request.cursoId());
        Competencia competencia = buscarCompetencia(request.competenciaId());
        var existente = cursoCompetenciaRepository
                .findByCursoIdAndCompetenciaId(request.cursoId(), request.competenciaId());
        if (existente.filter(CursoCompetencia::isActivo).isPresent()) {
            throw new ConflictException(
                    "CURSO_COMPETENCIA_DUPLICADA", "La competencia ya esta asociada al curso");
        }

        // Recuperar la misma fila conserva su identidad y fecha de creacion.
        CursoCompetencia relacion = existente.orElseGet(CursoCompetencia::new);
        relacion.setCurso(curso);
        relacion.setCompetencia(competencia);
        relacion.setOrden(request.orden());
        relacion.setActivo(true);
        return new ResultadoAsociacion(
                mapear(cursoCompetenciaRepository.saveAndFlush(relacion)), existente.isEmpty());
    }

    @Transactional
    public CursoCompetenciaResponse actualizar(Long id, ActualizarCursoCompetenciaRequest request) {
        CursoCompetencia relacion = obtenerEntidad(id);
        exigirVersion(relacion.getVersion(), request.version());
        validarUnica(request.cursoId(), request.competenciaId(), id);
        relacion.setCurso(buscarCurso(request.cursoId()));
        relacion.setCompetencia(buscarCompetencia(request.competenciaId()));
        relacion.setOrden(request.orden());
        return mapear(cursoCompetenciaRepository.saveAndFlush(relacion));
    }

    public CursoCompetenciaResponse obtener(Long id) {
        CursoCompetencia relacion = cursoCompetenciaRepository
                .buscarDisponiblePorId(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CURSO_COMPETENCIA_NO_DISPONIBLE",
                        "No existe una relacion curso-competencia disponible con id " + id));

        return mapear(relacion);
    }

    public Page<CursoCompetenciaResponse> listar(Pageable pageable) {
        return cursoCompetenciaRepository.buscarDisponibles(pageable).map(this::mapear);
    }

    @Transactional
    public void eliminar(Long id) {
        CursoCompetencia relacion = obtenerEntidad(id);
        relacion.setActivo(false);
        cursoCompetenciaRepository.save(relacion);
    }

    private CursoCompetencia obtenerEntidad(Long id) {
        return cursoCompetenciaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CURSO_COMPETENCIA_NO_ENCONTRADA",
                        "No existe una relacion curso-competencia activa con id " + id));
    }

    private Curso buscarCurso(Long id) {
        return cursoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CURSO_NO_ENCONTRADO", "No existe un curso activo con id " + id));
    }

    private Competencia buscarCompetencia(Long id) {
        return competenciaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COMPETENCIA_NO_ENCONTRADA", "No existe una competencia activa con id " + id));
    }

    private void validarUnica(Long cursoId, Long competenciaId, Long idActual) {
        cursoCompetenciaRepository.findByCursoIdAndCompetenciaId(cursoId, competenciaId)
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException(
                            "CURSO_COMPETENCIA_DUPLICADA",
                            "Ya existe una relacion entre el curso y la competencia, activa o inactiva");
                });
    }

    private CursoCompetenciaResponse mapear(CursoCompetencia relacion) {
        return new CursoCompetenciaResponse(
                relacion.getId(), relacion.getCurso().getId(), relacion.getCurso().getCodigo(),
                relacion.getCurso().getNombre(), relacion.getCompetencia().getId(),
                relacion.getCompetencia().getCodigo(), relacion.getCompetencia().getNombre(),
                relacion.getOrden(), relacion.isActivo(), relacion.getVersion());
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La relacion fue modificada; vuelva a cargarla");
        }
    }
}
