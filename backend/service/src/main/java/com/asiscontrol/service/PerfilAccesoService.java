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
    private final PersonaService personaService;
    private final AuditoriaService auditoria;

    public PerfilAccesoService(PersonaRepository personas, AdministradorRepository administradores,
        AlumnoRepository alumnos, DocenteRepository docentes, ApoderadoRepository apoderados,
        AlumnoApoderadoRepository vinculos, AccesoUsuarioRepository accesos,
        PersonaService personaService, AuditoriaService auditoria) {
        this.personas = personas; this.administradores = administradores; this.alumnos = alumnos;
        this.docentes = docentes; this.apoderados = apoderados; this.vinculos = vinculos;
        this.accesos = accesos; this.personaService = personaService; this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioDtos.PersonaAccesoResponse> list(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) invalid("La paginacion no es valida");
        return PageResponse.from(personas.searchActive(search == null || search.isBlank() ? null : search.trim(),
            PageRequest.of(page, size, Sort.by("apellidoPaterno"))), p -> {
                List<String> profiles = new ArrayList<>();
                for (String role : List.of("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO"))
                    if (hasProfile(p.getId(), role)) profiles.add(role);
                return new UsuarioDtos.PersonaAccesoResponse(p.getId(), p.nombreCompleto(),
                    p.getNumeroDocumento(), profiles, accesos.findAllByPersonaIdAndActivoTrue(p.getId())
                        .stream().map(a -> a.getRol().getNombre()).toList());
            });
    }

    public Persona resolve(UsuarioDtos.ProvisionRequest r) {
        if ((r.personaId() == null) == (r.persona() == null))
            invalid("Seleccione una persona existente o complete una nueva, exclusivamente");
        return r.personaId() == null ? personaService.createPersona(r.persona()) : personas.lockActiveById(r.personaId())
            .orElseThrow(() -> new ResourceNotFoundException("PERSONA_NO_ENCONTRADA", "No se encontro la persona habilitada"));
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
                    if (administradores.existsByPersonaId(p.getId())) inactive();
                    Administrador a = new Administrador(); a.setPersona(p); administradores.save(a);
                }
                case "ALUMNO", "ESTUDIANTE" -> {
                    if (alumnos.existsByPersonaId(p.getId())) inactive();
                    String code = code(r.codigoAlumno());
                    if (alumnos.findByCodigoAlumnoIgnoreCase(code).isPresent()) duplicate();
                    Alumno a = new Alumno(); a.setPersona(p); a.setCodigoAlumno(code); alumnos.save(a);
                }
                case "DOCENTE" -> {
                    if (docentes.existsByPersonaId(p.getId())) inactive();
                    String code = code(r.codigoDocente());
                    if (r.fechaIngreso() == null || r.fechaIngreso().isAfter(LocalDate.now()) || r.especialidad() == null)
                        invalid("Indique una fecha de ingreso no futura y una especialidad");
                    if (docentes.findByCodigoDocenteIgnoreCase(code).isPresent()) duplicate();
                    Docente d = new Docente(); d.setPersona(p); d.setCodigoDocente(code);
                    d.setFechaIngreso(r.fechaIngreso()); d.setEspecialidad(r.especialidad()); docentes.save(d);
                }
                case "APODERADO" -> {
                    if (apoderados.existsByPersonaId(p.getId())) inactive();
                    Apoderado a = new Apoderado(); a.setPersona(p); apoderados.save(a);
                }
                default -> invalid("El rol no pertenece a los cuatro perfiles institucionales");
            }
            auditoria.registrar("CREAR_PERFIL", "PERSONAS", role, p.getId().toString(),
                ResultadoAuditoria.EXITOSO, null, null, "personaId=" + p.getId());
        }
        if (role.equals("APODERADO") && r.alumnoIds() != null) {
            Apoderado a = apoderados.findByPersonaIdAndActivoTrue(p.getId()).orElseThrow();
            for (Long id : r.alumnoIds()) {
                alumnos.findByIdAndActivoTrue(id).filter(s -> s.getPersona().isActivo())
                    .orElseThrow(() -> new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "No se encontro un estudiante habilitado"));
                if (vinculos.findByAlumnoIdAndApoderadoIdAndActivoTrue(id, a.getId()).isEmpty()) {
                    personaService.linkGuardian(id,
                        new com.asiscontrol.dto.persona.PersonaDtos.VinculoApoderadoRequest(
                            a.getId(), Parentesco.NO_DECLARADO, false));
                }
            }
        }
    }
    private String code(String code) {
        if (code == null || code.isBlank() || code.trim().length() > 30) invalid("El codigo del perfil es obligatorio y admite hasta 30 caracteres");
        return code.trim().toUpperCase(java.util.Locale.ROOT);
    }
    private void invalid(String message) { throw new BusinessRuleException("DATOS_ACCESO_INVALIDOS", message); }
    private void inactive() { throw new ConflictException("PERFIL_INACTIVO", "La persona ya tiene un perfil inactivo; revise su estado"); }
    private void duplicate() { throw new ConflictException("CODIGO_DUPLICADO", "El codigo del perfil ya existe"); }
}
