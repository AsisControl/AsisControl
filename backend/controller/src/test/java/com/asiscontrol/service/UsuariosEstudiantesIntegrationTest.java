package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.RolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuariosEstudiantesIntegrationTest {

    @Autowired private PersonaService personas;
    @Autowired private PerfilAccesoService perfiles;
    @Autowired private UsuarioService usuarios;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private ApoderadoRepository apoderadoRepository;
    @Autowired private AlumnoApoderadoRepository vinculoRepository;
    @Autowired private AccesoUsuarioRepository accesoRepository;
    @Autowired private RolRepository rolRepository;

    @Test
    void gestionaLaFichaCompletaDelEstudiante() {
        String key = key();
        var creado = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key), " est" + key + " "));

        assertThat(creado.codigoAlumno()).isEqualTo(("EST" + key).toUpperCase());
        assertThat(personas.getAlumno(creado.id()).persona().numeroDocumento())
                .isEqualTo(key.toUpperCase());
        assertThat(personas.listAlumnos("Prueba", 0, 20, "codigoAlumno", Sort.Direction.ASC)
                .content()).extracting(PersonaDtos.AlumnoResponse::id).contains(creado.id());

        var actualizado = personas.updateAlumno(
                creado.id(),
                new PersonaDtos.AlumnoUpdateRequest(persona(key, "Actualizada"), "ALU" + key));

        assertThat(actualizado.codigoAlumno()).isEqualTo(("ALU" + key).toUpperCase());
        assertThat(actualizado.persona().nombres()).isEqualTo("Actualizada");

        personas.deleteAlumno(creado.id());

        assertThat(alumnoRepository.findByIdAndActivoTrue(creado.id())).isEmpty();
        assertThat(personaRepository.findById(creado.persona().id()).orElseThrow().isActivo())
                .isFalse();
        assertThatThrownBy(() -> personas.getAlumno(creado.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rechazaDuplicadosDeEstudianteYPersona() {
        String key = key();
        personas.createAlumno(new PersonaDtos.AlumnoCreateRequest(persona(key), "EST" + key));

        assertThatThrownBy(() -> personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key() + "x"), "EST" + key)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(persona(key))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void gestionaApoderadosYUnSoloVinculoPrincipal() {
        var alumno = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "EST" + key()));
        var primerApoderado = personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(persona(key())));
        var segundoApoderado = personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(persona(key())));

        var primerVinculo = personas.linkGuardian(
                alumno.id(),
                new PersonaDtos.VinculoApoderadoRequest(
                        primerApoderado.id(), Parentesco.MADRE, true));
        var segundoVinculo = personas.linkGuardian(
                alumno.id(),
                new PersonaDtos.VinculoApoderadoRequest(segundoApoderado.id(), null, true));

        AlumnoApoderado primero = vinculoRepository.findById(primerVinculo.id()).orElseThrow();
        AlumnoApoderado segundo = vinculoRepository.findById(segundoVinculo.id()).orElseThrow();
        assertThat(primero.isPrincipal()).isFalse();
        assertThat(segundo.isPrincipal()).isTrue();
        assertThat(segundo.getParentesco()).isEqualTo(Parentesco.NO_DECLARADO);
        assertThat(personas.getAlumno(alumno.id()).apoderados()).hasSize(2);
        assertThat(personas.getApoderado(segundoApoderado.id()).alumnos()).hasSize(1);

        assertThatThrownBy(() -> personas.unlinkGuardian(alumno.id(), segundoApoderado.id()))
                .isInstanceOf(BusinessRuleException.class);
        personas.unlinkGuardian(alumno.id(), primerApoderado.id());
        personas.unlinkGuardian(alumno.id(), segundoApoderado.id());

        assertThat(personas.getAlumno(alumno.id()).apoderados()).isEmpty();
    }

    @Test
    void rechazaVinculoDuplicadoYApoderadoInexistente() {
        var alumno = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "EST" + key()));
        var apoderado = personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(persona(key())));
        var request = new PersonaDtos.VinculoApoderadoRequest(
                apoderado.id(), Parentesco.PADRE, false);

        personas.linkGuardian(alumno.id(), request);

        assertThatThrownBy(() -> personas.linkGuardian(alumno.id(), request))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> personas.unlinkGuardian(alumno.id(), Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void permiteReactivarUnVinculoPreviamenteRetirado() {
        var alumno = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "EST" + key()));
        var apoderado = personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(persona(key())));
        var inicial = personas.linkGuardian(
                alumno.id(),
                new PersonaDtos.VinculoApoderadoRequest(
                        apoderado.id(), Parentesco.PADRE, false));

        personas.unlinkGuardian(alumno.id(), apoderado.id());
        var reactivado = personas.linkGuardian(
                alumno.id(),
                new PersonaDtos.VinculoApoderadoRequest(
                        apoderado.id(), Parentesco.TUTOR_LEGAL, true));

        assertThat(reactivado.id()).isEqualTo(inicial.id());
        assertThat(reactivado.parentesco()).isEqualTo(Parentesco.TUTOR_LEGAL);
        assertThat(reactivado.principal()).isTrue();
        assertThat(vinculoRepository.findAllByAlumnoIdAndActivoTrue(alumno.id())).hasSize(1);
    }

    @Test
    void actualizaYDesactivaApoderadoConSusVinculos() {
        var alumno = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "EST" + key()));
        String guardianKey = key();
        var apoderado = personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(persona(guardianKey)));
        personas.linkGuardian(
                alumno.id(),
                new PersonaDtos.VinculoApoderadoRequest(
                        apoderado.id(), Parentesco.TUTOR_LEGAL, false));

        var actualizado = personas.updateApoderado(
                apoderado.id(),
                new PersonaDtos.ApoderadoUpdateRequest(persona(guardianKey, "Tutor")));
        assertThat(actualizado.persona().nombres()).isEqualTo("Tutor");
        assertThat(personas.listApoderados("Tutor", 0, 20, "fechaCreacion", Sort.Direction.DESC)
                .content()).extracting(PersonaDtos.ApoderadoResponse::id).contains(apoderado.id());

        personas.deleteApoderado(apoderado.id());

        assertThat(apoderadoRepository.findByIdAndActivoTrue(apoderado.id())).isEmpty();
        assertThat(vinculoRepository.findAllByApoderadoIdAndActivoTrue(apoderado.id())).isEmpty();
    }

    @Test
    void gestionaCatalogoDeNacionalidades() {
        String nombre = "Nacionalidad " + key();
        var creada = personas.createNationality(new PersonaDtos.NacionalidadRequest(nombre));

        assertThat(personas.listNationalities())
                .extracting(PersonaDtos.NacionalidadResponse::id)
                .contains(creada.id());
        assertThatThrownBy(() -> personas.createNationality(
                new PersonaDtos.NacionalidadRequest("  " + nombre.toLowerCase() + "  ")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void gestionaLaFichaCompletaDelDocenteYSusValidaciones() {
        String key = key();
        var creado = personas.createDocente(
                new PersonaDtos.DocenteCreateRequest(
                        persona(key), " doc" + key + " ", LocalDate.now().minusYears(1),
                        Especialidad.MATEMATICA));

        assertThat(creado.codigoDocente()).isEqualTo(("DOC" + key).toUpperCase());
        assertThat(personas.getDocente(creado.id()).especialidad())
                .isEqualTo(Especialidad.MATEMATICA);
        assertThat(personas.listDocentes(
                        "Prueba", Especialidad.MATEMATICA, 0, 20,
                        "codigoDocente", Sort.Direction.ASC)
                .content()).extracting(PersonaDtos.DocenteResponse::id).contains(creado.id());

        var actualizado = personas.updateDocente(
                creado.id(),
                new PersonaDtos.DocenteUpdateRequest(
                        persona(key, "Docente actualizado"), "PROF" + key,
                        LocalDate.now().minusMonths(6), Especialidad.INGLES));
        assertThat(actualizado.codigoDocente()).isEqualTo(("PROF" + key).toUpperCase());
        assertThat(actualizado.especialidad()).isEqualTo(Especialidad.INGLES);

        assertThatThrownBy(() -> personas.createDocente(
                new PersonaDtos.DocenteCreateRequest(
                        persona(key()), "FUT" + key(), LocalDate.now().plusDays(1),
                        Especialidad.TUTORIA)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> personas.createDocente(
                new PersonaDtos.DocenteCreateRequest(
                        persona(key()), actualizado.codigoDocente(), LocalDate.now(),
                        Especialidad.TUTORIA)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> personas.updateDocente(
                creado.id(),
                new PersonaDtos.DocenteUpdateRequest(
                        persona(key), actualizado.codigoDocente(), LocalDate.now().plusDays(1),
                        Especialidad.TUTORIA)))
                .isInstanceOf(BusinessRuleException.class);

        personas.deleteDocente(creado.id());
        assertThatThrownBy(() -> personas.getDocente(creado.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validaNacionalidadYCorreoDuplicadoAlActualizarPersonas() {
        var nacionalidad = personas.createNationality(
                new PersonaDtos.NacionalidadRequest("Nacionalidad " + key()));
        String firstKey = key();
        var primero = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(
                        persona(firstKey, "Primero", nacionalidad.id()), "EST" + firstKey));
        String secondKey = key();
        var segundo = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(secondKey), "EST" + secondKey));

        assertThat(primero.persona().nacionalidadId()).isEqualTo(nacionalidad.id());
        assertThat(primero.persona().nacionalidad()).isEqualTo(nacionalidad.nombre());
        assertThatThrownBy(() -> personas.updateAlumno(
                segundo.id(),
                new PersonaDtos.AlumnoUpdateRequest(
                        personaConCorreo(secondKey, "Segundo", primero.persona().correo()),
                        segundo.codigoAlumno())))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> personas.createApoderado(
                new PersonaDtos.ApoderadoCreateRequest(
                        persona(key(), "Sin nacionalidad", Long.MAX_VALUE))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gestionaElCicloDeVidaDelAccesoInstitucional() {
        var alumno = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "EST" + key()));
        long roleId = role("ALUMNO");
        String key = key();
        var acceso = usuarios.provision(new UsuarioDtos.ProvisionRequest(
                alumno.persona().id(), null, roleId, "usr" + key,
                "cuenta" + key + "@example.test", "Temporal123!",
                null, null, null, null, Set.of()));

        assertThat(usuarios.get(acceso.id()).estado()).isEqualTo(EstadoAcceso.ACTIVO);
        assertThat(usuarios.list("usr" + key, EstadoAcceso.ACTIVO, roleId, 0, 20,
                "username", Sort.Direction.ASC).content()).hasSize(1);
        assertThat(usuarios.listPersonas(alumno.persona().numeroDocumento(), 0, 20).content())
                .anySatisfy(p -> {
                    assertThat(p.perfiles()).contains("ALUMNO");
                    assertThat(p.rolesConAcceso()).contains("ALUMNO");
                });

        var desactivado = usuarios.update(
                acceso.id(),
                new UsuarioDtos.UpdateRequest(roleId, acceso.correo(),
                        EstadoAcceso.DESACTIVADO, true, null, "Solicitud institucional"));
        assertThat(desactivado.estado()).isEqualTo(EstadoAcceso.DESACTIVADO);

        var reactivado = usuarios.update(
                acceso.id(),
                new UsuarioDtos.UpdateRequest(roleId, "nuevo" + key + "@example.test",
                        EstadoAcceso.ACTIVO, true, "Temporal456!", "Reactivacion autorizada"));
        assertThat(reactivado.estado()).isEqualTo(EstadoAcceso.ACTIVO);
        assertThat(reactivado.correo()).startsWith("nuevo");
        assertThat(reactivado.requiereCambioContrasena()).isTrue();

        usuarios.resetPassword(
                acceso.id(),
                new UsuarioDtos.ResetPasswordRequest(
                        true, "Identidad verificada presencialmente"));
        AccesoUsuario persistido = accesoRepository.findById(acceso.id()).orElseThrow();
        assertThat(persistido.isRequiereCambioContrasena()).isTrue();
        assertThat(persistido.getContrasenaTemporalExpira()).isNotNull();
        assertThatThrownBy(() -> usuarios.delete(acceso.id()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void exigeMotivoIdentidadYRolInmutableAlAdministrarAccesos() {
        var alumno = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "EST" + key()));
        long studentRole = role("ALUMNO");
        String key = key();
        var acceso = usuarios.provision(new UsuarioDtos.ProvisionRequest(
                alumno.persona().id(), null, studentRole, "usr" + key,
                "cuenta" + key + "@example.test", "Temporal123!",
                null, null, null, null, Set.of()));

        assertThatThrownBy(() -> usuarios.update(
                acceso.id(),
                new UsuarioDtos.UpdateRequest(studentRole, acceso.correo(),
                        EstadoAcceso.DESACTIVADO, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> usuarios.update(
                acceso.id(),
                new UsuarioDtos.UpdateRequest(role("APODERADO"), acceso.correo(),
                        EstadoAcceso.ACTIVO, null, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> usuarios.resetPassword(
                acceso.id(), new UsuarioDtos.ResetPasswordRequest(
                        false, "No verificada")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void exponeSoloRolesInstitucionalesYLaMatrizEsControlada() {
        assertThat(usuarios.listRoles())
                .extracting(UsuarioDtos.RolResponse::nombre)
                .containsExactlyInAnyOrder("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO");
        assertThat(usuarios.listPermissions()).isNotEmpty();
        assertThatThrownBy(() -> usuarios.createRole(null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> usuarios.updateRole(1L, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> usuarios.deleteRole(1L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aprovisionaLosCuatroPerfilesInstitucionales() {
        var alumnoBase = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(key()), "BASE" + key()));

        String adminKey = key();
        var admin = usuarios.provision(provision(
                persona(adminKey), role("ADMINISTRADOR"), adminKey,
                null, null, null, null, Set.of()));
        String studentKey = key();
        var alumno = usuarios.provision(provision(
                persona(studentKey), role("ALUMNO"), studentKey,
                "ALU" + studentKey, null, null, null, Set.of()));
        String teacherKey = key();
        var docente = usuarios.provision(provision(
                persona(teacherKey), role("DOCENTE"), teacherKey,
                null, "DOC" + teacherKey, LocalDate.now(), Especialidad.COMUNICACION,
                Set.of()));
        String guardianKey = key();
        var apoderado = usuarios.provision(provision(
                persona(guardianKey), role("APODERADO"), guardianKey,
                null, null, null, null, Set.of(alumnoBase.id())));

        assertThat(admin.rol()).isEqualTo("ADMINISTRADOR");
        assertThat(alumno.rol()).isEqualTo("ALUMNO");
        assertThat(docente.rol()).isEqualTo("DOCENTE");
        assertThat(apoderado.rol()).isEqualTo("APODERADO");
        assertThat(vinculoRepository.findAllByAlumnoIdAndActivoTrue(alumnoBase.id())).hasSize(1);
        assertThat(perfiles.hasProfile(admin.personaId(), "ROL_DESCONOCIDO")).isFalse();
    }

    @Test
    void validaDatosAntesDeCrearPerfilesYListados() {
        assertThatThrownBy(() -> usuarios.listPersonas(null, -1, 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> usuarios.provision(new UsuarioDtos.ProvisionRequest(
                null, null, role("ALUMNO"), "usr" + key(),
                "cuenta" + key() + "@example.test", "Temporal123!",
                "ALU" + key(), null, null, null, Set.of())))
                .isInstanceOf(BusinessRuleException.class);

        String existingKey = key();
        var existing = personas.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(persona(existingKey), "EST" + existingKey));
        assertThatThrownBy(() -> usuarios.provision(new UsuarioDtos.ProvisionRequest(
                existing.persona().id(), persona(key()), role("ALUMNO"), "usr" + key(),
                "cuenta" + key() + "@example.test", "Temporal123!",
                "ALU" + key(), null, null, null, Set.of())))
                .isInstanceOf(BusinessRuleException.class);

        String teacherKey = key();
        assertThatThrownBy(() -> usuarios.provision(provision(
                persona(teacherKey), role("DOCENTE"), teacherKey,
                null, "DOC" + teacherKey, LocalDate.now().plusDays(1), null, Set.of())))
                .isInstanceOf(BusinessRuleException.class);

        String adminKey = key();
        assertThatThrownBy(() -> usuarios.provision(provision(
                persona(adminKey), role("ADMINISTRADOR"), adminKey,
                null, null, null, null, Set.of(existing.id()))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reportaRecursosInexistentesEnLugarDeFallarConNulos() {
        assertThatThrownBy(() -> personas.getAlumno(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> personas.getApoderado(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> personas.getDocente(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> usuarios.get(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retirarApoderadoConservaElPerfilYAccesoDeDocenteDeLaMismaPersona() {
        String teacherKey = key();
        var docente = usuarios.provision(provision(
                persona(teacherKey), role("DOCENTE"), teacherKey,
                null, "DOC" + teacherKey, LocalDate.now(), Especialidad.MATEMATICA, Set.of()));
        String guardianKey = key();
        var apoderado = usuarios.provision(new UsuarioDtos.ProvisionRequest(
                docente.personaId(), null, role("APODERADO"), "usr" + guardianKey,
                "cuenta" + guardianKey + "@example.test", "Temporal123!",
                null, null, null, null, Set.of()));
        var perfil = apoderadoRepository.findByPersonaIdAndActivoTrue(docente.personaId()).orElseThrow();

        personas.deleteApoderado(perfil.getId());

        assertThat(personaRepository.findById(docente.personaId()).orElseThrow().isActivo()).isTrue();
        assertThat(accesoRepository.findById(docente.id()).orElseThrow().getEstado())
                .isEqualTo(EstadoAcceso.ACTIVO);
        assertThat(accesoRepository.findById(apoderado.id()).orElseThrow().getEstado())
                .isEqualTo(EstadoAcceso.DESACTIVADO);
    }

    private long role(String name) {
        return rolRepository.findByNombreIgnoreCase(name).orElseThrow().getId();
    }

    private UsuarioDtos.ProvisionRequest provision(
            PersonaDtos.PersonaRequest persona,
            long roleId,
            String key,
            String codigoAlumno,
            String codigoDocente,
            LocalDate fechaIngreso,
            Especialidad especialidad,
            Set<Long> alumnoIds) {
        return new UsuarioDtos.ProvisionRequest(
                null,
                persona,
                roleId,
                "usr" + key,
                "cuenta" + key + "@example.test",
                "Temporal123!",
                codigoAlumno,
                codigoDocente,
                fechaIngreso,
                especialidad,
                alumnoIds);
    }

    private String key() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private PersonaDtos.PersonaRequest persona(String key) {
        return persona(key, "Persona");
    }

    private PersonaDtos.PersonaRequest persona(String key, String nombres) {
        return persona(key, nombres, null);
    }

    private PersonaDtos.PersonaRequest persona(String key, String nombres, Long nacionalidadId) {
        return new PersonaDtos.PersonaRequest(
                nombres,
                "Prueba",
                "Sistema",
                TipoDocumento.PASAPORTE,
                key,
                nacionalidadId,
                Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(2008, 1, 1),
                "+51 999 999 999",
                key + "@example.test",
                "Direccion de prueba");
    }

    private PersonaDtos.PersonaRequest personaConCorreo(
            String key, String nombres, String correo) {
        return new PersonaDtos.PersonaRequest(
                nombres,
                "Prueba",
                "Sistema",
                TipoDocumento.PASAPORTE,
                key,
                null,
                Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(2008, 1, 1),
                null,
                correo,
                null);
    }
}
