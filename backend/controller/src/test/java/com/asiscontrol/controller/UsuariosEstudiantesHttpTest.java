package com.asiscontrol.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.repository.RolRepository;
import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuariosEstudiantesHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired RolRepository roles;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String key() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private PersonaDtos.PersonaRequest persona(String key) {
        return new PersonaDtos.PersonaRequest(
                "Maria",
                "Torres",
                "Mamani",
                TipoDocumento.PASAPORTE,
                key,
                null,
                Sexo.FEMENINO,
                LocalDate.of(2009, 3, 10),
                null,
                key + "@example.test",
                null);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expected)
            throws Exception {
        request.with(user("admin-prueba").roles("ADMINISTRADOR"));
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response =
                mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsByteArray().length == 0
                ? json.nullNode()
                : json.readTree(response.getContentAsByteArray());
    }

    @Test
    void crudDeEstudianteYApoderadoConVinculacion() throws Exception {
        String s = key(), g = key();
        var student =
                call(
                        post("/api/alumnos"),
                        new PersonaDtos.AlumnoCreateRequest(persona(s)),
                        201);
        long id = student.path("id").asLong();
        assertThat(student.path("persona").path("nombres").asText()).isEqualTo("Maria");
        call(get("/api/alumnos"), null, 200);
        call(get("/api/alumnos/{id}", id), null, 200);
        call(
                put("/api/alumnos/{id}", id),
                new PersonaDtos.AlumnoUpdateRequest(persona(s), "NUEVO" + s),
                400);
        call(
                put("/api/alumnos/{id}", id),
                new PersonaDtos.AlumnoUpdateRequest(
                        persona(s), student.path("codigoAlumno").asText()),
                200);
        var guardian =
                call(
                        post("/api/apoderados"),
                        new PersonaDtos.ApoderadoCreateRequest(persona(g)),
                        201);
        long gid = guardian.path("id").asLong();
        call(get("/api/apoderados"), null, 200);
        call(get("/api/apoderados/{id}", gid), null, 200);
        call(
                put("/api/apoderados/{id}", gid),
                new PersonaDtos.ApoderadoUpdateRequest(persona(g)),
                200);
        call(
                post("/api/alumnos/{id}/apoderados", id),
                new PersonaDtos.VinculoApoderadoRequest(gid, Parentesco.MADRE, true),
                201);
        call(delete("/api/alumnos/{id}/apoderados/{gid}", id, gid), null, 204);
        call(delete("/api/apoderados/{id}", gid), null, 204);
        call(delete("/api/alumnos/{id}", id), null, 204);
        call(get("/api/alumnos/{id}", id), null, 404);
    }

    @Test
    void administraAccesosConEstadosMotivoYContrasenaTemporal() throws Exception {
        String s = key();
        var alumno =
                call(
                        post("/api/alumnos"),
                        new PersonaDtos.AlumnoCreateRequest(persona(s)),
                        201);
        long role = roles.findByNombreIgnoreCase("ALUMNO").orElseThrow().getId();
        var credencial = alumno.path("credencialAcceso");
        assertThat(credencial.path("username").asText()).matches("[0-9]{8}");
        assertThat(credencial.path("temporaryPassword").asText()).isNotBlank();
        var account = call(get("/api/usuarios")
                .param("search", credencial.path("username").asText()), null, 200)
                .path("content").get(0);
        long id = account.path("id").asLong();
        String email = account.path("correo").asText();
        assertThat(email).isEqualTo(credencial.path("username").asText() + "@asiscontrol.edu.pe");
        assertThat(account.path("roles").get(0).path("nombre").asText()).isEqualTo("ALUMNO");
        assertThat(call(get("/api/alumnos/{id}", alumno.path("id").asLong()), null, 200)
                .path("credencialAcceso").isNull()).isTrue();
        call(get("/api/usuarios/personas"), null, 200);
        call(get("/api/usuarios/{id}", id), null, 200);
        call(
                put("/api/usuarios/{id}", id),
                new UsuarioDtos.UpdateRequest(
                        role, email, EstadoAcceso.DESACTIVADO, true, null, "Solicitud formal"),
                200);
        call(
                put("/api/usuarios/{id}", id),
                new UsuarioDtos.UpdateRequest(
                        role, email, EstadoAcceso.ACTIVO, true, "Temporal456!", "Reactivacion"),
                200);
        call(
                put("/api/usuarios/{id}/contrasena-temporal", id),
                new UsuarioDtos.ResetPasswordRequest("Temporal789!", true, "Identidad verificada"),
                204);
        call(delete("/api/usuarios/{id}", id), null, 422);
        var registro = call(post("/api/usuarios/administradores"),
                new UsuarioDtos.RegistroAdministradorRequest(
                        alumno.path("persona").path("id").asLong(), null), 201);
        assertThat(registro.path("id").asLong()).isEqualTo(id);
        assertThat(registro.path("contrasenaTemporal").isNull()).isTrue();
        var rolesAsignados = call(get("/api/usuarios/{id}", id), null, 200).path("roles");
        assertThat(rolesAsignados.size()).isEqualTo(2);

        var sinAlumno = call(put("/api/usuarios/{id}/roles/{rol}", id, role),
                new UsuarioDtos.CambiarRolRequest(false, "Cambio de responsabilidades"), 200);
        assertThat(sinAlumno.path("roles").get(1).path("activo").asBoolean()).isFalse();
    }

    @Test
    void exigeDocumentoAlGuardarUnaFichaNueva() throws Exception {
        var sinDocumento = new PersonaDtos.PersonaRequest(
                "Maria", "Torres", "Mamani", null, null, null, Sexo.FEMENINO,
                LocalDate.of(2009, 3, 10), null, null, null);
        call(post("/api/alumnos"), new PersonaDtos.AlumnoCreateRequest(sinDocumento), 400);
    }

    @Test
    void exponeCatalogosYRolesControladosYRechazaDatosInvalidos() throws Exception {
        call(get("/api/catalogos/enumeraciones"), null, 200);
        call(get("/api/catalogos/nacionalidades"), null, 200);
        call(
                post("/api/catalogos/nacionalidades"),
                new PersonaDtos.NacionalidadRequest("Nacionalidad " + key()),
                201);
        call(get("/api/roles"), null, 200);
        call(get("/api/roles/permisos"), null, 200);
        var request = new UsuarioDtos.RolRequest("ROL_EXTRA", "No permitido", Set.of());
        call(post("/api/roles"), request, 422);
        call(put("/api/roles/1"), request, 422);
        call(delete("/api/roles/1"), null, 422);
        call(post("/api/alumnos"), Map.of("codigoAlumno", "", "persona", Map.of()), 400);
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").with(user("alumno").roles("ALUMNO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void importaLaPlantillaDesdeMultipart() throws Exception {
        byte[] template =
                mvc.perform(
                                get("/api/alumnos/importaciones/plantilla")
                                        .with(user("admin-prueba").roles("ADMINISTRADOR")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        mvc.perform(
                        multipart("/api/alumnos/importaciones")
                                .file(
                                        new MockMultipartFile(
                                                "archivo", "alumnos.xlsx", null, template))
                                .with(user("admin-prueba").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(1));
    }
}
