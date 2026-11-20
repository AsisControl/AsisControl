package com.asiscontrol.service;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.entity.SesionAcceso;
import com.asiscontrol.entity.TokenRenovacion;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.repository.TokenRenovacionRepository;
import com.asiscontrol.security.AuthenticatedUser;
import com.asiscontrol.util.HashUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class RenovacionSesionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthService auth;
    private final SesionAccesoRepository sesiones;
    private final TokenRenovacionRepository tokens;
    private final AuditoriaService auditoria;

    public RenovacionSesionService(AuthService auth, SesionAccesoRepository sesiones,
            TokenRenovacionRepository tokens, AuditoriaService auditoria) {
        this.auth = auth;
        this.sesiones = sesiones;
        this.tokens = tokens;
        this.auditoria = auditoria;
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public Credenciales login(AuthDtos.LoginRequest request) {
        AuthDtos.AuthResponse response = auth.login(request);
        SesionAcceso sesion = sesiones.findById(response.user().sesionId()).orElseThrow();
        return new Credenciales(response, emitir(sesion), sesion.getFechaExpiracion());
    }

    @Transactional(noRollbackFor = AuthenticationException.class,
            isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Credenciales renovar(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String hash = HashUtils.sha256(rawToken);
        Long sesionId = tokens.findSesionIdByHash(hash).orElseThrow(this::invalid);
        // Una única fila serializa las renovaciones de la misma familia de tokens.
        SesionAcceso sesion = sesiones.bloquear(sesionId).orElseThrow(this::invalid);
        TokenRenovacion token = tokens.findByTokenHash(hash).orElseThrow(this::invalid);
        if (token.getFechaUso() != null) {
            // Un token de un solo uso presentado de nuevo indica que pudo ser robado.
            EstadoSesion estadoPrevio = sesion.getEstado();
            revocar(sesion);
            auditarSesion("REUTILIZACION_TOKEN_RENOVACION", sesion,
                    "Se presento un token de renovacion ya usado; la sesion se revoca",
                    "estado=" + estadoPrevio + ",tokenUsadoEn=" + token.getFechaUso());
            throw invalid();
        }
        Instant now = Instant.now();
        var usuario = sesion.getUsuario();
        var principal = new AuthenticatedUser(usuario);
        String motivo = motivoRechazo(sesion, principal, now);
        if (motivo != null) {
            EstadoSesion estadoPrevio = sesion.getEstado();
            revocar(sesion);
            // Solo es una revocacion si la sesion seguia vigente; si no, es un intento sobre una
            // sesion ya cerrada.
            auditarSesion(estadoPrevio == EstadoSesion.VIGENTE
                            ? "REVOCAR_SESION" : "RENOVACION_SESION_RECHAZADA",
                    sesion, motivo, "estado=" + estadoPrevio);
            throw invalid();
        }
        var disponibles = auth.rolesDisponibles(usuario);
        var activo = sesion.getRolActivo();
        if (activo != null && disponibles.stream().noneMatch(rol -> rol.getId().equals(activo.getId()))) {
            sesion.setRolActivo(null);
            sesion.setVersionContexto(sesion.getVersionContexto() + 1);
        }
        token.setFechaUso(now);
        sesion.setUltimaActividad(now);
        var response = auth.emitirRespuesta(sesion, disponibles);
        return new Credenciales(response, emitir(sesion), sesion.getFechaExpiracion());
    }

    @Transactional
    public void cerrar(String rawToken, Jwt jwt) {
        if (rawToken != null && !rawToken.isBlank()) {
            tokens.findSesionIdByHash(HashUtils.sha256(rawToken)).ifPresent(this::cerrarPorId);
        }
        if (jwt != null && jwt.getClaims().get("sesionId") instanceof Number id) {
            cerrarPorId(id.longValue());
        }
    }

    private void cerrarPorId(Long id) {
        sesiones.bloquear(id).filter(sesion -> sesion.getEstado() == EstadoSesion.VIGENTE)
                .ifPresent(sesion -> {
                    revocar(sesion);
                    auditoria.registrarComo(sesion.getUsuario(), sesion.getRolActivo(),
                            "CERRAR_SESION", ModuloAuditoria.SEGURIDAD, "SesionAcceso",
                            sesion.getId().toString(), ResultadoAuditoria.EXITOSO,
                            null, "estado=VIGENTE", "estado=" + sesion.getEstado());
                });
    }

    /** Devuelve por que la sesion no se puede renovar, o null si puede. */
    private String motivoRechazo(SesionAcceso sesion, AuthenticatedUser principal, Instant now) {
        var usuario = sesion.getUsuario();
        if (sesion.getEstado() != EstadoSesion.VIGENTE) return "La sesion ya estaba " + sesion.getEstado();
        if (!sesion.getFechaExpiracion().isAfter(now)) return "La sesion alcanzo su expiracion absoluta";
        if (!sesion.getUltimaActividad().isAfter(now.minus(Duration.ofMinutes(30))))
            return "Inactividad mayor a 30 minutos";
        if (!usuario.isActivo() || usuario.getEstado() != EstadoAcceso.ACTIVO)
            return "El acceso no esta activo (" + usuario.getEstado() + ")";
        if (!usuario.getPersona().isActivo()) return "La persona del acceso esta inactiva";
        if (!principal.isCredentialsNonExpired()) return "La contrasena temporal vencio";
        return null;
    }

    private void auditarSesion(String accion, SesionAcceso sesion, String motivo, String anterior) {
        auditoria.registrarComo(sesion.getUsuario(), sesion.getRolActivo(), accion, ModuloAuditoria.SEGURIDAD,
                "SesionAcceso", sesion.getId().toString(), ResultadoAuditoria.RECHAZADO,
                motivo, anterior, "estado=" + sesion.getEstado());
    }

    private String emitir(SesionAcceso sesion) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TokenRenovacion token = new TokenRenovacion();
        token.setSesion(sesion);
        token.setTokenHash(HashUtils.sha256(raw));
        token.setFechaCreacion(Instant.now());
        tokens.save(token);
        return raw;
    }

    private void revocar(SesionAcceso sesion) {
        sesion.setEstado(EstadoSesion.REVOCADA);
        sesion.setFechaRevocacion(Instant.now());
        sesiones.save(sesion);
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("La sesión no se puede renovar; inicie sesión nuevamente");
    }

    public record Credenciales(AuthDtos.AuthResponse response, String refreshToken, Instant expiresAt) {
        @Override
        public String toString() { return "Credenciales[secretos=PROTEGIDOS]"; }
    }
}
