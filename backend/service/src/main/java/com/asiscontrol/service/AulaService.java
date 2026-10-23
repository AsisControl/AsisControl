package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AulaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AulaService {

    private final AulaRepository aulaRepository;
    private final AuditoriaService auditoria;

    public AulaService(AulaRepository aulaRepository, AuditoriaService auditoria) {
        this.aulaRepository = aulaRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public AulaResponse crear(CrearAulaRequest request) {
        validarCodigoUnico(request.codigo(), null);
        Aula aula = new Aula();
        aplicar(aula, request);
        aula.setActivo(true);
        Aula saved = aulaRepository.save(aula);
        auditoria.registrar("CREAR_AULA", "ACADEMICO", "Aula",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "codigo=" + saved.getCodigo() + ",nombre=" + saved.getNombre());
        return mapear(saved);
    }

    @Transactional
    public AulaResponse actualizar(Long id, ActualizarAulaRequest request) {
        Aula aula = obtenerEntidad(id);
        exigirVersion(aula.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        String anterior = "codigo=" + aula.getCodigo() + ",nombre=" + aula.getNombre();
        aula.setCodigo(request.codigo().trim().toUpperCase());
        aula.setNombre(request.nombre().trim());
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(request.ubicacion().trim());
        Aula saved = aulaRepository.save(aula);
        auditoria.registrar("ACTUALIZAR_AULA", "ACADEMICO", "Aula",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior,
                "codigo=" + saved.getCodigo() + ",nombre=" + saved.getNombre());
        return mapear(saved);
    }

    public AulaResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<AulaResponse> listar(Pageable pageable) {
        return aulaRepository.findByActivoTrue(pageable).map(this::mapear);
    }

    @Transactional
    public void eliminar(Long id) {
        Aula aula = obtenerEntidad(id);
        aula.setActivo(false);
        aulaRepository.save(aula);
        auditoria.registrar("ELIMINAR_AULA", "ACADEMICO", "Aula",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "codigo=" + aula.getCodigo() + ",nombre=" + aula.getNombre(), null);
    }

    public Aula obtenerEntidad(Long id) {
        return aulaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AULA_NO_ENCONTRADA", "No existe un aula activa con id " + id));
    }

    private void aplicar(Aula aula, CrearAulaRequest request) {
        aula.setCodigo(request.codigo().trim().toUpperCase());
        aula.setNombre(request.nombre().trim());
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(request.ubicacion().trim());
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        aulaRepository.findByCodigoIgnoreCaseAndActivoTrue(codigo.trim())
                .filter(existente -> idActual == null || !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictException("AULA_CODIGO_DUPLICADO", "Ya existe un aula con ese codigo");
                });
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
