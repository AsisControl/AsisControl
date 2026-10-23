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
    public CursoCompetenciaResponse crear(CrearCursoCompetenciaRequest request) {
        validarUnica(request.cursoId(), request.competenciaId(), null);
        CursoCompetencia relacion = new CursoCompetencia();
        relacion.setCurso(buscarCurso(request.cursoId()));
        relacion.setCompetencia(buscarCompetencia(request.competenciaId()));
        relacion.setOrden(request.orden());
        relacion.setActivo(true);
        return mapear(cursoCompetenciaRepository.save(relacion));
    }

    @Transactional
    public CursoCompetenciaResponse actualizar(Long id, ActualizarCursoCompetenciaRequest request) {
        CursoCompetencia relacion = obtenerEntidad(id);
        exigirVersion(relacion.getVersion(), request.version());
        validarUnica(request.cursoId(), request.competenciaId(), id);
        relacion.setCurso(buscarCurso(request.cursoId()));
        relacion.setCompetencia(buscarCompetencia(request.competenciaId()));
        relacion.setOrden(request.orden());
        return mapear(cursoCompetenciaRepository.save(relacion));
    }

    public CursoCompetenciaResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<CursoCompetenciaResponse> listar(Pageable pageable) {
        return cursoCompetenciaRepository.findByActivoTrue(pageable).map(this::mapear);
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
        cursoCompetenciaRepository.findByCursoIdAndCompetenciaIdAndActivoTrue(cursoId, competenciaId)
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException(
                            "CURSO_COMPETENCIA_DUPLICADA",
                            "La competencia ya esta asociada al curso");
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
