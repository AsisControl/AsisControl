package com.asiscontrol.service;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.exception.*;
import com.asiscontrol.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PerfilAccesoService {
    private final PersonaRepository personas;
    private final AdministradorRepository administradores;
    private final AlumnoRepository alumnos;
    private final DocenteRepository docentes;
    private final ApoderadoRepository apoderados;
    private final AlumnoApoderadoRepository vinculos;
    private final AccesoUsuarioRepository accesos;
    private final AccesoUsuarioRolRepository asignaciones;
    private final PersonaService personaService;
    private final AuditoriaService auditoria;
    private final CodigoInstitucionalService codigos;

    public PerfilAccesoService(PersonaRepository personas, AdministradorRepository administradores,
        AlumnoRepository alumnos, DocenteRepository docentes, ApoderadoRepository apoderados,
        AlumnoApoderadoRepository vinculos, AccesoUsuarioRepository accesos,
        AccesoUsuarioRolRepository asignaciones,
        PersonaService personaService, AuditoriaService auditoria,
        CodigoInstitucionalService codigos) {
        this.personas = personas; this.administradores = administradores; this.alumnos = alumnos;
        this.docentes = docentes; this.apoderados = apoderados; this.vinculos = vinculos;
        this.accesos = accesos; this.personaService = personaService; this.auditoria = auditoria;
        this.asignaciones = asignaciones;
        this.codigos = codigos;
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioDtos.PersonaAccesoResponse> list(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) invalid("La paginacion no es valida");
        return PageResponse.from(personas.searchActive(search == null || search.isBlank() ? null : search.trim(),
            PageRequest.of(page, size, Sort.by("apellidoPaterno"))), p -> {
                List<String> profiles = new ArrayList<>();
                for (String role : List.of("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO"))
                    if (hasProfile(p.getId(), role)) profiles.add(role);
                List<String> roles = accesos.findByPersonaId(p.getId()).stream()
                        .flatMap(cuenta -> asignaciones.findAllByUsuarioId(cuenta.getId()).stream())
                        .filter(AccesoUsuarioRol::isActivo)
                        .map(a -> a.getRol().getNombre()).toList();
                return new UsuarioDtos.PersonaAccesoResponse(
                        p.getId(), p.nombreCompleto(), p.getNumeroDocumento(), profiles, roles);
            });
    }

    public Persona resolve(Long personaId, com.asiscontrol.dto.persona.PersonaDtos.PersonaRequest datos) {
        if ((personaId == null) == (datos == null))
            invalid("Seleccione una persona existente o complete una nueva, exclusivamente");
        return personaId == null ? personaService.resolvePersona(datos) : personas.lockActiveById(personaId)
            .orElseThrow(() -> new ResourceNotFoundException("PERSONA_NO_ENCONTRADA", "No se encontro la persona habilitada"));
    }

    public String registrarAdministrador(Persona persona) {
        if (hasProfile(persona.getId(), "ADMINISTRADOR")) {
            return codigoPerfil(persona.getId(), "ADMINISTRADOR", true);
        }
        if (administradores.existsByPersonaId(persona.getId())) inactive();
        Administrador administrador = new Administrador();
        administrador.setPersona(persona);
        administrador.setCodigoAdministrador(codigos.siguiente(CodigoInstitucionalService.Prefijo.ADM));
        administradores.save(administrador);
        auditoria.registrar("CREAR_PERFIL", ModuloAuditoria.PERSONAS, "Administrador",
                administrador.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "personaId=" + persona.getId());
        return administrador.getCodigoAdministrador();
    }

    public boolean hasProfile(Long id, String role) {
        return switch (role) {
            case "ADMINISTRADOR" -> administradores.findByPersonaIdAndActivoTrue(id).isPresent();
            case "DOCENTE" -> docentes.findByPersonaIdAndActivoTrue(id).isPresent();
            case "ALUMNO", "ESTUDIANTE" -> alumnos.findByPersonaIdAndActivoTrue(id).isPresent();
            case "APODERADO" -> apoderados.findByPersonaIdAndActivoTrue(id).isPresent();
            default -> false;
        };
    }

    public void prepare(Persona p, String role, UsuarioDtos.ProvisionRequest r) {
        if (r.alumnoIds() != null && !r.alumnoIds().isEmpty() && !role.equals("APODERADO"))
            invalid("Las asociaciones con estudiantes corresponden al apoderado");
        if (!hasProfile(p.getId(), role)) {
            switch (role) {
                case "ADMINISTRADOR" -> {
                    registrarAdministrador(p);
                    return;
                }
                case "ALUMNO", "ESTUDIANTE" -> {
                    if (alumnos.existsByPersonaId(p.getId())) inactive();
                    if (p.getTipoDocumento() == null || p.getNumeroDocumento() == null)
                        invalid("El documento de identidad del estudiante es obligatorio");
                    Alumno a = new Alumno(); a.setPersona(p);
                    a.setCodigoAlumno(codigos.siguiente(CodigoInstitucionalService.Prefijo.EST));
                    alumnos.save(a);
                }
                case "DOCENTE" -> {
                    if (docentes.existsByPersonaId(p.getId())) inactive();
                    if (r.fechaIngreso() == null || r.fechaIngreso().isAfter(LocalDate.now()) || r.especialidad() == null)
                        invalid("Indique una fecha de ingreso no futura y una especialidad");
                    Docente d = new Docente(); d.setPersona(p);
                    d.setCodigoDocente(codigos.siguiente(CodigoInstitucionalService.Prefijo.DOC));
                    d.setFechaIngreso(r.fechaIngreso()); d.setEspecialidad(r.especialidad()); docentes.save(d);
                }
                case "APODERADO" -> {
                    if (apoderados.existsByPersonaId(p.getId())) inactive();
                    Apoderado a = new Apoderado(); a.setPersona(p);
                    a.setCodigoApoderado(codigos.siguiente(CodigoInstitucionalService.Prefijo.APO));
                    apoderados.save(a);
                }
                default -> invalid("El rol no pertenece a los cuatro perfiles institucionales");
            }
            auditoria.registrar("CREAR_PERFIL", ModuloAuditoria.PERSONAS, role.charAt(0) + role.substring(1).toLowerCase(), p.getId().toString(),
                ResultadoAuditoria.EXITOSO, null, null, "personaId=" + p.getId());
        }
        if (role.equals("APODERADO") && r.alumnoIds() != null) {
            Apoderado a = apoderados.findByPersonaIdAndActivoTrue(p.getId()).orElseThrow();
            for (Long id : r.alumnoIds()) {
                alumnos.findByIdAndActivoTrue(id).filter(s -> s.getPersona().isActivo())
                    .orElseThrow(() -> new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "No se encontro un estudiante habilitado"));
                if (vinculos.findByAlumnoIdAndApoderadoIdAndActivoTrue(id, a.getId()).isEmpty()) {
                    // linkGuardian ya audita ASOCIAR_APODERADO.
                    personaService.linkGuardian(id,
                        new com.asiscontrol.dto.persona.PersonaDtos.VinculoApoderadoRequest(
                            a.getId(), Parentesco.NO_DECLARADO, false));
                }
            }
        }
    }
    /**
     * Código institucional del perfil habilitado para el rol, o null si no tiene. Con
     * asignarSiFalta, un apoderado o administrador sin código (registrado fuera de las altas del
     * sistema) recibe uno; requiere una transacción de escritura.
     */
    public String codigoPerfil(Long personaId, String role, boolean asignarSiFalta) {
        return switch (role) {
            case "ADMINISTRADOR" -> administradores.findByPersonaIdAndActivoTrue(personaId).map(a -> {
                if (asignarSiFalta && a.getCodigoAdministrador() == null)
                    a.setCodigoAdministrador(codigos.siguiente(CodigoInstitucionalService.Prefijo.ADM));
                return a.getCodigoAdministrador();
            }).orElse(null);
            case "DOCENTE" -> docentes.findByPersonaIdAndActivoTrue(personaId).map(Docente::getCodigoDocente).orElse(null);
            case "ALUMNO", "ESTUDIANTE" -> alumnos.findByPersonaIdAndActivoTrue(personaId).map(Alumno::getCodigoAlumno).orElse(null);
            case "APODERADO" -> apoderados.findByPersonaIdAndActivoTrue(personaId).map(a -> {
                if (asignarSiFalta && a.getCodigoApoderado() == null)
                    a.setCodigoApoderado(codigos.siguiente(CodigoInstitucionalService.Prefijo.APO));
                return a.getCodigoApoderado();
            }).orElse(null);
            default -> null;
        };
    }
    private void invalid(String message) { throw new BusinessRuleException("DATOS_ACCESO_INVALIDOS", message); }
    private void inactive() { throw new ConflictException("PERFIL_INACTIVO", "La persona ya tiene un perfil inactivo; revise su estado"); }
}
