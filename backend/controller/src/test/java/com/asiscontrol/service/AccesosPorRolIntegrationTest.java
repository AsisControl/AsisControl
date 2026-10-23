package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccesosPorRolIntegrationTest {
    @Autowired UsuarioService service;
    @Autowired PersonaService personas;
    @Autowired PersonaRepository personaRepo;
    @Autowired AccesoUsuarioRepository accesoRepo;
    @Autowired DocenteRepository docenteRepo;
    @Autowired ApoderadoRepository apoderadoRepo;
    @Autowired AlumnoApoderadoRepository links;
    @Autowired RolRepository roles;

    private long role(String name) { return roles.findByNombreIgnoreCase(name).orElseThrow().getId(); }
    private String key() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private PersonaDtos.PersonaRequest person(String k) {
        return new PersonaDtos.PersonaRequest("Persona", "Prueba", "Sistema", TipoDocumento.PASAPORTE,
            k, null, Sexo.PREFIERE_NO_INDICAR, LocalDate.of(1990, 1, 1), null, k + "@example.test", null);
    }
    private UsuarioDtos.ProvisionRequest request(String rol, Long id, Set<Long> studentIds) {
        String k = key();
        return new UsuarioDtos.ProvisionRequest(id, id == null ? person(k) : null, role(rol),
            "usr" + k, "cuenta" + k + "@example.test", "Temporal123!", "EST" + k, "DOC" + k,
            LocalDate.now(), Especialidad.MATEMATICA, studentIds);
    }

    @Test void permiteDocenteYApoderadoSeparadosEnUnaPersona() {
        var first = service.provision(request("DOCENTE", null, Set.of()));
        var second = service.provision(request("APODERADO", first.personaId(), Set.of()));
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.personaId()).isEqualTo(first.personaId());
        assertThat(accesoRepo.findAllByPersonaIdAndActivoTrue(first.personaId())).hasSize(2);
        assertThat(docenteRepo.findByPersonaIdAndActivoTrue(first.personaId())).isPresent();
        assertThat(apoderadoRepo.findByPersonaIdAndActivoTrue(first.personaId())).isPresent();
    }
    @Test void noDuplicaAccesoDelMismoRol() {
        var first = service.provision(request("ADMINISTRADOR", null, Set.of()));
        assertThatThrownBy(() -> service.provision(request("ADMINISTRADOR", first.personaId(), Set.of())))
            .isInstanceOf(ConflictException.class);
    }
    @Test void obligaCambioInicialInclusoEnCreacionDirecta() {
        var first = service.provision(request("ALUMNO", null, Set.of()));
        assertThat(first.requiereCambioContrasena()).isTrue();
    }
    @Test void vinculaApoderadoConEstudianteHabilitado() {
        var personaRequest = person(key());
        // La ficha secundaria ahora exige 10–20 años; los otros perfiles conservan su fixture adulto.
        var studentPerson = new PersonaDtos.PersonaRequest(
                personaRequest.nombres(), personaRequest.apellidoPaterno(),
                personaRequest.apellidoMaterno(), personaRequest.tipoDocumento(),
                personaRequest.numeroDocumento(), personaRequest.nacionalidadId(),
                personaRequest.sexo(),
                LocalDate.now(java.time.ZoneId.of("America/Lima")).minusYears(14),
                personaRequest.telefono(), personaRequest.correo(), personaRequest.direccion());
        var student = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(studentPerson, "EST" + key()));
        var guardian = service.provision(request("APODERADO", null, Set.of(student.id())));
        var profile = apoderadoRepo.findByPersonaIdAndActivoTrue(guardian.personaId()).orElseThrow();
        var link = links.findByAlumnoIdAndApoderadoIdAndActivoTrue(student.id(), profile.getId()).orElseThrow();
        assertThat(link.getParentesco()).isEqualTo(Parentesco.NO_DECLARADO);
    }
    @Test void exigePerfilCompatibleEnEndpointLegado() {
        var person = personas.createPersona(person(key()));
        assertThatThrownBy(() -> service.create(new UsuarioDtos.CreateRequest(person.getId(), role("DOCENTE"),
            "doc" + key(), key() + "@example.test", "Temporal123!", false)))
            .isInstanceOf(ConflictException.class);
    }
    @Test void noPermiteCambiarRolDeCuentaExistente() {
        var first = service.provision(request("DOCENTE", null, Set.of()));
        assertThatThrownBy(() -> service.update(first.id(), new UsuarioDtos.UpdateRequest(role("APODERADO"),
            first.correo(), EstadoAcceso.ACTIVO))).isInstanceOf(ConflictException.class);
    }
    @Test void rechazaPasswordTemporalDebil() {
        var r = request("ADMINISTRADOR", null, Set.of());
        var weak = new UsuarioDtos.ProvisionRequest(r.personaId(), r.persona(), r.rolId(), r.username(), r.correo(),
            "soloMinusculas", null, null, null, null, Set.of());
        assertThatThrownBy(() -> service.provision(weak)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void exigeExclusividadEntrePersonaNuevaYExistente() {
        var first = service.provision(request("DOCENTE", null, Set.of()));
        var r = request("APODERADO", null, Set.of());
        var ambiguous = new UsuarioDtos.ProvisionRequest(first.personaId(), r.persona(), r.rolId(), r.username(),
            r.correo(), r.password(), null, null, null, null, Set.of());
        assertThatThrownBy(() -> service.provision(ambiguous)).isInstanceOf(BusinessRuleException.class);
    }
}
