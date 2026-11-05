package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.PersonaRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/** Un documento ya registrado reutiliza a la persona al darle otro perfil de docente o apoderado. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReutilizacionPersonaIntegrationTest {

    private static final LocalDate NACIMIENTO = LocalDate.of(1985, 3, 14);

    @Autowired private PersonaService personas;
    @Autowired private PersonaRepository personaRepository;

    @Test
    void unaDocenteRegistradaSeReutilizaComoApoderadaAunqueVarienTildesYEspacios() {
        String dni = dni();
        var docente = personas.createDocente(docente(persona(dni, "María José", "Quispe", NACIMIENTO)));
        long personasAntes = personaRepository.count();

        var apoderada =
                personas.createApoderado(
                        new PersonaDtos.ApoderadoCreateRequest(
                                persona(dni, "  maria   jose ", "QUISPE", NACIMIENTO)));

        assertThat(apoderada.persona().id()).isEqualTo(docente.persona().id());
        assertThat(apoderada.persona().nombres()).isEqualTo("María José");
        assertThat(personaRepository.count()).isEqualTo(personasAntes);
    }

    @Test
    void unApoderadoRegistradoSeReutilizaComoDocente() {
        String dni = dni();
        var apoderado =
                personas.createApoderado(
                        new PersonaDtos.ApoderadoCreateRequest(
                                persona(dni, "Luis", "Rojas", NACIMIENTO)));

        var docente = personas.createDocente(docente(persona(dni, "Luis", "Rojas", NACIMIENTO)));

        assertThat(docente.persona().id()).isEqualTo(apoderado.persona().id());
    }

    @Test
    void elMismoDocumentoConOtraIdentidadSeRechaza() {
        String dni = dni();
        personas.createDocente(docente(persona(dni, "Ana", "Flores", NACIMIENTO)));

        assertConflict(
                () ->
                        personas.createApoderado(
                                new PersonaDtos.ApoderadoCreateRequest(
                                        persona(dni, "Ana", "Flores", NACIMIENTO.plusDays(1)))),
                "DOCUMENTO_CON_OTROS_DATOS");
        assertConflict(
                () ->
                        personas.createApoderado(
                                new PersonaDtos.ApoderadoCreateRequest(
                                        persona(dni, "Ana", "Vega", NACIMIENTO))),
                "DOCUMENTO_CON_OTROS_DATOS");
    }

    @Test
    void noSeRepiteElMismoPerfilParaUnaPersona() {
        String dni = dni();
        var request =
                new PersonaDtos.ApoderadoCreateRequest(persona(dni, "Rosa", "Pizarro", NACIMIENTO));
        personas.createApoderado(request);

        assertConflict(() -> personas.createApoderado(request), "PERFIL_DUPLICADO");
    }

    @Test
    void elDocumentoDeUnEstudianteNoSeReutiliza() {
        String dni = dni();
        var persona = persona(dni, "Mateo", "Ramos", LocalDate.of(2012, 5, 2));
        personas.createAlumno(new PersonaDtos.AlumnoCreateRequest(persona, "EST" + dni));

        assertConflict(
                () -> personas.createApoderado(new PersonaDtos.ApoderadoCreateRequest(persona)),
                "DOCUMENTO_DE_ESTUDIANTE");
    }

    @Test
    void unaPersonaInhabilitadaNoRecibeOtroPerfil() {
        String dni = dni();
        var docente = personas.createDocente(docente(persona(dni, "Gary", "Raiser", NACIMIENTO)));
        var registrada = personaRepository.findById(docente.persona().id()).orElseThrow();
        registrada.setActivo(false);
        personaRepository.saveAndFlush(registrada);

        assertConflict(
                () ->
                        personas.createApoderado(
                                new PersonaDtos.ApoderadoCreateRequest(
                                        persona(dni, "Gary", "Raiser", NACIMIENTO))),
                "PERSONA_INHABILITADA");
    }

    private void assertConflict(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        conflict -> assertThat(conflict.getCode()).isEqualTo(code));
    }

    private PersonaDtos.DocenteCreateRequest docente(PersonaDtos.PersonaRequest persona) {
        return new PersonaDtos.DocenteCreateRequest(
                persona, "DOC" + persona.numeroDocumento(), LocalDate.now(), Especialidad.TUTORIA);
    }

    private PersonaDtos.PersonaRequest persona(
            String dni, String nombres, String apellido, LocalDate nacimiento) {
        return new PersonaDtos.PersonaRequest(
                nombres,
                apellido,
                null,
                TipoDocumento.DNI,
                dni,
                null,
                Sexo.PREFIERE_NO_INDICAR,
                nacimiento,
                null,
                dni + "@example.test",
                null);
    }

    /** DNI de 8 dígitos distinto en cada prueba. */
    private String dni() {
        return String.valueOf(10_000_000 + Math.floorMod(UUID.randomUUID().hashCode(), 89_999_999));
    }
}
