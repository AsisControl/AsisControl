package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Permiso;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.PermisoRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.RolRepository;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.util.PageUtils;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class UsuarioService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.asiscontrol.repository.TokenRecuperacionRepository recuperaciones;

    private static final Set<String> SORT_FIELDS =
            Set.of("username", "correo", "estado", "fechaCreacion");

    private final AccesoUsuarioRepository usuarioRepository;
    private final PersonaRepository personaRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final SesionAccesoRepository sesionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final PerfilAccesoService perfiles;

    public UsuarioService(
            AccesoUsuarioRepository usuarioRepository,
            PersonaRepository personaRepository,
            RolRepository rolRepository,
            PermisoRepository permisoRepository,
            SesionAccesoRepository sesionRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            PerfilAccesoService perfiles) {
        this.usuarioRepository = usuarioRepository;
        this.personaRepository = personaRepository;
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.sesionRepository = sesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.perfiles = perfiles;
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioDtos.Response> list(
            String search,
            EstadoAcceso estado,
            Long roleId,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction) {
        Pageable pageable =
                PageUtils.create(page, size, sortBy, direction, SORT_FIELDS, "fechaCreacion");
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(
                usuarioRepository.searchActive(normalizedSearch, estado, roleId, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public UsuarioDtos.Response get(Long id) {
        return toResponse(findActive(id));
    }

    @Transactional
    public UsuarioDtos.Response create(UsuarioDtos.CreateRequest request) {
        Persona persona =
                personaRepository
                        .lockActiveById(request.personaId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "PERSONA_NO_ENCONTRADA",
                                                "No se encontro la persona"));
        Rol role = findActiveRole(request.rolId());
        if (!perfiles.hasProfile(persona.getId(), role.getNombre()))
            throw new ConflictException(
                    "PERFIL_REQUERIDO",
                    "La persona necesita un perfil habilitado compatible con el rol");
        if (usuarioRepository.existsByPersonaIdAndRolId(persona.getId(), role.getId()))
            throw new ConflictException(
                    "ACCESO_DUPLICADO", "La persona ya tiene un acceso para este rol");
        com.asiscontrol.security.PasswordPolicy.validate(request.password(), request.username());
        ensureUniqueCredentials(request.username(), request.correo(), null);

        AccesoUsuario usuario = new AccesoUsuario();
        usuario.setPersona(persona);
        usuario.setRol(role);
        usuario.setUsername(request.username().trim());
        usuario.setCorreo(request.correo().trim().toLowerCase());
        usuario.setContrasenaHash(passwordEncoder.encode(request.password()));
        usuario.setRequiereCambioContrasena(true);
        usuario.setContrasenaTemporalExpira(Instant.now().plus(java.time.Duration.ofHours(24)));
        usuario.setEstado(EstadoAcceso.ACTIVO);
        usuario.setCorreoVerificado(Boolean.TRUE.equals(request.correoVerificado()));
        AccesoUsuario saved = usuarioRepository.save(usuario);
        auditoriaService.registrar(
                "CREAR_USUARIO",
                "SEGURIDAD",
                "AccesoUsuario",
                saved.getId().toString(),
                ResultadoAuditoria.EXITOSO,
                null,
                null,
                "rol=" + role.getNombre());
        return toResponse(saved);
    }

    @Transactional
    public UsuarioDtos.Response provision(UsuarioDtos.ProvisionRequest request) {
        Rol role = findActiveRole(request.rolId());
        Persona persona = perfiles.resolve(request);
        perfiles.prepare(persona, role.getNombre(), request);
        return create(
                new UsuarioDtos.CreateRequest(
                        persona.getId(),
                        role.getId(),
                        request.username(),
                        request.correo(),
                        request.password(),
                        true,
                        request.correoVerificado()));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioDtos.PersonaAccesoResponse> listPersonas(
            String search, int page, int size) {
        return perfiles.list(search, page, size);
    }

    @Transactional
    public UsuarioDtos.Response update(Long id, UsuarioDtos.UpdateRequest request) {
        rolRepository.bloquearAdministrador();
        AccesoUsuario usuario =
                usuarioRepository
                        .bloquear(id)
                        .filter(AccesoUsuario::isActivo)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "USUARIO_NO_ENCONTRADO",
                                                "No se encontró el usuario"));
        protegerAdministrador(usuario, request.estado() != EstadoAcceso.ACTIVO);
        if (request.estado() != usuario.getEstado()
                && (request.motivo() == null || request.motivo().isBlank()))
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Registre el motivo del cambio de estado");
        boolean cambioCorreo = !usuario.getCorreo().equalsIgnoreCase(request.correo().trim());
        boolean reactivar =
                (usuario.getEstado() == EstadoAcceso.DESACTIVADO
                                || usuario.getEstado() == EstadoAcceso.PENDIENTE)
                        && request.estado() == EstadoAcceso.ACTIVO;
        if (reactivar) {
            com.asiscontrol.security.PasswordPolicy.validate(
                    request.temporaryPassword(), usuario.getUsername());
            if (passwordEncoder.matches(request.temporaryPassword(), usuario.getContrasenaHash()))
                throw new ConflictException(
                        "CONTRASENA_REPETIDA", "Use una contraseña temporal diferente");
            usuario.setContrasenaHash(passwordEncoder.encode(request.temporaryPassword()));
            usuario.setRequiereCambioContrasena(true);
            usuario.setContrasenaTemporalExpira(Instant.now().plus(java.time.Duration.ofHours(24)));
        }
        if (cambioCorreo
                || request.estado() != EstadoAcceso.ACTIVO
                || Boolean.FALSE.equals(request.correoVerificado()))
            recuperaciones.revocar(id, Instant.now());
        if (request.correoVerificado() != null)
            usuario.setCorreoVerificado(Boolean.TRUE.equals(request.correoVerificado()));
        else if (cambioCorreo) usuario.setCorreoVerificado(false);
        ensureUniqueCredentials(usuario.getUsername(), request.correo(), usuario.getId());
        Rol role = findActiveRole(request.rolId());
        String previous =
                "rol="
                        + usuario.getRol().getNombre()
                        + ",estado="
                        + usuario.getEstado()
                        + ",correoConfirmado="
                        + usuario.isCorreoVerificado();
        if (!usuario.getRol().getId().equals(role.getId()))
            throw new ConflictException(
                    "ROL_FIJO", "Cree otro acceso para utilizar un rol diferente");
        usuario.setRol(role);
        usuario.setCorreo(request.correo().trim().toLowerCase());
        usuario.setEstado(request.estado());
        if (request.estado() != EstadoAcceso.BLOQUEADO_TEMPORALMENTE) {
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
        }
        AccesoUsuario saved = usuarioRepository.save(usuario);
        if (request.estado() != EstadoAcceso.ACTIVO || cambioCorreo || reactivar) {
            sesionRepository.revokeAllByUsuarioId(
                    saved.getId(), EstadoSesion.REVOCADA, Instant.now());
        }
        auditoriaService.registrar(
                "ACTUALIZAR_USUARIO",
                "SEGURIDAD",
                "AccesoUsuario",
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                request.motivo(),
                previous,
                "rol="
                        + role.getNombre()
                        + ",estado="
                        + request.estado()
                        + ",correoConfirmado="
                        + saved.isCorreoVerificado());
        return toResponse(saved);
    }

    @Transactional
    public UsuarioDtos.ResetPasswordResponse resetPassword(
            Long id, UsuarioDtos.ResetPasswordRequest request) {
        AccesoUsuario usuario =
                usuarioRepository
                        .bloquear(id)
                        .filter(AccesoUsuario::isActivo)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "USUARIO_NO_ENCONTRADO",
                                                "No se encontró el usuario"));
        if (!request.identidadVerificada()
                || request.motivo() == null
                || request.motivo().isBlank())
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "IDENTIDAD_REQUERIDA", "Verifique la identidad y registre el motivo");
        if (usuario.getEstado() != EstadoAcceso.ACTIVO
                && usuario.getEstado() != EstadoAcceso.BLOQUEADO_TEMPORALMENTE) {
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "ACCESO_NO_HABILITADO", "Active el acceso antes de restablecerlo");
        }
        String temporal;
        do {
            temporal = com.asiscontrol.security.GeneradorContrasenaTemporal.generar(
                    usuario.getUsername());
        } while (passwordEncoder.matches(temporal, usuario.getContrasenaHash()));
        usuario.setContrasenaTemporalExpira(Instant.now().plus(java.time.Duration.ofHours(24)));
        recuperaciones.revocar(id, Instant.now());
        usuario.setContrasenaHash(passwordEncoder.encode(temporal));
        usuario.setRequiereCambioContrasena(true);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        if (usuario.getEstado() == EstadoAcceso.BLOQUEADO_TEMPORALMENTE) {
            usuario.setEstado(EstadoAcceso.ACTIVO);
        }
        usuarioRepository.save(usuario);
        sesionRepository.revokeAllByUsuarioId(id, EstadoSesion.REVOCADA, Instant.now());
        auditoriaService.registrar(
                "RESTABLECER_CONTRASENA",
                "SEGURIDAD",
                "AccesoUsuario",
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                request.motivo().trim(),
                null,
                null);
        return new UsuarioDtos.ResetPasswordResponse(
                usuario.getId(), temporal, usuario.getContrasenaTemporalExpira());
    }

    @Transactional
    public void delete(Long id) {
        findActive(id);
        throw new com.asiscontrol.exception.BusinessRuleException(
                "HISTORIAL_PROTEGIDO",
                "Los accesos no se eliminan. Cambie el estado con motivo desde Editar acceso");
    }

    @Transactional(readOnly = true)
    public List<UsuarioDtos.RolResponse> listRoles() {
        return rolRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .filter(
                        r ->
                                com.asiscontrol.security.RolesInstitucionales.NOMBRES
                                        .contains(r.getNombre()))
                .map(this::toRoleResponse)
                .toList();
    }

    @Transactional
    public UsuarioDtos.RolResponse createRole(UsuarioDtos.RolRequest request) {
        throw new com.asiscontrol.exception.BusinessRuleException(
                "MATRIZ_FIJA",
                "Los cuatro roles y sus permisos son configuración controlada; no se modifican"
                        + " desde la aplicación");
    }

    @Transactional
    public UsuarioDtos.RolResponse updateRole(Long id, UsuarioDtos.RolRequest request) {
        throw new com.asiscontrol.exception.BusinessRuleException(
                "MATRIZ_FIJA",
                "Los cuatro roles y sus permisos son configuración controlada; no se modifican"
                        + " desde la aplicación");
    }

    @Transactional
    public void deleteRole(Long id) {
        throw new com.asiscontrol.exception.BusinessRuleException(
                "MATRIZ_FIJA",
                "Los cuatro roles y sus permisos son configuración controlada; no se modifican"
                        + " desde la aplicación");
    }

    @Transactional(readOnly = true)
    public List<UsuarioDtos.PermisoResponse> listPermissions() {
        return permisoRepository.findAllByActivoTrueOrderByCodigoAsc().stream()
                .map(this::toPermissionResponse)
                .toList();
    }

    private void protegerAdministrador(AccesoUsuario usuario, boolean desactivar) {
        if (!desactivar) return;
        var actor =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (actor != null && actor.getName().equalsIgnoreCase(usuario.getUsername()))
            throw new ConflictException(
                    "AUTO_DESACTIVACION", "No puede desactivar su propio acceso");
        if (usuario.getRol().getNombre().equals("ADMINISTRADOR")
                && usuario.getEstado() == EstadoAcceso.ACTIVO
                && usuarioRepository.countByRolNombreAndActivoTrueAndEstado(
                                "ADMINISTRADOR", EstadoAcceso.ACTIVO)
                        <= 1)
            throw new ConflictException(
                    "ULTIMO_ADMINISTRADOR", "Debe conservar al menos un administrador habilitado");
    }

    private void ensureUniqueCredentials(String username, String email, Long currentId) {
        if (currentId == null
                && (usuarioRepository.existsByUsernameIgnoreCase(username.trim())
                        || usuarioRepository.existsByCorreoIgnoreCase(requestEmail(email))))
            throw new ConflictException(
                    "CREDENCIALES_DUPLICADAS", "El usuario o correo ya estan registrados");
        usuarioRepository
                .findActiveByIdentifier(username)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "USERNAME_DUPLICADO", "El nombre de usuario ya esta en uso");
                        });
        usuarioRepository
                .findActiveByIdentifier(email)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    "CORREO_DUPLICADO", "El correo ya esta en uso");
                        });
    }

    private String requestEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private AccesoUsuario findActive(Long id) {
        return usuarioRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "USUARIO_NO_ENCONTRADO", "No se encontro el usuario"));
    }

    private Rol findActiveRole(Long id) {
        return rolRepository
                .findByIdAndActivoTrue(id)
                .filter(role -> com.asiscontrol.security.RolesInstitucionales.NOMBRES
                        .contains(role.getNombre()))
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ROL_NO_ENCONTRADO", "No se encontro el rol"));
    }

    private UsuarioDtos.Response toResponse(AccesoUsuario usuario) {
        return new UsuarioDtos.Response(
                usuario.getId(),
                usuario.getPersona().getId(),
                usuario.getUsername(),
                usuario.getCorreo(),
                usuario.getPersona().nombreCompleto(),
                usuario.getRol().getId(),
                usuario.getRol().getNombre(),
                usuario.getEstado(),
                usuario.isRequiereCambioContrasena(),
                usuario.getIntentosFallidos(),
                usuario.getBloqueadoHasta(),
                usuario.getUltimoAcceso(),
                usuario.getFechaCreacion(),
                usuario.isCorreoVerificado(),
                usuario.getContrasenaTemporalExpira());
    }

    private UsuarioDtos.RolResponse toRoleResponse(Rol role) {
        return new UsuarioDtos.RolResponse(
                role.getId(),
                role.getNombre(),
                role.getDescripcion(),
                role.getPermisos().stream()
                        .filter(Permiso::isActivo)
                        .map(this::toPermissionResponse)
                        .sorted(java.util.Comparator.comparing(UsuarioDtos.PermisoResponse::codigo))
                        .toList(),
                role.isActivo());
    }

    private UsuarioDtos.PermisoResponse toPermissionResponse(Permiso permission) {
        return new UsuarioDtos.PermisoResponse(
                permission.getId(),
                permission.getCodigo(),
                permission.getNombre(),
                permission.getDescripcion(),
                permission.getCategoria() == null ? null : permission.getCategoria().getNombre());
    }
}
