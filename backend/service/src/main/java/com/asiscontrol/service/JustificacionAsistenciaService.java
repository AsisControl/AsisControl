package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.CrearJustificacionRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.JustificacionAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RevisarJustificacionRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.SubsanarJustificacionRequest;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.JustificacionAsistenciaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.asiscontrol.service.AuditoriaService;
import com.asiscontrol.entity.enums.ResultadoAuditoria;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class JustificacionAsistenciaService {

    private static final Set<EstadoAsistencia> ESTADOS_JUSTIFICABLES = Set.of(
            EstadoAsistencia.TARDANZA,
            EstadoAsistencia.INASISTENCIA,
            EstadoAsistencia.SIN_MARCACION);

    private final JustificacionAsistenciaRepository justificacionRepository;
    private final AsistenciaAlumnoRepository asistenciaRepository;
    private final AsistenciaAlumnoService asistenciaService;
    private final ArchivoStorageService archivoService;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public JustificacionAsistenciaService(
            JustificacionAsistenciaRepository justificacionRepository,
            AsistenciaAlumnoRepository asistenciaRepository,
            AsistenciaAlumnoService asistenciaService,
            ArchivoStorageService archivoService,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.justificacionRepository = justificacionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.asistenciaService = asistenciaService;
        this.archivoService = archivoService;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
    }

    @Transactional
    public JustificacionAsistenciaResponse crear(CrearJustificacionRequest request) {
        AsistenciaAlumno asistencia = asistenciaService.obtenerEntidad(request.asistenciaAlumnoId());
        accessGuard.verificarSolicitanteJustificacion(asistencia.getAlumno());
        if (!ESTADOS_JUSTIFICABLES.contains(asistencia.getEstadoAsistencia())) {
            throw new BusinessRuleException(
                    "ASISTENCIA_NO_JUSTIFICABLE", "El estado actual de asistencia no admite justificación");
        }
        if (justificacionRepository.findByAsistenciaAlumnoIdAndActivoTrue(asistencia.getId()).isPresent()) {
            throw new ConflictException(
                    "JUSTIFICACION_DUPLICADA", "La asistencia ya posee una justificación activa");
        }

        JustificacionAsistencia justificacion = new JustificacionAsistencia();
        justificacion.setAsistenciaAlumno(asistencia);
        justificacion.setMotivo(request.motivo().trim());
        justificacion.setFechaHoraEnvio(LocalDateTime.now());
        justificacion.setEstado(EstadoJustificacion.ENVIADA);
        justificacion.setArchivos(archivoService.obtenerActivos(request.archivoIds()));
        justificacion.setActivo(true);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "CREAR_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse iniciarRevision(Long id, RevisarJustificacionRequest request) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        exigirEstado(justificacion, EstadoJustificacion.ENVIADA, "Solo se revisan justificaciones enviadas");
        aplicarRevision(justificacion, request, EstadoJustificacion.EN_REVISION);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "INICIAR_REVISION_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse observar(Long id, RevisarJustificacionRequest request) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        exigirEstado(justificacion, EstadoJustificacion.EN_REVISION, "Solo se observa una justificación en revisión");
        if (request.observacion() == null || request.observacion().isBlank()) {
            throw new BusinessRuleException(
                    "JUSTIFICACION_OBSERVACION_REQUERIDA", "Debe indicar qué información debe subsanarse");
        }
        aplicarRevision(justificacion, request, EstadoJustificacion.OBSERVADA);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "OBSERVAR_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse aprobar(Long id, RevisarJustificacionRequest request) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        exigirEstado(justificacion, EstadoJustificacion.EN_REVISION, "Solo se aprueba una justificación en revisión");
        aplicarRevision(justificacion, request, EstadoJustificacion.APROBADA);

        AsistenciaAlumno asistencia = justificacion.getAsistenciaAlumno();
        if (!ESTADOS_JUSTIFICABLES.contains(asistencia.getEstadoAsistencia())) {
            throw new BusinessRuleException(
                    "ASISTENCIA_CAMBIO_DURANTE_REVISION",
                    "La asistencia cambió y ya no admite la aprobación de esta justificación");
        }
        EstadoAsistencia justificado = asistencia.getEstadoAsistencia() == EstadoAsistencia.TARDANZA
                ? EstadoAsistencia.TARDANZA_JUSTIFICADA
                : EstadoAsistencia.INASISTENCIA_JUSTIFICADA;
        asistencia.setEstadoAsistencia(justificado);
        asistenciaRepository.save(asistencia);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "APROBAR_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse rechazar(Long id, RevisarJustificacionRequest request) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        exigirEstado(justificacion, EstadoJustificacion.EN_REVISION, "Solo se rechaza una justificación en revisión");
        if (request.observacion() == null || request.observacion().isBlank()) {
            throw new BusinessRuleException(
                    "JUSTIFICACION_MOTIVO_REQUERIDO", "El rechazo debe incluir un motivo");
        }
        aplicarRevision(justificacion, request, EstadoJustificacion.RECHAZADA);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "RECHAZAR_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse subsanar(Long id, SubsanarJustificacionRequest request) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        accessGuard.verificarSolicitanteJustificacion(justificacion.getAsistenciaAlumno().getAlumno());
        exigirVersion(justificacion.getVersion(), request.version());
        exigirEstado(justificacion, EstadoJustificacion.OBSERVADA, "Solo se subsana una justificación observada");
        justificacion.setMotivo(request.motivo().trim());
        justificacion.setArchivos(archivoService.obtenerActivos(request.archivoIds()));
        justificacion.setFechaHoraEnvio(LocalDateTime.now());
        justificacion.setFechaHoraRevision(null);
        justificacion.setDocenteRevisor(null);
        justificacion.setObservacionRevision(null);
        justificacion.setEstado(EstadoJustificacion.ENVIADA);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "SUBSANAR_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse cancelar(Long id) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        accessGuard.verificarSolicitanteJustificacion(justificacion.getAsistenciaAlumno().getAlumno());
        if (justificacion.getEstado() != EstadoJustificacion.ENVIADA
                && justificacion.getEstado() != EstadoJustificacion.OBSERVADA) {
            throw new BusinessRuleException(
                    "JUSTIFICACION_NO_CANCELABLE", "Solo se cancela una justificación enviada u observada");
        }
        justificacion.setEstado(EstadoJustificacion.CANCELADA);
        JustificacionAsistencia savedJustificacion = justificacionRepository.save(justificacion);
        auditoria.registrar(
                "CANCELAR_JUSTIFICACION",
                ModuloAuditoria.ASISTENCIA,
                "JustificacionAsistencia",
                savedJustificacion.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
        return mapResponse(savedJustificacion);
    }

    public JustificacionAsistenciaResponse obtener(Long id) {
        return mapResponse(obtenerEntidad(id));
    }

    public Page<JustificacionAsistenciaResponse> listar(
            Collection<EstadoJustificacion> estados,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        Collection<EstadoJustificacion> filtroEstados = estados == null || estados.isEmpty()
                ? null
                : estados;
        return justificacionRepository.buscarVisibles(
                        filtroEstados,
                        personaDocenteId,
                        personaAlumnoId,
                        personaApoderadoId,
                        pageable)
                .map(this::mapResponse);
    }

    private JustificacionAsistencia obtenerEntidad(Long id) {
        return justificacionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "JUSTIFICACION_NO_ENCONTRADA", "No existe una justificación activa con id " + id));
    }

    private void aplicarRevision(
            JustificacionAsistencia justificacion,
            RevisarJustificacionRequest request,
            EstadoJustificacion nuevoEstado) {
        Docente revisor = buscarActivo(
                Docente.class, request.docenteRevisorId(), "DOCENTE_NO_ENCONTRADO", "docente revisor");
        accessGuard.verificarDocentePropietario(revisor);
        accessGuard.verificarAsignacionDocente(justificacion.getAsistenciaAlumno().getAsignacionCurso());
        justificacion.setDocenteRevisor(revisor);
        justificacion.setFechaHoraRevision(LocalDateTime.now());
        justificacion.setObservacionRevision(normalizar(request.observacion()));
        justificacion.setEstado(nuevoEstado);
    }

    private JustificacionAsistenciaResponse mapResponse(JustificacionAsistencia justificacion) {
        return new JustificacionAsistenciaResponse(
                justificacion.getId(),
                justificacion.getAsistenciaAlumno().getId(),
                justificacion.getMotivo(),
                justificacion.getFechaHoraEnvio(),
                justificacion.getFechaHoraRevision(),
                justificacion.getDocenteRevisor() == null ? null : justificacion.getDocenteRevisor().getId(),
                justificacion.getObservacionRevision(),
                justificacion.getEstado(),
                justificacion.getArchivos().stream().map(archivoService::mapResponse).toList(),
                justificacion.getVersion());
    }

    private void exigirEstado(
            JustificacionAsistencia justificacion, EstadoJustificacion esperado, String mensaje) {
        if (justificacion.getEstado() != esperado) {
            throw new BusinessRuleException("JUSTIFICACION_TRANSICION_INVALIDA", mensaje);
        }
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La justificación fue modificada; vuelva a cargarla");
        }
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private <T extends AuditableEntity> T buscarActivo(
            Class<T> tipo, Long id, String codigo, String nombreRecurso) {
        T entidad = entityManager.find(tipo, id);
        if (entidad == null || !entidad.isActivo()) {
            throw new ResourceNotFoundException(codigo, "No existe un " + nombreRecurso + " activo con id " + id);
        }
        return entidad;
    }
}
