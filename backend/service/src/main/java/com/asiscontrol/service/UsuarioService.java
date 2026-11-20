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
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.PermisoRepository;
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
            Set.of("codigo", "username", "correo", "estado", "fechaCreacion");

    private final AccesoUsuarioRepository usuarioRepository;
    private final AccesoUsuarioRolRepository asignaciones;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final SesionAccesoRepository sesionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final PerfilAccesoService perfiles;
    private final CredencialesInstitucionales credenciales;
    private final CicloCuentaService ciclo;
    private final CodigoInstitucionalService codigos;
    private final VigenciaContrasenaTemporal vigencia;

    public UsuarioService(
            AccesoUsuarioRepository usuarioRepository,
            AccesoUsuarioRolRepository asignaciones,
            RolRepository rolRepository,
            PermisoRepository permisoRepository,
            SesionAccesoRepository sesionRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            PerfilAccesoService perfiles,
            CredencialesInstitucionales credenciales,
            CicloCuentaService ciclo,
            CodigoInstitucionalService codigos,
            VigenciaContrasenaTemporal vigencia) {
        this.usuarioRepository = usuarioRepository;
        this.asignaciones = asignaciones;
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.sesionRepository = sesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.perfiles = perfiles;
        this.credenciales = credenciales;
        this.ciclo = ciclo;
        this.codigos = codigos;
        this.vigencia = vigencia;
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
    public UsuarioDtos.RegistroAdministradorResponse registrarAdministrador(
            UsuarioDtos.RegistroAdministradorRequest request) {
        prepararAlta();
        Persona persona = perfiles.resolve(request.personaId(), request.persona());
        String codigoPerfil = perfiles.registrarAdministrador(persona);
        var credencial = ciclo.altaDesdeFicha(persona, "ADMINISTRADOR", codigoPerfil);
        AccesoUsuario cuenta = usuarioRepository.findByPersonaId(persona.getId()).orElseThrow();
        return new UsuarioDtos.RegistroAdministradorResponse(
                cuenta.getId(), cuenta.getCodigo(), cuenta.getUsername(), cuenta.getCorreo(),
                persona.nombreCompleto(),
                credencial.temporaryPassword(), credencial.expiresAt());
    }

    @Transactional
    public UsuarioDtos.Response cambiarRol(Long id, Long rolId, UsuarioDtos.CambiarRolRequest req) {
        if (req.motivo() == null || req.motivo().isBlank()) {
            throw new BusinessRuleException("MOTIVO_REQUERIDO", "Registre el motivo del cambio de rol");
        }
        rolRepository.bloquearAdministrador().orElseThrow();
        AccesoUsuario cuenta = buscarConBloqueo(id);
        Rol rol = findActiveRole(rolId);
        if (req.activo() && (!cuenta.getPersona().isActivo()
                || !perfiles.hasProfile(cuenta.getPersona().getId(), rol.getNombre()))) {
            throw new ConflictException(
                    "PERFIL_REQUERIDO", "La cuenta necesita un perfil habilitado para este rol");
        }
        ciclo.cambiarRol(cuenta, rol, req.activo(), req.motivo().trim());
        return toResponse(cuenta);
    }

    /** Alta con temporal preconfigurada para integraciones internas autorizadas. */
    @Transactional
    public UsuarioDtos.Response create(UsuarioDtos.CreateRequest request) {
        prepararAlta();
        Persona persona = perfiles.resolve(request.personaId(), null);
        Rol rol = findActiveRole(request.rolId());
        return altaConfigurada(persona, rol, request.correo(), request.password(),
                Boolean.TRUE.equals(request.correoVerificado()));
    }

    @Transactional
    public UsuarioDtos.Response provision(UsuarioDtos.ProvisionRequest request) {
        prepararAlta();
        Rol rol = findActiveRole(request.rolId());
        Persona persona = perfiles.resolve(request.personaId(), request.persona());
        perfiles.prepare(persona, rol.getNombre(), request);
        return altaConfigurada(persona, rol, null, request.password(),
                Boolean.TRUE.equals(request.correoVerificado()));
    }

    private void prepararAlta() {
        rolRepository.bloquearAdministrador().orElseThrow();
        codigos.bloquearSecuenciaCuentas();
    }

    private UsuarioDtos.Response altaConfigurada(
            Persona persona, Rol rol, String correo, String password, boolean verificado) {
        String codigo = codigoPerfil(persona, rol);
        // Sin correo indicado, el ciclo de cuentas genera a{código de cuenta}@dominio.
        ciclo.altaConfigurada(persona, rol.getNombre(), codigo, correo, password, verificado);
        return toResponse(usuarioRepository.findByPersonaId(persona.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioDtos.PersonaAccesoResponse> listPersonas(
            String search, int page, int size) {
        return perfiles.list(search, page, size);
    }

    @Transactional
    public UsuarioDtos.Response update(Long id, UsuarioDtos.UpdateRequest request) {
        rolRepository.bloquearAdministrador().orElseThrow();
        AccesoUsuario usuario = buscarConBloqueo(id);
        if (request.estado() == EstadoAcceso.BLOQUEADO_TEMPORALMENTE
                && request.estado() != usuario.getEstado()) {
            throw new BusinessRuleException("ESTADO_INVALIDO",
                    "El bloqueo temporal solo puede aplicarlo el sistema");
        }
        boolean deshabilitar = request.estado() == EstadoAcceso.DESACTIVADO
                || request.estado() == EstadoAcceso.PENDIENTE;
        if (deshabilitar && request.estado() != usuario.getEstado()) {
            protegerCuentaPropia(usuario);
            ciclo.protegerUltimoAdministrador(usuario);
        }
        if (request.estado() != usuario.getEstado()
                && (request.motivo() == null || request.motivo().isBlank()))
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Registre el motivo del cambio de estado");
        String nuevoCorreo = request.correo().trim().toLowerCase(java.util.Locale.ROOT);
        boolean cambioCorreo = !usuario.getCorreo().equalsIgnoreCase(nuevoCorreo);
        String previous = "estado=" + usuario.getEstado()
                + ",correoConfirmado=" + usuario.isCorreoVerificado();
        boolean reactivar =
                (usuario.getEstado() == EstadoAcceso.DESACTIVADO
                                || usuario.getEstado() == EstadoAcceso.PENDIENTE)
                        && request.estado() == EstadoAcceso.ACTIVO;
        if (reactivar) {
            com.asiscontrol.security.PasswordPolicy.validate(
                    request.temporaryPassword(), usuario.getCodigo());
            if (passwordEncoder.matches(request.temporaryPassword(), usuario.getContrasenaHash()))
                throw new ConflictException(
                        "CONTRASENA_REPETIDA", "Use una contraseña temporal diferente");
            usuario.setContrasenaHash(passwordEncoder.encode(request.temporaryPassword()));
            usuario.setRequiereCambioContrasena(true);
            usuario.setContrasenaTemporalExpira(vigencia.expiraDesde(Instant.now()));
        }
        if (cambioCorreo
                || request.estado() != EstadoAcceso.ACTIVO
                || Boolean.FALSE.equals(request.correoVerificado()))
            recuperaciones.revocar(id, Instant.now());
        if (request.correoVerificado() != null)
            usuario.setCorreoVerificado(Boolean.TRUE.equals(request.correoVerificado()));
        else if (cambioCorreo) usuario.setCorreoVerificado(false);
        if (cambioCorreo && usuarioRepository.existsByCorreoIgnoreCase(nuevoCorreo)) {
            throw new ConflictException("CORREO_DUPLICADO", "El correo ya está en uso");
        }
        usuario.setCorreo(nuevoCorreo);
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
                ModuloAuditoria.SEGURIDAD,
                "AccesoUsuario",
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                request.motivo(),
                previous,
                "estado=" + request.estado()
                        + ",correoConfirmado="
                        + saved.isCorreoVerificado());
        return toResponse(saved);
    }

    @Transactional
    public void resetPassword(Long id, UsuarioDtos.ResetPasswordRequest request) {
        AccesoUsuario usuario = buscarConBloqueo(id);
        if (!request.identidadVerificada()
                || request.motivo() == null
                || request.motivo().isBlank())
            throw new com.asiscontrol.exception.BusinessRuleException(
                    "IDENTIDAD_REQUERIDA", "Verifique la identidad y registre el motivo");
        com.asiscontrol.security.PasswordPolicy.validate(
                request.temporaryPassword(), usuario.getCodigo());
        if (passwordEncoder.matches(request.temporaryPassword(), usuario.getContrasenaHash()))
            throw new ConflictException(
                    "CONTRASENA_REPETIDA", "Use una contraseña temporal diferente");
        usuario.setContrasenaTemporalExpira(vigencia.expiraDesde(Instant.now()));
        recuperaciones.revocar(id, Instant.now());
        usuario.setContrasenaHash(passwordEncoder.encode(request.temporaryPassword()));
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
                ModuloAuditoria.SEGURIDAD,
                "AccesoUsuario",
                id.toString(),
                ResultadoAuditoria.EXITOSO,
                request.motivo().trim(),
                null,
                null);
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
                                CicloCuentaService.ROLES_INSTITUCIONALES.contains(r.getNombre()))
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

    private void protegerCuentaPropia(AccesoUsuario usuario) {
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        if (actor != null && actor.getName().equalsIgnoreCase(usuario.getCodigo())) {
            throw new ConflictException(
                    "AUTO_DESACTIVACION", "No puede desactivar su propio acceso");
        }
    }

    private AccesoUsuario buscarConBloqueo(Long id) {
        return usuarioRepository.bloquear(id).filter(AccesoUsuario::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USUARIO_NO_ENCONTRADO", "No se encontró el usuario"));
    }

    private String codigoPerfil(Persona persona, Rol role) {
        String codigo = perfiles.codigoPerfil(persona.getId(), role.getNombre(), true);
        if (codigo == null)
            throw new ConflictException(
                    "PERFIL_REQUERIDO",
                    "La persona necesita un perfil habilitado compatible con el rol");
        return codigo;
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
                .filter(rol -> CicloCuentaService.ROLES_INSTITUCIONALES.contains(rol.getNombre()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ROL_NO_ENCONTRADO", "No se encontró el rol institucional"));
    }

    private UsuarioDtos.Response toResponse(AccesoUsuario usuario) {
        List<UsuarioDtos.RolAsignadoResponse> roles = asignaciones.findAllByUsuarioId(usuario.getId())
                .stream()
                .map(asignacion -> new UsuarioDtos.RolAsignadoResponse(
                        asignacion.getRol().getId(), asignacion.getRol().getNombre(),
                        asignacion.isActivo(), perfiles.codigoPerfil(
                                usuario.getPersona().getId(), asignacion.getRol().getNombre(), false)))
                .sorted(java.util.Comparator.comparing(UsuarioDtos.RolAsignadoResponse::nombre))
                .toList();
        var rolUnico = roles.size() == 1 ? roles.getFirst() : null;
        return new UsuarioDtos.Response(
                usuario.getId(),
                usuario.getPersona().getId(),
                usuario.getCodigo(),
                usuario.getUsername(),
                usuario.getCorreo(),
                usuario.getPersona().nombreCompleto(),
                rolUnico == null ? null : rolUnico.id(),
                rolUnico == null ? null : rolUnico.nombre(),
                usuario.getEstado(),
                usuario.isRequiereCambioContrasena(),
                usuario.getIntentosFallidos(),
                usuario.getBloqueadoHasta(),
                usuario.getUltimoAcceso(),
                usuario.getFechaCreacion(),
                usuario.isCorreoVerificado(),
                usuario.getContrasenaTemporalExpira(),
                rolUnico == null ? null : rolUnico.codigoPerfil(),
                roles);
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
