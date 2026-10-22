package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResumenResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAula;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AulaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AulaService {

    private static final String MODULO = "ACADEMICO";
    private static final String ENTIDAD = "Aula";

    private final AulaRepository aulaRepository;
    private final AuditoriaService auditoriaService;

    public AulaService(AulaRepository aulaRepository, AuditoriaService auditoriaService) {
        this.aulaRepository = aulaRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public AulaResponse crear(CrearAulaRequest request) {
        validarCapacidad(request.capacidad());
        validarCodigoUnico(request.codigo(), null);
        Aula aula = new Aula();
        aplicar(aula, request);
        aula.setActivo(true);
        Aula guardada = aulaRepository.save(aula);
        auditar("CREAR_AULA", guardada, null, null, describir(guardada));
        return mapear(guardada);
    }

    @Transactional
    public AulaResponse actualizar(Long id, ActualizarAulaRequest request) {
        validarCapacidad(request.capacidad());
        Aula aula = obtenerEntidad(id);
        exigirVersion(aula.getVersion(), request.version());
        validarCodigoUnico(request.codigo(), id);
        String codigo = normalizarCodigo(request.codigo());
        String nombre = request.nombre().trim();
        String ubicacion = normalizarUbicacion(request.ubicacion());
        StringBuilder anterior = new StringBuilder();
        StringBuilder nuevo = new StringBuilder();
        anotarCambio(anterior, nuevo, "codigo", aula.getCodigo(), codigo);
        anotarCambio(anterior, nuevo, "nombre", aula.getNombre(), nombre);
        anotarCambio(anterior, nuevo, "tipoAula", aula.getTipoAula(), request.tipoAula());
        anotarCambio(anterior, nuevo, "ubicacion", aula.getUbicacion(), ubicacion);
        Integer capacidadAnterior = aula.getCapacidad();
        aula.setCodigo(codigo);
        aula.setNombre(nombre);
        aula.setTipoAula(request.tipoAula());
        aula.setCapacidad(request.capacidad());
        aula.setUbicacion(ubicacion);
        Aula guardada = aulaRepository.save(aula);
        if (!nuevo.isEmpty()) {
            auditar("ACTUALIZAR_AULA", guardada, null, anterior.toString(), nuevo.toString());
        }
        if (!capacidadAnterior.equals(request.capacidad())) {
            auditar("CAMBIAR_CAPACIDAD_AULA", guardada, null,
                    "capacidad=" + capacidadAnterior, "capacidad=" + request.capacidad());
        }
        return mapear(guardada);
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

    private void auditar(String accion, Aula aula, String motivo, String valorAnterior, String nuevoValor) {
        // Misma transaccion que la operacion: si esta se revierte, no queda registro.
        auditoriaService.registrar(
                accion, MODULO, ENTIDAD, aula.getId().toString(), ResultadoAuditoria.EXITOSO,
                motivo, valorAnterior, nuevoValor);
    }

    private String describir(Aula aula) {
        return "codigo=" + aula.getCodigo()
                + ",nombre=" + aula.getNombre()
                + ",tipoAula=" + aula.getTipoAula()
                + ",capacidad=" + aula.getCapacidad()
                + ",ubicacion=" + aula.getUbicacion()
                + ",activo=" + aula.isActivo();
    }

    private void anotarCambio(StringBuilder anterior, StringBuilder nuevo, String campo, Object antes, Object despues) {
        if (Objects.equals(antes, despues)) {
            return;
        }
        if (!nuevo.isEmpty()) {
            anterior.append(',');
            nuevo.append(',');
        }
        anterior.append(campo).append('=').append(antes);
        nuevo.append(campo).append('=').append(despues);
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "El aula fue modificada; vuelva a cargarla");
        }
    }
}
