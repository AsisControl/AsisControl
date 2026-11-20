package com.asiscontrol.service;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.SesionAcceso;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.TokenRecuperacionRepository;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.security.AuthenticatedUser;
import com.asiscontrol.security.JwtService;
import com.asiscontrol.util.HashUtils;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AuthenticationManager authenticationManager;
    private final AccesoUsuarioRepository usuarioRepository;
    private final SesionAccesoRepository sesionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final AccesoUsuarioRolRepository asignaciones;
    private final TokenRecuperacionRepository recuperaciones;

    public AuthService(
            AuthenticationManager authenticationManager,
            AccesoUsuarioRepository usuarioRepository,
            SesionAccesoRepository sesionRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            AccesoUsuarioRolRepository asignaciones,
            TokenRecuperacionRepository recuperaciones) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.sesionRepository = sesionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.asignaciones = asignaciones;
        this.recuperaciones = recuperaciones;
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
                    || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                throw new BadCredentialsException("Credenciales invalidas");
            }
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

            var disponibles = rolesDisponibles(usuario);
            Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            SesionAcceso sesion = new SesionAcceso();
            sesion.setUsuario(usuario);
            if (disponibles.size() == 1) sesion.setRolActivo(disponibles.getFirst());
            // La identidad de sesión debe existir antes de firmar el primer token.
            sesion.setTokenHash(HashUtils.sha256(UUID.randomUUID().toString()));
            sesion.setFechaCreacion(ahora);
            sesion.setUltimaActividad(ahora);
            sesion.setFechaExpiracion(ahora.plus(Duration.ofHours(8)));
            sesion.setEstado(EstadoSesion.VIGENTE);
            sesionRepository.save(sesion);

            auditoriaService.registrarAcceso("INICIAR_SESION", usuario,
                    sesion.getRolActivo(), ResultadoAuditoria.EXITOSO);
            return emitirRespuesta(sesion, disponibles);
        } catch (AuthenticationException exception) {
            if (!bloqueado && locked != null && locked.getEstado() == EstadoAcceso.ACTIVO) {
                registrarIntentoFallido(locked);
            }
            auditoriaService.registrar(
                    "INICIAR_SESION",
                    ModuloAuditoria.SEGURIDAD,
                    "AccesoUsuario",
                    locked == null ? "NO_IDENTIFICADO" : locked.getId().toString(),
                    ResultadoAuditoria.RECHAZADO,
                    "Credenciales invalidas",
                    null,
                    null);
            throw new BadCredentialsException("Credenciales invalidas");
        }
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserSummary getCurrentUser(Jwt jwt) {
        SesionAcceso sesion = sesionRepository.findById(reclamo(jwt, "sesionId"))
                .filter(s -> contextoVigente(s, jwt))
                .orElseThrow(this::sesionInvalida);
        return toUserSummary(sesion, rolesDisponibles(sesion.getUsuario()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthDtos.AuthResponse cambiarRol(Jwt jwt, AuthDtos.RolActivoRequest request) {
        SesionAcceso sesion = sesionRepository.bloquear(reclamo(jwt, "sesionId"))
                .filter(s -> contextoVigente(s, jwt))
                .orElseThrow(this::sesionInvalida);
        if (sesion.getUsuario().isRequiereCambioContrasena()) {
            throw new AccessDeniedException("Debes cambiar tu contraseña antes de elegir un rol.");
        }
        var disponibles = rolesDisponibles(sesion.getUsuario());
        Rol nuevo = disponibles.stream().filter(rol -> rol.getNombre().equals(request.rol()))
                .findFirst().orElseThrow(() -> new AccessDeniedException("El rol no está disponible."));
        Rol anterior = sesion.getRolActivo();
        String nombreAnterior = anterior == null ? null : anterior.getNombre();
        if (!Objects.equals(nombreAnterior, nuevo.getNombre())) {
            sesion.setRolActivo(nuevo);
            sesion.setVersionContexto(sesion.getVersionContexto() + 1);
            auditoriaService.registrarComo(sesion.getUsuario(), nuevo, "CAMBIAR_ROL_ACTIVO",
                    ModuloAuditoria.SEGURIDAD, "SesionAcceso", sesion.getId().toString(),
                    ResultadoAuditoria.EXITOSO, null, nombreAnterior, nuevo.getNombre());
        }
        return emitirRespuesta(sesion, disponibles);
    }

    private boolean contextoVigente(SesionAcceso sesion, Jwt jwt) {
        return sesion.getEstado() == EstadoSesion.VIGENTE
                && sesion.getVersionContexto() == reclamo(jwt, "versionContexto")
                && sesion.getUsuario().getId().equals(reclamo(jwt, "usuarioId"));
    }

    private long reclamo(Jwt jwt, String nombre) {
        if (jwt.getClaims().get(nombre) instanceof Number valor) return valor.longValue();
        throw sesionInvalida();
    }

    private BadCredentialsException sesionInvalida() {
        return new BadCredentialsException("La sesión ya no está vigente.");
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
                request.newPassword(), usuario.getCodigo());
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
                ModuloAuditoria.SEGURIDAD,
                "AccesoUsuario",
                usuario.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                null);
    }

    private void registrarIntentoFallido(AccesoUsuario usuario) {
        int intentos = usuario.getIntentosFallidos() + 1;
        usuario.setIntentosFallidos(intentos);
        if (intentos >= MAX_FAILED_ATTEMPTS) {
            usuario.setEstado(EstadoAcceso.BLOQUEADO_TEMPORALMENTE);
            usuario.setBloqueadoHasta(Instant.now().plus(LOCK_DURATION));
        }
        usuarioRepository.save(usuario);
    }

    List<Rol> rolesDisponibles(AccesoUsuario usuario) {
        if (usuario.isRequiereCambioContrasena()) return List.of();
        return asignaciones.buscarDisponibles(usuario.getId());
    }

    AuthDtos.AuthResponse emitirRespuesta(SesionAcceso sesion, List<Rol> disponibles) {
        JwtService.GeneratedToken token = jwtService.generate(sesion);
        sesion.setTokenHash(HashUtils.sha256(token.value()));
        return new AuthDtos.AuthResponse("Bearer", token.value(), token.expiresAt(),
                toUserSummary(sesion, disponibles));
    }

    private AuthDtos.UserSummary toUserSummary(SesionAcceso sesion, List<Rol> disponibles) {
        var usuario = sesion.getUsuario();
        Rol activo = sesion.getRolActivo();
        List<String> permisos = activo == null ? List.of() : activo.getPermisos().stream()
                .filter(permiso -> permiso.isActivo())
                .map(permiso -> permiso.getCodigo())
                .sorted()
                .toList();
        return new AuthDtos.UserSummary(
                usuario.getId(),
                usuario.getPersona().getId(),
                usuario.getUsername(),
                usuario.getCorreo(),
                usuario.getPersona().nombreCompleto(),
                activo == null ? null : activo.getNombre(),
                permisos,
                usuario.isRequiereCambioContrasena(),
                disponibles.stream().map(Rol::getNombre).toList(),
                sesion.getId(),
                sesion.getVersionContexto());
    }
}
