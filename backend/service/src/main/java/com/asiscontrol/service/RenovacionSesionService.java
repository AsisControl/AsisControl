package com.asiscontrol.service;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.entity.SesionAcceso;
import com.asiscontrol.entity.TokenRenovacion;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.repository.TokenRenovacionRepository;
import com.asiscontrol.security.AuthenticatedUser;
import com.asiscontrol.security.JwtService;
import com.asiscontrol.util.HashUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
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
    private final JwtService jwt;

    public RenovacionSesionService(AuthService auth, SesionAccesoRepository sesiones,
            TokenRenovacionRepository tokens, JwtService jwt) {
        this.auth = auth;
        this.sesiones = sesiones;
        this.tokens = tokens;
        this.jwt = jwt;
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public Credenciales login(AuthDtos.LoginRequest request) {
        AuthDtos.AuthResponse response = auth.login(request);
        SesionAcceso sesion = sesiones.findByTokenHashAndEstado(
                HashUtils.sha256(response.accessToken()), EstadoSesion.VIGENTE).orElseThrow();
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
            revocar(sesion);
            throw invalid();
        }
        Instant now = Instant.now();
        var usuario = sesion.getUsuario();
        var principal = new AuthenticatedUser(usuario);
        if (sesion.getEstado() != EstadoSesion.VIGENTE
                || !sesion.getFechaExpiracion().isAfter(now)
                || !sesion.getUltimaActividad().isAfter(now.minus(Duration.ofMinutes(30)))
                || !usuario.isActivo() || usuario.getEstado() != EstadoAcceso.ACTIVO
                || !usuario.getPersona().isActivo() || !usuario.getRol().isActivo()
                || !principal.isCredentialsNonExpired()) {
            revocar(sesion);
            throw invalid();
        }
        token.setFechaUso(now);
        tokens.save(token);
        var generated = jwt.generate(principal);
        sesion.setTokenHash(HashUtils.sha256(generated.value()));
        sesion.setUltimaActividad(now);
        sesiones.save(sesion);
        var response = new AuthDtos.AuthResponse("Bearer", generated.value(),
                generated.expiresAt(), auth.toUserSummary(usuario));
        return new Credenciales(response, emitir(sesion), sesion.getFechaExpiracion());
    }

    @Transactional
    public void cerrar(String rawToken, String bearer) {
        if (rawToken != null && !rawToken.isBlank()) {
            tokens.findByTokenHash(HashUtils.sha256(rawToken)).ifPresent(token ->
                    sesiones.bloquear(token.getSesion().getId()).ifPresent(this::revocar));
        }
        if (bearer != null && !bearer.isBlank()) auth.logout(bearer);
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
