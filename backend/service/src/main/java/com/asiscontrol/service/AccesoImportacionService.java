package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.RolRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altas de la carga masiva con su acceso al sistema. Cada método registra el perfil y su cuenta
 * en la misma transacción: si no se puede crear la cuenta, tampoco queda la persona. Una persona
 * que ya tiene acceso para el rol conserva esa cuenta y no recibe otra.
 */
@Service
public class AccesoImportacionService {

    private final PersonaService personas;
    private final UsuarioService usuarios;
    private final AccesoUsuarioRepository accesos;
    private final RolRepository roles;
    private final GeneradorContrasenaTemporal contrasenas;
    private final PerfilAccesoService perfiles;

    public AccesoImportacionService(
            PersonaService personas,
            UsuarioService usuarios,
            AccesoUsuarioRepository accesos,
            RolRepository roles,
            GeneradorContrasenaTemporal contrasenas,
            PerfilAccesoService perfiles) {
        this.personas = personas;
        this.usuarios = usuarios;
        this.accesos = accesos;
        this.roles = roles;
        this.contrasenas = contrasenas;
        this.perfiles = perfiles;
    }

    @Transactional
    public PersonaDtos.CredencialAcceso alumno(int fila, PersonaDtos.AlumnoCreateRequest request) {
        var alumno = personas.createAlumno(request);
        return acceso(fila, alumno.persona().id(), alumno.persona().nombreCompleto(), "ALUMNO");
    }

    @Transactional
    public PersonaDtos.CredencialAcceso docente(int fila, PersonaDtos.DocenteCreateRequest request) {
        var docente = personas.createDocente(request);
        return acceso(fila, docente.persona().id(), docente.persona().nombreCompleto(), "DOCENTE");
    }

    @Transactional
    public PersonaDtos.CredencialAcceso apoderado(
            int fila, PersonaDtos.ApoderadoCreateRequest request) {
        var apoderado = personas.registrarOReutilizarApoderado(request);
        return acceso(
                fila, apoderado.persona().id(), apoderado.persona().nombreCompleto(), "APODERADO");
    }

    private PersonaDtos.CredencialAcceso acceso(
            int fila, Long personaId, String nombreCompleto, String rol) {
        var existente = accesos.findAllByPersonaIdAndActivoTrue(personaId).stream()
                .filter(acceso -> acceso.getRol().getNombre().equals(rol))
                .findFirst();
        if (existente.isPresent()) {
            var cuenta = existente.get();
            return new PersonaDtos.CredencialAcceso(
                    fila, nombreCompleto, rol, perfiles.codigoPerfil(personaId, rol, false),
                    cuenta.getUsername(), cuenta.getCorreo(), null, null, true);
        }
        Long rolId = roles.findByNombreIgnoreCase(rol).orElseThrow().getId();
        String contrasena = contrasenas.generar(null);
        UsuarioDtos.Response creado =
                usuarios.create(new UsuarioDtos.CreateRequest(personaId, rolId, contrasena));
        return new PersonaDtos.CredencialAcceso(
                fila, nombreCompleto, rol, creado.codigoPerfil(), creado.username(),
                creado.correo(), contrasena, creado.contrasenaTemporalExpira(), false);
    }
}
