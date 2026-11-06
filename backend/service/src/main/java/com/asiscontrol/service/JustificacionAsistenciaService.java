package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.CrearJustificacionRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.JustificacionAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RevisarJustificacionRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.SubsanarJustificacionRequest;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import com.asiscontrol.entity.enums.TipoNotificacion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.JustificacionAsistenciaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.asiscontrol.service.AuditoriaService;
import com.asiscontrol.entity.enums.ResultadoAuditoria;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class JustificacionAsistenciaService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Set<EstadoAsistencia> ESTADOS_JUSTIFICABLES = Set.of(
            EstadoAsistencia.TARDANZA,
            EstadoAsistencia.INASISTENCIA,
            EstadoAsistencia.SIN_MARCACION);

    // asistencia alumnos: el apoderado solo solicita sobre inasistencias
    private static final Set<EstadoAsistencia> ESTADOS_SOLICITABLES = Set.of(
            EstadoAsistencia.INASISTENCIA,
            EstadoAsistencia.SIN_MARCACION);

    private final JustificacionAsistenciaRepository justificacionRepository;
    private final AsistenciaAlumnoRepository asistenciaRepository;
    private final AsistenciaAlumnoService asistenciaService;
    private final ArchivoStorageService archivoService;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;
    private final AlumnoApoderadoRepository vinculoRepository;
    private final AccesoUsuarioRepository accesoRepository;

    public JustificacionAsistenciaService(
            JustificacionAsistenciaRepository justificacionRepository,
            AsistenciaAlumnoRepository asistenciaRepository,
            AsistenciaAlumnoService asistenciaService,
            ArchivoStorageService archivoService,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria,
            NotificacionService notificaciones,
            AlumnoApoderadoRepository vinculoRepository,
            AccesoUsuarioRepository accesoRepository) {
        this.justificacionRepository = justificacionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.asistenciaService = asistenciaService;
        this.archivoService = archivoService;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.vinculoRepository = vinculoRepository;
        this.accesoRepository = accesoRepository;
    }

    @Transactional
    public JustificacionAsistenciaResponse crear(CrearJustificacionRequest request) {
        AsistenciaAlumno asistencia = asistenciaService.obtenerEntidad(request.asistenciaAlumnoId());
        accessGuard.verificarSolicitanteJustificacion(asistencia.getAlumno());
        if (!ESTADOS_SOLICITABLES.contains(asistencia.getEstadoAsistencia())) {
            throw new BusinessRuleException(
                    "ASISTENCIA_NO_JUSTIFICABLE", "El estado actual de asistencia no admite justificación");
        }
        // asistencia alumnos: solo cabe una fila por asistencia; una cancelada se reutiliza
        JustificacionAsistencia justificacion = justificacionRepository
                .findByAsistenciaAlumnoIdAndActivoTrue(asistencia.getId())
                .orElseGet(JustificacionAsistencia::new);
        if (justificacion.getId() != null && justificacion.getEstado() != EstadoJustificacion.CANCELADA) {
            throw new ConflictException(
                    "JUSTIFICACION_DUPLICADA", "La asistencia ya posee una justificación activa");
        }

        justificacion.setAsistenciaAlumno(asistencia);
        justificacion.setDocenteRevisor(null);
        justificacion.setFechaHoraRevision(null);
        justificacion.setObservacionRevision(null);
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
        exigirPorResolver(justificacion, "Solo se observa una justificación en revisión");
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
        exigirPorResolver(justificacion, "Solo se aprueba una justificación en revisión");
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
        avisarApoderados(savedJustificacion, "Justificación aprobada", "fue aprobada.");
        return mapResponse(savedJustificacion);
    }

    @Transactional
    public JustificacionAsistenciaResponse rechazar(Long id, RevisarJustificacionRequest request) {
        JustificacionAsistencia justificacion = obtenerEntidad(id);
        exigirPorResolver(justificacion, "Solo se rechaza una justificación en revisión");
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
        avisarApoderados(savedJustificacion, "Justificación rechazada",
                "fue rechazada. Motivo: " + savedJustificacion.getObservacionRevision());
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
        exigirVersion(justificacion.getVersion(), request.version());
        AsignacionCurso asignacion = justificacion.getAsistenciaAlumno().getAsignacionCurso();
        accessGuard.verificarAsignacionDocente(asignacion);
        justificacion.setDocenteRevisor(asignacion.getDocente());
        justificacion.setFechaHoraRevision(LocalDateTime.now());
        justificacion.setObservacionRevision(normalizar(request.observacion()));
        justificacion.setEstado(nuevoEstado);
    }

    /** Avisa a cada apoderado vinculado que tenga acceso; quien no tenga cuenta lo ve en la pantalla. */
    private void avisarApoderados(JustificacionAsistencia justificacion, String titulo, String resultado) {
        AsistenciaAlumno asistencia = justificacion.getAsistenciaAlumno();
        String mensaje = "La justificación de " + asistencia.getAlumno().getPersona().nombreCompleto()
                + " para " + asistencia.getAsignacionCurso().getCurso().getNombre()
                + " del " + asistencia.getFechaRegistro().format(FECHA) + " " + resultado;
        vinculoRepository.findAllByAlumnoIdAndActivoTrue(asistencia.getAlumno().getId()).stream()
                .flatMap(vinculo -> accesoRepository
                        .findAllByPersonaIdAndActivoTrue(vinculo.getApoderado().getPersona().getId()).stream())
                .filter(acceso -> "APODERADO".equals(acceso.getRol().getNombre()))
                .forEach(acceso -> notificaciones.create(
                        acceso.getId(), titulo, mensaje, TipoNotificacion.ASISTENCIA, "/asistencia"));
    }

    private JustificacionAsistenciaResponse mapResponse(JustificacionAsistencia justificacion) {
        AsistenciaAlumno asistencia = justificacion.getAsistenciaAlumno();
        AsignacionCurso asignacion = asistencia.getAsignacionCurso();
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
                justificacion.getVersion(),
                asistencia.getAlumno().getPersona().nombreLista(),
                asignacion.getCurso().getNombre(),
                asignacion.getSeccion().getGrado() + "° Grado " + asignacion.getSeccion().getNombre(),
                asistencia.getFechaRegistro(),
                asistencia.getSesionClase() == null ? null : asistencia.getSesionClase().getHoraInicio(),
                asistencia.getEstadoAsistencia());
    }

    private void exigirEstado(
            JustificacionAsistencia justificacion, EstadoJustificacion esperado, String mensaje) {
        if (justificacion.getEstado() != esperado) {
            throw new BusinessRuleException("JUSTIFICACION_TRANSICION_INVALIDA", mensaje);
        }
    }

    /** Enviada o en revisión: el docente resuelve directo, sin marcar antes el inicio de la revisión. */
    private void exigirPorResolver(JustificacionAsistencia justificacion, String mensaje) {
        if (justificacion.getEstado() != EstadoJustificacion.ENVIADA
                && justificacion.getEstado() != EstadoJustificacion.EN_REVISION) {
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
}
