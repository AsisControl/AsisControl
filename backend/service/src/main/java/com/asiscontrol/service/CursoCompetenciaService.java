package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCursoCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CursoCompetenciaResponse;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.CursoCompetencia;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
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
    private final AuditoriaService auditoria;

    public CursoCompetenciaService(
            CursoCompetenciaRepository cursoCompetenciaRepository,
            CursoRepository cursoRepository,
            CompetenciaRepository competenciaRepository,
            AuditoriaService auditoria) {
        this.cursoCompetenciaRepository = cursoCompetenciaRepository;
        this.cursoRepository = cursoRepository;
        this.competenciaRepository = competenciaRepository;
        this.auditoria = auditoria;
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
        CursoCompetencia saved = cursoCompetenciaRepository.saveAndFlush(relacion);
        boolean creada = existente.isEmpty();
        auditoria.registrar(creada ? "CREAR_CURSO_COMPETENCIA" : "REACTIVAR_CURSO_COMPETENCIA",
                ModuloAuditoria.ACADEMICO, "CursoCompetencia",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null,
                creada ? null : "activo=false",
                "cursoId=" + saved.getCurso().getId() + ",competenciaId=" + saved.getCompetencia().getId());
        return new ResultadoAsociacion(mapear(saved), creada);
    }

    @Transactional
    public CursoCompetenciaResponse actualizar(Long id, ActualizarCursoCompetenciaRequest request) {
        CursoCompetencia relacion = obtenerEntidad(id);
        exigirVersion(relacion.getVersion(), request.version());
        validarUnica(request.cursoId(), request.competenciaId(), id);
        String anterior = "cursoId=" + relacion.getCurso().getId()
                + ",competenciaId=" + relacion.getCompetencia().getId() + ",orden=" + relacion.getOrden();
        relacion.setCurso(buscarCurso(request.cursoId()));
        relacion.setCompetencia(buscarCompetencia(request.competenciaId()));
        relacion.setOrden(request.orden());
        CursoCompetencia saved = cursoCompetenciaRepository.saveAndFlush(relacion);
        auditoria.registrar("ACTUALIZAR_CURSO_COMPETENCIA", ModuloAuditoria.ACADEMICO, "CursoCompetencia",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, anterior,
                "cursoId=" + saved.getCurso().getId() + ",competenciaId=" + saved.getCompetencia().getId()
                        + ",orden=" + saved.getOrden());
        return mapear(saved);
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
        auditoria.registrar("ELIMINAR_CURSO_COMPETENCIA", ModuloAuditoria.ACADEMICO, "CursoCompetencia",
                relacion.getId().toString(), ResultadoAuditoria.EXITOSO, null,
                "cursoId=" + relacion.getCurso().getId() + ",competenciaId=" + relacion.getCompetencia().getId(), null);
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
