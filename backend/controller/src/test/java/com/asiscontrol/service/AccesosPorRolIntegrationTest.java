package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.*;
import org.assertj.core.api.Assertions;
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
    @Autowired AccesoUsuarioRolRepository asignaciones;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
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
        return new UsuarioDtos.ProvisionRequest(id, id == null ? person(k) : null, role(rol), "Temporal123!",
            LocalDate.now(), Especialidad.MATEMATICA, studentIds);
    }

    @Test void reutilizaCuentaYContrasenaAlCrearUnSegundoPerfil() {
        var identidad = person(key());
        var docente = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                identidad, LocalDate.now(), Especialidad.MATEMATICA));
        var primero = docente.credencialAcceso();
        var apoderado = personas.createApoderado(new PersonaDtos.ApoderadoCreateRequest(identidad));
        var despues = apoderado.credencialAcceso();

        assertThat(docente.persona().id()).isEqualTo(apoderado.persona().id());
        assertThat(primero.username()).matches("[0-9]{8}");
        assertThat(primero.temporaryPassword()).isNotBlank();
        assertThat(despues.username()).isEqualTo(primero.username());
        assertThat(despues.email()).isEqualTo(primero.email());
        assertThat(despues.temporaryPassword()).isNull();
        assertThat(primero.code()).matches("DOC[0-9]{8}");
        assertThat(despues.code()).matches("APO[0-9]{8}");
        var cuenta = accesoRepo.findByPersonaId(docente.persona().id()).orElseThrow();
        assertThat(encoder.matches(primero.temporaryPassword(), cuenta.getContrasenaHash())).isTrue();
        assertThat(cuenta.isRequiereCambioContrasena()).isTrue();
        assertThat(asignaciones.findAllByUsuarioId(cuenta.getId()))
                .extracting(asignacion -> asignacion.getRol().getNombre())
                .containsExactlyInAnyOrder("DOCENTE", "APODERADO");
    }

    @Test void registrarDosVecesAdministradorConservaCuentaYTemporal() {
        var primero = service.registrarAdministrador(
                new UsuarioDtos.RegistroAdministradorRequest(null, person(key())));
        var personaId = service.get(primero.id()).personaId();
        var segundo = service.registrarAdministrador(
                new UsuarioDtos.RegistroAdministradorRequest(personaId, null));
        assertThat(segundo.id()).isEqualTo(primero.id());
        assertThat(segundo.contrasenaTemporal()).isNull();
        assertThat(service.get(primero.id()).roles()).hasSize(1);
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
                new PersonaDtos.AlumnoCreateRequest(studentPerson));
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
    @Test void revocarRolDeDocenteNoAfectaElAccesoDeApoderado() {
        var primero = service.provision(request("DOCENTE", null, Set.of()));
        var ambos = service.provision(request("APODERADO", primero.personaId(), Set.of()));
        var resultado = service.cambiarRol(ambos.id(), role("DOCENTE"),
                new UsuarioDtos.CambiarRolRequest(false, "Fin de la asignación docente"));
        assertThat(resultado.estado()).isEqualTo(EstadoAcceso.ACTIVO);
        assertThat(resultado.roles()).extracting(UsuarioDtos.RolAsignadoResponse::nombre,
                UsuarioDtos.RolAsignadoResponse::activo)
                .containsExactlyInAnyOrder(Assertions.tuple("APODERADO", true),
                        Assertions.tuple("DOCENTE", false));
        assertThat(docenteRepo.findByPersonaIdAndActivoTrue(primero.personaId())).isPresent();
    }

    @Test void rechazaPasswordTemporalDebil() {
        var r = request("ADMINISTRADOR", null, Set.of());
        var weak = new UsuarioDtos.ProvisionRequest(r.personaId(), r.persona(), r.rolId(),
            "soloMinusculas", null, null, Set.of());
        assertThatThrownBy(() -> service.provision(weak)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void exigeExclusividadEntrePersonaNuevaYExistente() {
        var first = service.provision(request("DOCENTE", null, Set.of()));
        var r = request("APODERADO", null, Set.of());
        var ambiguous = new UsuarioDtos.ProvisionRequest(first.personaId(), r.persona(), r.rolId(), r.password(), null, null, Set.of());
        assertThatThrownBy(() -> service.provision(ambiguous)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void temporalUnicaTieneLaVigenciaConfigurada() {
        var antes = java.time.Instant.now();
        var acceso = service.registrarAdministrador(
                new UsuarioDtos.RegistroAdministradorRequest(null, person(key())));
        assertThat(acceso.username()).matches("[0-9]{8}");
        assertThat(acceso.correo()).isEqualTo(acceso.username() + "@asiscontrol.edu.pe");
        assertThat(acceso.contrasenaTemporal()).isNotBlank();
        assertThat(acceso.contrasenaTemporalExpira())
                .isBetween(antes.plus(java.time.Duration.ofDays(14)).minusSeconds(5),
                        java.time.Instant.now().plus(java.time.Duration.ofDays(14)));
    }
}
