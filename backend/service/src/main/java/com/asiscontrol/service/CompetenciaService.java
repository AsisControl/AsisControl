package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CompetenciaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCompetenciaRequest;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.CompetenciaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CompetenciaService {

    private final CompetenciaRepository competenciaRepository;

    public CompetenciaService(CompetenciaRepository competenciaRepository) {
        this.competenciaRepository = competenciaRepository;
    }

    @Transactional
    public CompetenciaResponse crear(CrearCompetenciaRequest request) {
        validarCodigoUnico(request.codigo(), null);
        Competencia competencia = new Competencia();
        aplicar(competencia, request.codigo(), request.nombre(), request.descripcion());
        competencia.setActivo(true);
        return mapear(competenciaRepository.save(competencia));
    }

    @Transactional
    public CompetenciaResponse actualizar(Long id, ActualizarCompetenciaRequest request) {
        Competencia competencia = obtenerEntidad(id);
        exigirVersion(competencia.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        aplicar(competencia, request.codigo(), request.nombre(), request.descripcion());
        return mapear(competenciaRepository.save(competencia));
    }

    public CompetenciaResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<CompetenciaResponse> listar(String busqueda, Pageable pageable) {
        String criterio = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        return competenciaRepository.buscar(criterio, pageable).map(this::mapear);
    }

    @Transactional
    public void eliminar(Long id) {
        Competencia competencia = obtenerEntidad(id);
        competencia.setActivo(false);
        competenciaRepository.save(competencia);
    }

    public Competencia obtenerEntidad(Long id) {
        return competenciaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "COMPETENCIA_NO_ENCONTRADA", "No existe una competencia activa con id " + id));
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        competenciaRepository.findByCodigoIgnoreCaseAndActivoTrue(codigo.trim())
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException(
                            "COMPETENCIA_CODIGO_DUPLICADO", "Ya existe una competencia con ese codigo");
                });
    }

    private void aplicar(Competencia competencia, String codigo, String nombre, String descripcion) {
        competencia.setCodigo(codigo.trim().toUpperCase());
        competencia.setNombre(nombre.trim());
        competencia.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
    }

    private CompetenciaResponse mapear(Competencia competencia) {
        return new CompetenciaResponse(
                competencia.getId(), competencia.getCodigo(), competencia.getNombre(),
                competencia.getDescripcion(), competencia.isActivo(), competencia.getVersion());
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La competencia fue modificada; vuelva a cargarla");
        }
    }
}
