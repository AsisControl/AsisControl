package com.asiscontrol.service;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.SesionAcceso;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.security.AuthenticatedUser;
import com.asiscontrol.security.JwtService;
import com.asiscontrol.util.HashUtils;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.asiscontrol.repository.TokenRecuperacionRepository recuperaciones;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AuthenticationManager authenticationManager;
    private final AccesoUsuarioRepository usuarioRepository;
    private final SesionAccesoRepository sesionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public AuthService(
            AuthenticationManager authenticationManager,
            AccesoUsuarioRepository usuarioRepository,
            SesionAccesoRepository sesionRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.sesionRepository = sesionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String identifier = request.identifier().trim();
        AccesoUsuario locked = usuarioRepository.bloquearPorIdentificador(identifier).orElse(null);
        boolean bloqueado =
                locked != null
                        && locked.getBloqueadoHasta() != null
                        && locked.getBloqueadoHasta().isAfter(Instant.now());
        try {
            if (bloqueado
                    || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            > 72) throw new BadCredentialsException("Credenciales invalidas");
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    identifier, request.password()));
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            AccesoUsuario usuario = principal.getUsuario();
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuario.setEstado(EstadoAcceso.ACTIVO);
            usuario.setUltimoAcceso(Instant.now());
            usuarioRepository.save(usuario);

            JwtService.GeneratedToken token = jwtService.generate(principal);
            SesionAcceso sesion = new SesionAcceso();
            sesion.setUsuario(usuario);
            sesion.setTokenHash(HashUtils.sha256(token.value()));
            sesion.setFechaCreacion(token.issuedAt());
            sesion.setUltimaActividad(token.issuedAt());
            // Límite absoluto de la sesión, independiente de los JWT cortos que se renuevan.
            sesion.setFechaExpiracion(token.issuedAt().plus(Duration.ofHours(8)));
            sesion.setEstado(EstadoSesion.VIGENTE);
            sesionRepository.save(sesion);

            auditoriaService.registrarAcceso("INICIAR_SESION", usuario, ResultadoAuditoria.EXITOSO);
            return new AuthDtos.AuthResponse(
                    "Bearer", token.value(), token.expiresAt(), toUserSummary(usuario));
        } catch (AuthenticationException exception) {
            if (!bloqueado) registerFailedAttempt(identifier);
            auditoriaService.registrar(
                    "INICIAR_SESION",
                    "SEGURIDAD",
                    "AccesoUsuario",
                    locked == null ? "NO_IDENTIFICADO" : locked.getId().toString(),
                    ResultadoAuditoria.RECHAZADO,
                    "Credenciales invalidas",
                    null,
                    null);
            throw new BadCredentialsException("Credenciales invalidas");
        }
    }

    @Transactional
    public void logout(String tokenValue) {
        sesionRepository
                .findByTokenHashAndEstado(HashUtils.sha256(tokenValue), EstadoSesion.VIGENTE)
                .ifPresent(
                        session -> {
                            session.setEstado(EstadoSesion.REVOCADA);
                            session.setFechaRevocacion(Instant.now());
                            sesionRepository.save(session);
                            auditoriaService.registrar(
                                    "CERRAR_SESION",
                                    "SEGURIDAD",
                                    "SesionAcceso",
                                    session.getId().toString(),
                                    ResultadoAuditoria.EXITOSO,
                                    null,
                                    null,
                                    null);
                        });
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserSummary getCurrentUser(Jwt jwt) {
        AccesoUsuario usuario =
                usuarioRepository
                        .findActiveByIdentifier(jwt.getSubject())
                        .orElseThrow(
                                () ->
                                        new BadCredentialsException(
                                                "La cuenta ya no esta disponible"));
        return toUserSummary(usuario);
    }

    @Transactional
    public void changePassword(Jwt jwt, AuthDtos.ChangePasswordRequest request) {
        AccesoUsuario usuario =
                usuarioRepository
                        .bloquearPorIdentificador(jwt.getSubject())
                        .orElseThrow(
                                () ->
                                        new BadCredentialsException(
                                                "La cuenta ya no esta disponible"));
        com.asiscontrol.security.PasswordPolicy.validate(
                request.newPassword(), usuario.getUsername());
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessRuleException(
                    "CONFIRMACION_INVALIDA", "La nueva contrasena y su confirmacion no coinciden");
        }
        if (request.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(
                        request.currentPassword(), usuario.getContrasenaHash())) {
            throw new BusinessRuleException(
                    "CONTRASENA_ACTUAL_INCORRECTA", "La contrasena actual no es correcta");
        }
        if (passwordEncoder.matches(request.newPassword(), usuario.getContrasenaHash())) {
            throw new BusinessRuleException(
                    "CONTRASENA_REPETIDA", "La nueva contrasena debe ser diferente a la actual");
        }
        usuario.setContrasenaTemporalExpira(null);
        recuperaciones.revocar(usuario.getId(), Instant.now());
        usuario.setContrasenaHash(passwordEncoder.encode(request.newPassword()));
        usuario.setRequiereCambioContrasena(false);
        usuarioRepository.save(usuario);
        sesionRepository.revokeAllByUsuarioId(
                usuario.getId(), EstadoSesion.REVOCADA, Instant.now());
        auditoriaService.registrar(
                "CAMBIAR_CONTRASENA",
                "SEGURIDAD",
                "AccesoUsuario",
                usuario.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
    }

    private void registerFailedAttempt(String identifier) {
        usuarioRepository
                .findActiveByIdentifier(identifier)
                .filter(usuario -> usuario.getEstado() == EstadoAcceso.ACTIVO)
                .ifPresent(
                        usuario -> {
                            int attempts = usuario.getIntentosFallidos() + 1;
                            usuario.setIntentosFallidos(attempts);
                            if (attempts >= MAX_FAILED_ATTEMPTS) {
                                usuario.setEstado(EstadoAcceso.BLOQUEADO_TEMPORALMENTE);
                                usuario.setBloqueadoHasta(Instant.now().plus(LOCK_DURATION));
                            }
                            usuarioRepository.save(usuario);
                        });
    }

    AuthDtos.UserSummary toUserSummary(AccesoUsuario usuario) {
        List<String> permissions =
                usuario.getRol().getPermisos().stream()
                        .filter(permission -> permission.isActivo())
                        .map(permission -> permission.getCodigo())
                        .sorted()
                        .toList();
        return new AuthDtos.UserSummary(
                usuario.getId(),
                usuario.getPersona().getId(),
                usuario.getUsername(),
                usuario.getCorreo(),
                usuario.getPersona().nombreCompleto(),
                usuario.getRol().getNombre(),
                permissions,
                usuario.isRequiereCambioContrasena());
    }
}
