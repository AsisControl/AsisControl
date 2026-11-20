package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos.CredencialAcceso;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AccesoUsuarioRol;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.RolRepository;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.asiscontrol.security.PasswordPolicy;

import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class CicloCuentaService {
    public static final Set<String> ROLES_INSTITUCIONALES =
            Set.of("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO");

    private final AccesoUsuarioRepository cuentas;
    private final AccesoUsuarioRolRepository asignaciones;
    private final PersonaRepository personas;
    private final RolRepository roles;
    private final SesionAccesoRepository sesiones;
    private final AuditoriaService auditoria;
    private final CodigoInstitucionalService codigos;
    private final CredencialesInstitucionales credenciales;
    private final GeneradorContrasenaTemporal temporales;
    private final VigenciaContrasenaTemporal vigencia;
    private final PasswordEncoder encoder;
    private final EntityManager entityManager;

    public CicloCuentaService(
            AccesoUsuarioRepository cuentas,
            AccesoUsuarioRolRepository asignaciones,
            PersonaRepository personas,
            RolRepository roles,
            SesionAccesoRepository sesiones,
            AuditoriaService auditoria,
            CodigoInstitucionalService codigos,
            CredencialesInstitucionales credenciales,
            GeneradorContrasenaTemporal temporales,
            VigenciaContrasenaTemporal vigencia,
            PasswordEncoder encoder,
            EntityManager entityManager) {
        this.cuentas = cuentas;
        this.asignaciones = asignaciones;
        this.personas = personas;
        this.roles = roles;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.codigos = codigos;
        this.credenciales = credenciales;
        this.temporales = temporales;
        this.vigencia = vigencia;
        this.encoder = encoder;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CredencialAcceso altaDesdeFicha(Persona persona, String rol, String codigoPerfil) {
        return registrar(persona, rol, codigoPerfil, null, null, false);
    }

    /** Los arranques configurados pueden entregar una clave que ya conoce el operador. */
    @Transactional(propagation = Propagation.MANDATORY)
    public CredencialAcceso altaConfigurada(
            Persona persona, String rol, String codigoPerfil, String correo, String password,
            boolean correoVerificado) {
        return registrar(persona, rol, codigoPerfil, correo, password, correoVerificado);
    }

    private CredencialAcceso registrar(
            Persona persona, String rol, String codigoPerfil, String correo, String password,
            boolean correoVerificado) {
        if ("ADMINISTRADOR".equals(rol)) roles.bloquearAdministrador().orElseThrow();
        Rol rolInstitucional = roles.findByNombreIgnoreCase(rol).filter(Rol::isActivo)
                .filter(r -> ROLES_INSTITUCIONALES.contains(r.getNombre()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ROL_NO_ENCONTRADO", "El rol institucional no está habilitado"));
        Persona identidad = personas.lockActiveById(persona.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PERSONA_NO_ENCONTRADA", "La persona no está habilitada"));
        AccesoUsuario cuenta = cuentas.bloquearPorPersonaId(identidad.getId()).orElse(null);
        boolean existente = cuenta != null;
        String temporal = null;
        if (cuenta == null) {
            String username = codigos.siguiente(CodigoInstitucionalService.Prefijo.CTA);
            temporal = password == null ? temporales.generar(username) : password;
            String email = correo == null ? correoDisponible(username)
                    : correo.trim().toLowerCase(Locale.ROOT);
            if (cuentas.existsByCorreoIgnoreCase(email)) {
                throw new ConflictException("CORREO_DUPLICADO", "El correo de acceso ya está registrado");
            }
            PasswordPolicy.validate(temporal, username);
            cuenta = new AccesoUsuario();
            cuenta.setPersona(identidad);
            cuenta.setUsername(username);
            cuenta.setCorreo(email);
            cuenta.setCorreoVerificado(correo != null && correoVerificado);
            cuenta.setContrasenaHash(encoder.encode(temporal));
            cuenta.setRequiereCambioContrasena(true);
            cuenta.setContrasenaTemporalExpira(vigencia.expiraDesde(Instant.now()));
            cuenta.setEstado(EstadoAcceso.ACTIVO);
            cuenta = cuentas.save(cuenta);
            auditar(cuenta, "CREAR_USUARIO", null, null, "estado=ACTIVO");
        }
        if (asignaciones.bloquearPorUsuarioIdYRolId(cuenta.getId(), rolInstitucional.getId()).isEmpty()) {
            asignar(cuenta, rolInstitucional, null);
        }
        return new CredencialAcceso(
                0, identidad.nombreCompleto(), rol, codigoPerfil, cuenta.getUsername(), cuenta.getCorreo(),
                temporal, existente ? null : cuenta.getContrasenaTemporalExpira(), existente);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cambiarRol(AccesoUsuario cuenta, Rol rol, boolean activo, String motivo) {
        var asignacion = asignaciones.bloquearPorUsuarioIdYRolId(cuenta.getId(), rol.getId());
        if (asignacion.isEmpty()) {
            if (activo) asignar(cuenta, rol, motivo);
            return;
        }
        AccesoUsuarioRol permiso = asignacion.get();
        if (permiso.isActivo() == activo) return;
        if (!activo && "ADMINISTRADOR".equals(rol.getNombre())) protegerUltimoAdministrador(cuenta);
        permiso.setActivo(activo);
        asignaciones.save(permiso);
        if (!activo) sesiones.invalidarRolActivo(cuenta.getId(), rol.getId());
        auditar(cuenta, activo ? "ACTIVAR_ROL" : "REVOCAR_ROL", motivo,
                "rol=" + rol.getNombre() + ",activo=" + !activo,
                "rol=" + rol.getNombre() + ",activo=" + activo);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revocarDesdeFicha(Long personaId, String nombreRol) {
        cuentas.bloquearPorPersonaId(personaId).ifPresent(cuenta ->
                roles.findByNombreIgnoreCase(nombreRol).ifPresent(rol ->
                        cambiarRol(cuenta, rol, false, "Perfil institucional deshabilitado")));
    }

    /**
     * Los cambios que pueden dar de baja un administrador comparten el bloqueo del Rol.
     * La lectura con bloqueo también ve las bajas confirmadas mientras esta transacción esperaba.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void protegerUltimoAdministrador(AccesoUsuario cuenta) {
        if (cuenta.getEstado() != EstadoAcceso.ACTIVO
                && cuenta.getEstado() != EstadoAcceso.BLOQUEADO_TEMPORALMENTE) return;
        var ids = entityManager.createNativeQuery(
                """
                SELECT u.id_acceso_usuario FROM AccesoUsuario u
                JOIN AccesoUsuarioRol ar ON ar.id_acceso_usuario = u.id_acceso_usuario
                JOIN Rol r ON r.id_rol = ar.id_rol
                JOIN Persona p ON p.id_persona = u.id_persona
                JOIN Administrador a ON a.id_persona = p.id_persona
                WHERE u.activo = TRUE
                  AND u.estado_acceso IN ('ACTIVO', 'BLOQUEADO_TEMPORALMENTE')
                  AND p.activo = TRUE AND ar.activo = TRUE AND a.activo = TRUE
                  AND r.activo = TRUE AND r.nombre = 'ADMINISTRADOR'
                FOR UPDATE
                """, Long.class).getResultList();
        if (ids.size() == 1 && ids.contains(cuenta.getId())) {
            throw new ConflictException(
                    "ULTIMO_ADMINISTRADOR", "Debe conservar al menos un administrador habilitado");
        }
    }

    private void asignar(AccesoUsuario cuenta, Rol rol, String motivo) {
        AccesoUsuarioRol permiso = new AccesoUsuarioRol();
        permiso.setUsuario(cuenta);
        permiso.setRol(rol);
        asignaciones.save(permiso);
        auditar(cuenta, "ASIGNAR_ROL", motivo, null, "rol=" + rol.getNombre() + ",activo=true");
    }

    private String correoDisponible(String username) {
        String correo = credenciales.correo(username);
        for (int sufijo = 2; cuentas.existsByCorreoIgnoreCase(correo); sufijo++) {
            correo = credenciales.correo(username + "-" + sufijo);
        }
        return correo;
    }

    private void auditar(
            AccesoUsuario cuenta, String accion, String motivo, String anterior, String nuevo) {
        auditoria.registrar(accion, ModuloAuditoria.SEGURIDAD, "AccesoUsuario",
                cuenta.getId().toString(), ResultadoAuditoria.EXITOSO, motivo, anterior, nuevo);
    }
}
