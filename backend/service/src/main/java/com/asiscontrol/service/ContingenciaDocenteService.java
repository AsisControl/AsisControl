package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RegistrarContingenciaRequest;
import com.asiscontrol.dto.asistencia.ContingenciaDocenteDto;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.asistencia.SolicitudContingenciaDocente;
import com.asiscontrol.entity.asistencia.SolicitudContingenciaDocente.Estado;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.asistencia.SolicitudContingenciaDocenteRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class ContingenciaDocenteService {
    private final SolicitudContingenciaDocenteRepository solicitudes;
    private final AsistenciaDocenteService asistencia;
    private final AccesoUsuarioRepository cuentas;
    private final AuditoriaService auditoria;
    private final EntityManager entityManager;
    private final Clock reloj;

    public ContingenciaDocenteService(SolicitudContingenciaDocenteRepository solicitudes,
            AsistenciaDocenteService asistencia, AccesoUsuarioRepository cuentas, AuditoriaService auditoria,
            EntityManager entityManager, Clock reloj) {
        this.solicitudes = solicitudes;
        this.asistencia = asistencia;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
        this.entityManager = entityManager;
        this.reloj = reloj;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContingenciaDocenteDto.Response solicitar(ContingenciaDocenteDto.Solicitar datos) {
        if (datos.fechaJornada().atTime(datos.horaReportada()).isAfter(LocalDateTime.now(reloj))) {
            throw new BusinessRuleException("CONTINGENCIA_FECHA_HORA_INVALIDA", "La fecha y hora reportadas no pueden ser futuras.");
        }
        var docente = asistencia.docenteDeLaSesion();
        asistencia.bloquearMarcacionDisponible(docente, datos.fechaJornada());
        if (solicitudes.existsByDocenteIdAndFechaJornadaAndEstado(docente.getId(), datos.fechaJornada(), Estado.PENDIENTE)) {
            throw new ConflictException("CONTINGENCIA_PENDIENTE", "Ya tienes una solicitud pendiente para esa jornada.");
        }
        var solicitud = new SolicitudContingenciaDocente();
        solicitud.setDocente(docente);
        solicitud.setFechaJornada(datos.fechaJornada());
        solicitud.setHoraReportada(datos.horaReportada());
        solicitud.setMotivo(datos.motivo().trim());
        solicitudes.saveAndFlush(solicitud);
        auditar("SOLICITAR_CONTINGENCIA_DOCENTE", solicitud, null);
        return respuesta(solicitud);
    }

    public PageResponse<ContingenciaDocenteDto.Response> listar(Estado estado, Pageable pagina, boolean administracion) {
        Long docenteId = administracion ? null : asistencia.docenteDeLaSesion().getId();
        return PageResponse.from(solicitudes.listar(docenteId, estado, pagina), this::respuesta);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContingenciaDocenteDto.Response resolver(
            Long id, ContingenciaDocenteDto.Resolucion resolucion, String identificador) {
        SolicitudContingenciaDocente solicitud = pendiente(id);
        AccesoUsuario actor = responsable(identificador);
        switch (resolucion.decision()) {
            case APROBAR -> aprobar(solicitud, resolucion.estadoSinVentana(), actor);
            case RECHAZAR -> rechazar(solicitud, resolucion.motivo(), actor);
        }
        return respuesta(solicitud);
    }

    private void aprobar(SolicitudContingenciaDocente solicitud, EstadoAsistencia sinVentana, AccesoUsuario actor) {
        var docente = solicitud.getDocente();
        var jornada = solicitud.getFechaJornada();
        var hora = jornada.atTime(solicitud.getHoraReportada());
        asistencia.bloquearMarcacionDisponible(docente, jornada);
        var estado = estadoSugerido(solicitud);
        if (estado == null) {
            if (sinVentana != EstadoAsistencia.PRESENTE && sinVentana != EstadoAsistencia.TARDANZA) {
                throw new BusinessRuleException("CONTINGENCIA_ESTADO_REQUERIDO",
                        "Selecciona Presente o Tardanza para esta jornada sin ventana de puntualidad.");
            }
            estado = sinVentana;
        }
        var marcacion = asistencia.registrarContingencia(
                new RegistrarContingenciaRequest(docente.getId(), jornada, hora, estado, solicitud.getMotivo()));
        solicitud.resolver(actor, Instant.now(reloj),
                entityManager.getReference(AsistenciaDocente.class, marcacion.id()), null);
        auditar("APROBAR_CONTINGENCIA_DOCENTE", solicitud, null);
    }

    private void rechazar(SolicitudContingenciaDocente solicitud, String motivo, AccesoUsuario actor) {
        String motivoRechazo = motivo.strip();
        solicitud.resolver(actor, Instant.now(reloj), null, motivoRechazo);
        auditar("RECHAZAR_CONTINGENCIA_DOCENTE", solicitud, motivoRechazo);
    }

    private AccesoUsuario responsable(String identificador) {
        return cuentas.findActiveByIdentifier(identificador)
                .orElseThrow(() -> new AccessDeniedException("La cuenta de administración ya no está activa."));
    }

    private SolicitudContingenciaDocente pendiente(Long id) {
        var solicitud = solicitudes.bloquear(id).orElseThrow(() -> new ResourceNotFoundException(
                "CONTINGENCIA_NO_ENCONTRADA", "No se encontró la solicitud de contingencia."));
        if (solicitud.getEstado() != Estado.PENDIENTE) {
            throw new ConflictException("CONTINGENCIA_RESUELTA", "La solicitud ya fue resuelta.");
        }
        return solicitud;
    }

    private EstadoAsistencia estadoSugerido(SolicitudContingenciaDocente solicitud) {
        return solicitud.getEstado() == Estado.PENDIENTE ? asistencia.sugerirEstadoContingencia(
                solicitud.getDocente().getId(), solicitud.getFechaJornada().atTime(solicitud.getHoraReportada())) : null;
    }

    private ContingenciaDocenteDto.Response respuesta(SolicitudContingenciaDocente solicitud) {
        return new ContingenciaDocenteDto.Response(solicitud.getId(), solicitud.getDocente().getId(),
                solicitud.getDocente().getPersona().nombreCompleto(), solicitud.getFechaJornada(),
                solicitud.getHoraReportada(), solicitud.getMotivo(), solicitud.getEstado(), solicitud.getFechaCreacion(),
                solicitud.getFechaResolucion(), solicitud.getMotivoRechazo(), estadoSugerido(solicitud),
                solicitud.getMarcacion() == null ? null : solicitud.getMarcacion().getEstado());
    }

    private void auditar(String accion, SolicitudContingenciaDocente solicitud, String motivo) {
        auditoria.registrar(accion, ModuloAuditoria.ASISTENCIA, "SolicitudContingenciaDocente",
                solicitud.getId().toString(), ResultadoAuditoria.EXITOSO, motivo, null,
                "docente=" + solicitud.getDocente().getId() + ",jornada=" + solicitud.getFechaJornada()
                        + ",estado=" + solicitud.getEstado());
    }
}
