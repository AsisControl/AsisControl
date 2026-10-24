package com.asiscontrol.service;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.*;
import com.asiscontrol.security.PasswordPolicy;
import com.asiscontrol.util.HashUtils;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class RecuperacionService {
    public static final String RESPUESTA =
            "Si el usuario tiene un correo institucional confirmado y un acceso habilitado,"
                + " recibirá instrucciones para recuperar su contraseña. Revise también el correo"
                + " no deseado. Si no recibe el enlace, contacte a la administración.";
    private final AccesoUsuarioRepository users;
    private final TokenRecuperacionRepository tokens;
    private final SesionAccesoRepository sessions;
    private final LimiteRecuperacionService limits;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final AuditoriaService audit;

    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager em;

    private final SecureRandom random = new SecureRandom();

    public RecuperacionService(
            AccesoUsuarioRepository u,
            TokenRecuperacionRepository t,
            SesionAccesoRepository s,
            LimiteRecuperacionService l,
            PasswordEncoder e,
            ApplicationEventPublisher p,
            AuditoriaService a) {
        users = u;
        tokens = t;
        sessions = s;
        limits = l;
        encoder = e;
        events = p;
        audit = a;
    }

    @Transactional
    public void solicitar(String identifier, String ip) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (!limits.permitir(normalized, ip)) return;
        users.bloquearPorIdentificador(normalized)
                .filter(this::elegible)
                .ifPresent(
                        user -> {
                            Instant now = Instant.now();
                            tokens.revocar(user.getId(), now);
                            byte[] bytes = new byte[32];
                            random.nextBytes(bytes);
                            String raw =
                                    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                            TokenRecuperacion t = new TokenRecuperacion();
                            t.setUsuario(user);
                            t.setTokenHash(HashUtils.sha256(raw));
                            t.setCreado(now);
                            t.setExpira(now.plus(Duration.ofMinutes(15)));
                            tokens.saveAndFlush(t);
                            events.publishEvent(new CorreoRecuperacion(user.getCorreo(), raw));
                            audit.registrar(
                                    "SOLICITAR_RECUPERACION",
                                    ModuloAuditoria.SEGURIDAD,
                                    "AccesoUsuario",
                                    user.getId().toString(),
                                    ResultadoAuditoria.EXITOSO,
                                    null,
                                    null,
                                    null);
                        });
    }

    @Transactional(readOnly = true)
    public void validar(String token) {
        tokens.findByTokenHash(HashUtils.sha256(token))
                .filter(this::vigente)
                .filter(t -> elegible(t.getUsuario()))
                .orElseThrow(this::invalido);
    }

    @Transactional
    public void confirmar(AuthDtos.ResetRecoveryRequest request) {
        String hash = HashUtils.sha256(request.token());
        Long uid =
                tokens.findByTokenHash(hash)
                        .map(t -> t.getUsuario().getId())
                        .orElseThrow(this::invalido);
        AccesoUsuario user = users.bloquear(uid).filter(this::elegible).orElseThrow(this::invalido);
        TokenRecuperacion token = tokens.bloquear(hash).orElseThrow(this::invalido);
        em.refresh(token, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!vigente(token)) throw invalido();
        if (!request.newPassword().equals(request.confirmPassword()))
            throw new BusinessRuleException(
                    "CONFIRMACION_INVALIDA", "Las contraseñas no coinciden");
        PasswordPolicy.validate(request.newPassword(), user.getUsername());
        if (encoder.matches(request.newPassword(), user.getContrasenaHash()))
            throw new BusinessRuleException(
                    "CONTRASENA_REPETIDA", "La nueva contraseña debe ser diferente a la actual");
        Instant now = Instant.now();
        token.setUsado(now);
        tokens.saveAndFlush(token);
        user.setContrasenaHash(encoder.encode(request.newPassword()));
        user.setRequiereCambioContrasena(false);
        user.setContrasenaTemporalExpira(null);
        user.setIntentosFallidos(0);
        user.setBloqueadoHasta(null);
        user.setEstado(EstadoAcceso.ACTIVO);
        users.save(user);
        tokens.revocar(uid, now);
        sessions.revokeAllByUsuarioId(uid, EstadoSesion.REVOCADA, now);
        audit.registrarAcceso("RECUPERAR_CONTRASENA", user, ResultadoAuditoria.EXITOSO);
    }

    private boolean elegible(AccesoUsuario u) {
        return u.isActivo()
                && u.isCorreoVerificado()
                && (u.getEstado() == EstadoAcceso.ACTIVO
                        || u.getEstado() == EstadoAcceso.BLOQUEADO_TEMPORALMENTE);
    }

    private boolean vigente(TokenRecuperacion t) {
        return t.getUsado() == null
                && t.getRevocado() == null
                && t.getExpira().isAfter(Instant.now());
    }

    private BusinessRuleException invalido() {
        return new BusinessRuleException(
                "ENLACE_INVALIDO",
                "El enlace venció, ya fue utilizado o fue reemplazado. Solicite uno nuevo");
    }

    // Deliberadamente sin toString: nunca registrar el enlace ni el destinatario.
    public static final class CorreoRecuperacion {
        private final String correo, token;

        public CorreoRecuperacion(String c, String t) {
            correo = c;
            token = t;
        }

        public String correo() {
            return correo;
        }

        public String token() {
            return token;
        }
    }
}
