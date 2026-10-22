package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResumenResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAula;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AulaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AulaService {

    private final AulaRepository aulaRepository;

    public AulaService(AulaRepository aulaRepository) {
        this.aulaRepository = aulaRepository;
    }

    @Transactional
    public AulaResponse crear(CrearAulaRequest request) {
        validarCapacidad(request.capacidad());
        validarCodigoUnico(request.codigo(), null);
        Aula aula = new Aula();
        aplicar(aula, request);
        aula.setActivo(true);
        return mapear(aulaRepository.save(aula));
    }

    @Transactional
    public AulaResponse actualizar(Long id, ActualizarAulaRequest request) {
        validarCapacidad(request.capacidad());
        Aula aula = obtenerEntidad(id);
        exigirVersion(aula.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        aula.setCodigo(normalizarCodigo(request.codigo()));
        aula.setNombre(request.nombre().trim());
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(normalizarUbicacion(request.ubicacion()));
        return mapear(aulaRepository.save(aula));
    }

    public AulaResponse obtener(Long id) {
        return mapear(obtenerEntidadSinFiltroActivo(id));
    }

    public Page<AulaResponse> listar(Pageable pageable) {
        return listar(null, pageable);
    }

    public Page<AulaResponse> listar(EstadoAula estado, Pageable pageable) {
        if (estado == null || estado == EstadoAula.HABILITADAS) {
            return aulaRepository.findByActivoTrue(pageable).map(this::mapear);
        }
        if (estado == EstadoAula.INHABILITADAS) {
            return aulaRepository.findByActivoFalse(pageable).map(this::mapear);
        }
        return aulaRepository.findAll(pageable).map(this::mapear);
    }

    public AulaResumenResponse resumen() {
        return new AulaResumenResponse(aulaRepository.countByActivoTrue());
    }

    @Transactional
    public void eliminar(Long id) {
        Aula aula = obtenerEntidad(id);
        aula.setActivo(false);
        aulaRepository.save(aula);
    }

    public Aula obtenerEntidad(Long id) {
        return aulaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AULA_NO_ENCONTRADA", "No existe un aula activa con id " + id));
    }

    private Aula obtenerEntidadSinFiltroActivo(Long id) {
        return aulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AULA_NO_ENCONTRADA", "No existe un aula con id " + id));
    }

    private void aplicar(Aula aula, CrearAulaRequest request) {
        aula.setCodigo(normalizarCodigo(request.codigo()));
        aula.setNombre(request.nombre().trim());
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(normalizarUbicacion(request.ubicacion()));
    }

    private void validarCapacidad(Integer capacidad) {
        if (capacidad == null || capacidad < 1)
            throw new BusinessRuleException(
                    "AULA_CAPACIDAD_INVALIDA", "La capacidad del aula debe ser un entero positivo");
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        aulaRepository.findByCodigoIgnoreCase(normalizarCodigo(codigo))
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException("AULA_CODIGO_DUPLICADO", "Ya existe un aula con ese codigo");
                });
    }

    private String normalizarCodigo(String codigo) {
        return codigo.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizarUbicacion(String ubicacion) {
        // La columna sigue siendo NOT NULL; "" representa sin ubicacion hasta V2.
        return ubicacion == null || ubicacion.isBlank() ? "" : ubicacion.trim();
    }

    private AulaResponse mapear(Aula aula) {
        return new AulaResponse(
                aula.getId(), aula.getCodigo(), aula.getNombre(), aula.getTipoAula(), aula.getCapacidad(),
                aula.getUbicacion(), aula.isActivo(), aula.getVersion());
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "El aula fue modificada; vuelva a cargarla");
        }
    }
}
