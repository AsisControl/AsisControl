package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CursoResponse;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.CursoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CursoService {

    private final CursoRepository cursoRepository;

    public CursoService(CursoRepository cursoRepository) {
        this.cursoRepository = cursoRepository;
    }

    @Transactional
    public CursoResponse crear(CrearCursoRequest request) {
        validarCodigoUnico(request.codigo(), null);
        Curso curso = new Curso();
        aplicar(curso, request.codigo(), request.nombre(), request.descripcion(), request.horasSemanales());
        curso.setActivo(true);
        return mapear(cursoRepository.save(curso));
    }

    @Transactional
    public CursoResponse actualizar(Long id, ActualizarCursoRequest request) {
        Curso curso = obtenerEntidad(id);
        exigirVersion(curso.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        aplicar(curso, request.codigo(), request.nombre(), request.descripcion(), request.horasSemanales());
        return mapear(cursoRepository.save(curso));
    }

    public CursoResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<CursoResponse> listar(String busqueda, Pageable pageable) {
        String criterio = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        return cursoRepository.buscar(criterio, pageable).map(this::mapear);
    }

    @Transactional
    public void eliminar(Long id) {
        Curso curso = obtenerEntidad(id);
        curso.setActivo(false);
        cursoRepository.save(curso);
    }

    public Curso obtenerEntidad(Long id) {
        return cursoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CURSO_NO_ENCONTRADO", "No existe un curso activo con id " + id));
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        cursoRepository.findByCodigoIgnoreCaseAndActivoTrue(codigo.trim())
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException("CURSO_CODIGO_DUPLICADO", "Ya existe un curso con ese codigo");
                });
    }

    private void aplicar(Curso curso, String codigo, String nombre, String descripcion, int horasSemanales) {
        curso.setCodigo(codigo.trim().toUpperCase());
        curso.setNombre(nombre.trim());
        curso.setDescripcion(normalizar(descripcion));
        curso.setHorasSemanales(horasSemanales);
    }

    private CursoResponse mapear(Curso curso) {
        return new CursoResponse(
                curso.getId(), curso.getCodigo(), curso.getNombre(), curso.getDescripcion(),
                curso.getHorasSemanales(), curso.isActivo(), curso.getVersion());
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "El curso fue modificado; vuelva a cargarlo");
        }
    }
}
