package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.RolRepository;
import com.asiscontrol.repository.SesionAccesoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:contexto_rol_auth;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false
        """)
@ActiveProfiles("test")
class ContextoRolHttpTest {

    private static final String TEMPORAL = "Temporal123!";
    private static final String PERSONAL = "MiClavePersonal456!";

    @Autowired private WebApplicationContext contexto;
    @Autowired private UsuarioService usuarios;
    @Autowired private RolRepository roles;
    @Autowired private AccesoUsuarioRolRepository asignaciones;
    @Autowired private SesionAccesoRepository sesiones;
    @Autowired private PlatformTransactionManager transacciones;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void cambiaEntreDocenteYApoderadoConLaMismaCuentaYAnulaElContextoAnterior() throws Exception {
        var cuenta = crear("DOCENTE");
        var otraAsignacion = usuarios.provision(solicitud("APODERADO", cuenta.personaId()));
        assertThat(otraAsignacion.id()).isEqualTo(cuenta.id());
        String inicial = completarCambio(cuenta.username());

        JsonNode seleccion = respuesta(ingresar(cuenta.username(), PERSONAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.rol").value(nullValue()))
                .andExpect(jsonPath("$.user.rolesDisponibles", containsInAnyOrder("APODERADO", "DOCENTE")))
                .andExpect(jsonPath("$.user.permisos").isEmpty()));
        String sinRol = token(seleccion);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(inicial)))
                .andExpect(status().isUnauthorized());
        consultarObligaciones(sinRol).andExpect(status().isForbidden());
        elegir(sinRol, "ADMINISTRADOR").andExpect(status().isForbidden());

        JsonNode docente = respuesta(elegir(sinRol, "DOCENTE").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.rol").value("DOCENTE")));
        consultarObligaciones(token(docente)).andExpect(status().isForbidden());

        JsonNode apoderado = respuesta(elegir(token(docente), "APODERADO").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.rol").value("APODERADO")));
        assertThat(apoderado.path("user").path("sesionId")).isEqualTo(docente.path("user").path("sesionId"));
        assertThat(apoderado.path("user").path("versionContexto").asLong())
                .isGreaterThan(docente.path("user").path("versionContexto").asLong());
        consultarObligaciones(token(docente)).andExpect(status().isUnauthorized());
        consultarObligaciones(token(apoderado)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(token(apoderado))))
                .andExpect(status().isForbidden());
    }

    @Test
    void asignacionUnicaSeActivaAlEntrarYRevocacionDejaLaCuentaSinModulos() throws Exception {
        var cuenta = crear("APODERADO");
        completarCambio(cuenta.username());
        JsonNode ingreso = respuesta(ingresar(cuenta.username(), PERSONAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.rol").value("APODERADO")));
        var rol = roles.findByNombreIgnoreCase("APODERADO").orElseThrow();

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            var asignacion = asignaciones.findByUsuarioIdAndRolId(cuenta.id(), rol.getId()).orElseThrow();
            asignacion.setActivo(false);
            sesiones.invalidarRolActivo(cuenta.id(), rol.getId());
        });

        consultarObligaciones(token(ingreso)).andExpect(status().isUnauthorized());
        String sinRol = token(respuesta(ingresar(cuenta.username(), PERSONAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.rol").value(nullValue()))
                .andExpect(jsonPath("$.user.rolesDisponibles").isEmpty())));
        elegir(sinRol, "APODERADO").andExpect(status().isForbidden());
    }

    private UsuarioDtos.Response crear(String rol) {
        return usuarios.provision(solicitud(rol, null));
    }

    private UsuarioDtos.ProvisionRequest solicitud(String rol, Long personaId) {
        String clave = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        var persona = new PersonaDtos.PersonaRequest("Persona", "Prueba", null,
                TipoDocumento.PASAPORTE, clave, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1990, 1, 1), null, clave + "@example.test", null);
        return new UsuarioDtos.ProvisionRequest(personaId, personaId == null ? persona : null,
                roles.findByNombreIgnoreCase(rol).orElseThrow().getId(), TEMPORAL,
                LocalDate.of(2018, 1, 1), Especialidad.MATEMATICA, Set.of());
    }

    private String completarCambio(String usuario) throws Exception {
        assertThat(usuario).matches("[0-9]{8}");
        String temporal = token(respuesta(ingresar(usuario, TEMPORAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.requiereCambioContrasena").value(true))
                .andExpect(jsonPath("$.user.rol").value(nullValue()))));
        elegir(temporal, "DOCENTE").andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, bearer(temporal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("currentPassword", TEMPORAL, "newPassword", PERSONAL))))
                .andExpect(status().isOk());
        return temporal;
    }

    private ResultActions ingresar(String usuario, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", usuario, "password", password))));
    }

    private ResultActions elegir(String token, String rol) throws Exception {
        return mvc.perform(put("/api/auth/rol-activo").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("rol", rol))));
    }

    private ResultActions consultarObligaciones(String token) throws Exception {
        return mvc.perform(get("/api/apoderado/obligaciones")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private JsonNode respuesta(ResultActions acciones) throws Exception {
        return json.readTree(acciones.andReturn().getResponse().getContentAsByteArray());
    }

    private String token(JsonNode respuesta) {
        return respuesta.path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
