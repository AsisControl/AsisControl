package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.auditoria.AuditoriaDtos;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.RegistroError;
import com.asiscontrol.entity.enums.EstadoError;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.SeveridadError;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.asiscontrol.repository.RegistroErrorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditoriaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaService.class);

    private final RegistroAuditoriaRepository auditoriaRepository;
    private final RegistroErrorRepository errorRepository;
    private final AccesoUsuarioRepository usuarioRepository;
    private final com.asiscontrol.repository.SeguimientoErrorRepository seguimientos;

    public AuditoriaService(
            RegistroAuditoriaRepository auditoriaRepository,
            RegistroErrorRepository errorRepository,
            AccesoUsuarioRepository usuarioRepository,
            com.asiscontrol.repository.SeguimientoErrorRepository seguimientos) {
        this.auditoriaRepository = auditoriaRepository;
        this.errorRepository = errorRepository;
        this.usuarioRepository = usuarioRepository;
        this.seguimientos = seguimientos;
    }

    @Transactional
    public void registrar(
            String accion,
            String modulo,
            String entidad,
            String registroId,
            ResultadoAuditoria resultado,
            String motivo,
            String valorAnterior,
            String nuevoValor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String identifier =
                authentication != null && authentication.isAuthenticated()
                        ? authentication.getName()
                        : "SISTEMA";
        RegistroAuditoria audit =
                nuevoRegistro(accion, modulo, entidad, registroId, resultado, motivo,
                        valorAnterior, nuevoValor);
        audit.setActorIdentificador(identifier);
        usuarioRepository
                .findActiveByIdentifier(identifier)
                .map(AccesoUsuario::getRol)
                .ifPresent(audit::setRolActor);
        auditoriaRepository.save(audit);
    }

    /**
     * Igual que registrar, pero a nombre de un usuario concreto. Sirve cuando la peticion no esta
     * autenticada, como al renovar una sesion, donde el actor es el dueno de la sesion.
     */
    @Transactional
    public void registrarComo(
            AccesoUsuario actor,
            String accion,
            String modulo,
            String entidad,
            String registroId,
            ResultadoAuditoria resultado,
            String motivo,
            String valorAnterior,
            String nuevoValor) {
        RegistroAuditoria audit =
                nuevoRegistro(accion, modulo, entidad, registroId, resultado, motivo,
                        valorAnterior, nuevoValor);
        audit.setActorIdentificador(actor.getUsername());
        audit.setRolActor(actor.getRol());
        auditoriaRepository.save(audit);
    }

    private RegistroAuditoria nuevoRegistro(
            String accion,
            String modulo,
            String entidad,
            String registroId,
            ResultadoAuditoria resultado,
            String motivo,
            String valorAnterior,
            String nuevoValor) {
        RegistroAuditoria audit = new RegistroAuditoria();
        // Se recortan a las longitudes de RegistroAuditoria para que un texto largo del usuario
        // no haga fallar la operacion auditada.
        audit.setAccion(recortar(accion, 80));
        audit.setModulo(recortar(modulo, 80));
        audit.setEntidad(recortar(entidad, 80));
        audit.setRegistroId(recortar(registroId, 80));
        audit.setFechaHora(Instant.now());
        audit.setResultado(resultado);
        audit.setMotivo(recortar(motivo, 500));
        audit.setValorAnterior(valorAnterior);
        audit.setNuevoValor(nuevoValor);
        audit.setCorrelacionId(correlationId());
        return audit;
    }

    /**
     * Registra una operacion rechazada o fallida en una transaccion propia, para que el
     * registro sobreviva al rollback de la operacion que la origino.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarRechazo(
            String accion,
            String modulo,
            String entidad,
            String registroId,
            ResultadoAuditoria resultado,
            String motivo) {
        registrar(accion, modulo, entidad, registroId, resultado, motivo, null, null);
    }

    @Transactional
    public void registrarAcceso(String accion, AccesoUsuario actor, ResultadoAuditoria resultado) {
        RegistroAuditoria audit = new RegistroAuditoria();
        audit.setActorIdentificador(actor.getUsername());
        audit.setRolActor(actor.getRol());
        audit.setAccion(accion);
        audit.setModulo(ModuloAuditoria.SEGURIDAD);
        audit.setEntidad("AccesoUsuario");
        audit.setRegistroId(actor.getId().toString());
        audit.setFechaHora(Instant.now());
        audit.setResultado(resultado);
        audit.setCorrelacionId(correlationId());
        auditoriaRepository.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarError(
            SeveridadError severidad,
            String componente,
            String mensajeSeguro,
            String detalleTecnicoSeguro) {
        RegistroError error = new RegistroError();
        error.setFechaHora(Instant.now());
        error.setSeveridad(severidad);
        error.setComponente(componente);
        error.setMensajeSeguro(mensajeSeguro);
        error.setDetalleTecnicoSeguro(detalleTecnicoSeguro);
        error.setCorrelacionId(correlationId());
        errorRepository.save(error);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditoriaDtos.AuditResponse> listarAuditorias(int page, int size) {
        PageRequest pageable =
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "fechaHora"));
        return PageResponse.from(auditoriaRepository.findAll(pageable), this::toAuditResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditoriaDtos.ErrorResponse> listarErrores(
            EstadoError estado, int page, int size) {
        PageRequest pageable =
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "fechaHora"));
        return estado == null
                ? PageResponse.from(errorRepository.findAll(pageable), this::toErrorResponse)
                : PageResponse.from(
                        errorRepository.findAllByEstadoOrderByFechaHoraDesc(estado, pageable),
                        this::toErrorResponse);
    }

    @Transactional
    public AuditoriaDtos.ErrorResponse actualizarError(
            Long id, AuditoriaDtos.UpdateErrorRequest request) {
        RegistroError error =
                errorRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "ERROR_NO_ENCONTRADO",
                                                "No se encontro el registro de error"));
        if (request.version() == null || error.getVersion() != request.version())
            throw new com.asiscontrol.exception.ConflictException(
                    "VERSION_DESACTUALIZADA", "El error fue actualizado; vuelva a cargarlo");
        if (request.observacionSeguimiento() == null || request.observacionSeguimiento().isBlank())
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "OBSERVACION_REQUERIDA", "Indique la observación de seguimiento");
        com.asiscontrol.entity.SeguimientoError seguimiento =
                new com.asiscontrol.entity.SeguimientoError();
        seguimiento.setError(error);
        seguimiento.setFecha(Instant.now());
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        seguimiento.setActor(actor == null ? "SISTEMA" : actor.getName());
        seguimiento.setAnterior(error.getEstado());
        seguimiento.setNuevo(request.estado());
        seguimiento.setObservacion(request.observacionSeguimiento().trim());
        seguimientos.save(seguimiento);
        error.setEstado(request.estado());
        error.setObservacionSeguimiento(request.observacionSeguimiento());
        return toErrorResponse(errorRepository.saveAndFlush(error));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditoriaDtos.ErrorResponse> filtrarErrores(
            EstadoError estado,
            SeveridadError severidad,
            String componente,
            String correlacion,
            java.time.LocalDate desde,
            java.time.LocalDate hasta,
            int page,
            int size) {
        if (desde != null && hasta != null && hasta.isBefore(desde))
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "FECHAS_INVALIDAS", "La fecha final debe ser igual o posterior a la inicial");
        org.springframework.data.jpa.domain.Specification<RegistroError> spec =
                (root, query, cb) -> {
                    java.util.List<jakarta.persistence.criteria.Predicate> filters =
                            new java.util.ArrayList<>();
                    if (estado != null) filters.add(cb.equal(root.get("estado"), estado));
                    if (severidad != null) filters.add(cb.equal(root.get("severidad"), severidad));
                    if (componente != null && !componente.isBlank())
                        filters.add(
                                cb.like(
                                        cb.lower(root.get("componente")),
                                        "%" + componente.toLowerCase(java.util.Locale.ROOT) + "%"));
                    if (correlacion != null && !correlacion.isBlank())
                        filters.add(cb.equal(root.get("correlacionId"), correlacion.trim()));
                    java.time.ZoneId zona = java.time.ZoneId.of("America/Lima");
                    if (desde != null)
                        filters.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("fechaHora"),
                                        desde.atStartOfDay(zona).toInstant()));
                    if (hasta != null)
                        filters.add(
                                cb.lessThan(
                                        root.get("fechaHora"),
                                        hasta.plusDays(1).atStartOfDay(zona).toInstant()));
                    return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        return PageResponse.from(
                errorRepository.findAll(
                        spec,
                        PageRequest.of(
                                Math.max(0, page),
                                Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Direction.DESC, "fechaHora"))),
                this::toErrorResponse);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditoriaDtos.SeguimientoResponse> seguimientos(Long id) {
        if (!errorRepository.existsById(id))
            throw new ResourceNotFoundException("ERROR_NO_ENCONTRADO", "No se encontró el error");
        return seguimientos.findByErrorIdOrderByFechaAsc(id).stream()
                .map(
                        s ->
                                new AuditoriaDtos.SeguimientoResponse(
                                        s.getId(),
                                        s.getActor(),
                                        s.getFecha(),
                                        s.getAnterior(),
                                        s.getNuevo(),
                                        s.getObservacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditoriaDtos.HistorialResponse> historial(String entidad, Long id) {
        return auditoriaRepository
                .findByEntidadAndRegistroIdOrderByFechaHoraAsc(entidad, id.toString())
                .stream()
                .map(
                        a ->
                                new AuditoriaDtos.HistorialResponse(
                                        a.getId(),
                                        a.getActorIdentificador(),
                                        a.getRolActor() == null
                                                ? null
                                                : a.getRolActor().getNombre(),
                                        a.getFechaHora(),
                                        a.getAccion(),
                                        a.getMotivo(),
                                        a.getValorAnterior(),
                                        a.getNuevoValor()))
                .toList();
    }

    private AuditoriaDtos.AuditResponse toAuditResponse(RegistroAuditoria audit) {
        return new AuditoriaDtos.AuditResponse(
                audit.getId(),
                audit.getActorIdentificador(),
                audit.getRolActor() == null ? null : audit.getRolActor().getNombre(),
                audit.getAccion(),
                audit.getModulo(),
                audit.getEntidad(),
                audit.getRegistroId(),
                audit.getFechaHora(),
                audit.getResultado(),
                audit.getMotivo(),
                audit.getCorrelacionId());
    }

    private AuditoriaDtos.ErrorResponse toErrorResponse(RegistroError error) {
        return new AuditoriaDtos.ErrorResponse(
                error.getId(),
                error.getFechaHora(),
                error.getSeveridad(),
                error.getComponente(),
                error.getMensajeSeguro(),
                error.getCorrelacionId(),
                error.getEstado(),
                error.getObservacionSeguimiento(),
                error.getVersion());
    }

    static String recortar(String valor, int maximo) {
        return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? "sin-correlacion" : value;
    }
}
