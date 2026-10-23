package com.asiscontrol.controller;

import com.asiscontrol.config.BootstrapDataInitializer;
import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.RolRepository;
import com.asiscontrol.security.PasswordPolicy;
import com.asiscontrol.service.*;
import com.asiscontrol.util.HashUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:asiscontrol_dev1_seguridad;"
        + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@ActiveProfiles("test")
class AutenticacionSeguridadHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired UsuarioService usuarios;
    @Autowired AuthService auth;
    @Autowired RolRepository roles;
    @Autowired AccesoUsuarioRepository accesos;
    @Autowired SesionActividadService actividad;
    @Autowired PasswordEncoder encoder;
    @Autowired BootstrapDataInitializer initializer;
    @Autowired PlatformTransactionManager manager;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private UsuarioDtos.Response crear() {
        String clave = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        var persona = new PersonaDtos.PersonaRequest("Persona", "Seguridad", "Prueba",
                TipoDocumento.PASAPORTE, clave, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(2009, 1, 1), null, clave + "@example.test", null);
        return usuarios.provision(new UsuarioDtos.ProvisionRequest(null, persona,
                roles.findByNombreIgnoreCase("ALUMNO").orElseThrow().getId(), "usr" + clave,
                "login" + clave + "@example.test", "Temporal123!", "EST" + clave,
                null, null, null, Set.of()));
    }

    private JsonNode respuesta(MockHttpServletRequestBuilder request, Object body, int status)
            throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body));
        var result = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
        return result.getContentAsByteArray().length == 0
                ? json.nullNode() : json.readTree(result.getContentAsByteArray());
    }

    @Test
    void identificadorInexistenteYClaveIncorrectaDevuelvenElMismoErrorSinFalloInterno()
            throws Exception {
        var acceso = crear();
        var desconocido = respuesta(post("/api/auth/login"),
                new AuthDtos.LoginRequest("inexistente" + UUID.randomUUID(), "Incorrecta123!"), 401);
        var incorrecto = respuesta(post("/api/auth/login"),
                new AuthDtos.LoginRequest(acceso.username(), "Incorrecta123!"), 401);
        assertThat(desconocido.path("message")).isEqualTo(incorrecto.path("message"));
        assertThat(accesos.findById(acceso.id()).orElseThrow().getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void restablecimientoGeneraTemporalRevocaSesionesYNoExponeLaClaveEnConsultas()
            throws Exception {
        var acceso = crear();
        var anterior = auth.login(new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"));
        var request = new UsuarioDtos.ResetPasswordRequest(true, "Verificacion presencial");
        var result = mvc.perform(put("/api/usuarios/{id}/contrasena-temporal", acceso.id())
                        .with(user("administrador").roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        var restablecido = json.readTree(result.getContentAsByteArray());
        String temporal = restablecido.path("temporaryPassword").asText();
        assertThat(temporal).hasSize(20).isNotEqualTo("Temporal123!");
        assertThatCode(() -> PasswordPolicy.validate(temporal, acceso.username())).doesNotThrowAnyException();
        var guardado = accesos.findById(acceso.id()).orElseThrow();
        assertThat(encoder.matches(temporal, guardado.getContrasenaHash())).isTrue();
        assertThat(actividad.validar(HashUtils.sha256(anterior.accessToken()))).isFalse();
        respuesta(post("/api/auth/login"), new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"), 401);
        var nuevo = respuesta(post("/api/auth/login"), new AuthDtos.LoginRequest(acceso.username(), temporal), 200);
        assertThat(nuevo.path("user").path("requiereCambioContrasena").asBoolean()).isTrue();
        var detalle = respuesta(get("/api/usuarios/{id}", acceso.id())
                .with(user("administrador").roles("ADMINISTRADOR")), null, 200);
        assertThat(detalle.has("temporaryPassword")).isFalse();
        assertThat(detalle.has("contrasenaHash")).isFalse();
        assertThat(new UsuarioDtos.ResetPasswordResponse(acceso.id(), temporal,
                guardado.getContrasenaTemporalExpira()).toString()).doesNotContain(temporal);
    }

    @Test
    void restablecimientoExigeAdministradorIdentidadYMotivo() throws Exception {
        var acceso = crear();
        var ruta = "/api/usuarios/" + acceso.id() + "/contrasena-temporal";
        var valido = new UsuarioDtos.ResetPasswordRequest(true, "Verificacion presencial");
        respuesta(put(ruta), valido, 401);
        respuesta(put(ruta).with(user("alumno").roles("ALUMNO")), valido, 403);
        respuesta(put(ruta).with(user("admin").roles("ADMINISTRADOR")),
                new UsuarioDtos.ResetPasswordRequest(false, "Sin verificar"), 400);
        respuesta(put(ruta).with(user("admin").roles("ADMINISTRADOR")),
                new UsuarioDtos.ResetPasswordRequest(true, ""), 400);
        assertThat(encoder.matches("Temporal123!",
                accesos.findById(acceso.id()).orElseThrow().getContrasenaHash())).isTrue();
    }

    @Test
    void cambioObligatorioValidaConfirmacionYRevocaLaTemporalYSuSesion() throws Exception {
        var acceso = crear();
        var session = auth.login(new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"));
        String bearer = "Bearer " + session.accessToken();
        var bloqueado = respuesta(get("/api/alumnos").header("Authorization", bearer), null, 403);
        assertThat(bloqueado.path("code").asText()).isEqualTo("CAMBIO_CONTRASENA_REQUERIDO");
        respuesta(post("/api/auth/change-password").header("Authorization", bearer),
                Map.of("currentPassword", "Temporal123!", "newPassword", "Definitiva456!"), 400);
        respuesta(post("/api/auth/change-password").header("Authorization", bearer),
                new AuthDtos.ChangePasswordRequest("Temporal123!", "Definitiva456!", "Diferente789!"), 422);
        respuesta(post("/api/auth/change-password").header("Authorization", bearer),
                new AuthDtos.ChangePasswordRequest("Temporal123!", "debilclave", "debilclave"), 422);
        assertThat(accesos.findById(acceso.id()).orElseThrow().isRequiereCambioContrasena()).isTrue();
        respuesta(post("/api/auth/change-password").header("Authorization", bearer),
                new AuthDtos.ChangePasswordRequest("Temporal123!", "Definitiva456!", "Definitiva456!"), 200);
        respuesta(get("/api/auth/me").header("Authorization", bearer), null, 401);
        respuesta(post("/api/auth/login"), new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"), 401);
        var nuevo = respuesta(post("/api/auth/login"),
                new AuthDtos.LoginRequest(acceso.username(), "Definitiva456!"), 200);
        assertThat(nuevo.path("user").path("requiereCambioContrasena").asBoolean()).isFalse();
        respuesta(get("/api/roles").header("Authorization", "Bearer " + nuevo.path("accessToken").asText()), null, 403);
    }

    @Test
    void accesoDesactivadoNoSeReactivaAlIntentarIngresar() throws Exception {
        var acceso = crear();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var guardado = accesos.findById(acceso.id()).orElseThrow();
            guardado.setEstado(EstadoAcceso.DESACTIVADO);
            accesos.save(guardado);
        });
        for (int intento = 0; intento < 6; intento++) {
            respuesta(post("/api/auth/login"),
                    new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"), 401);
        }
        var guardado = accesos.findById(acceso.id()).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoAcceso.DESACTIVADO);
        assertThat(guardado.getIntentosFallidos()).isZero();
        assertThat(guardado.getBloqueadoHasta()).isNull();
    }

    @Test
    void inicializacionConservaHistoriaPeroDeshabilitaRolesAjenosALaMatriz() throws Exception {
        var acceso = crear();
        var session = auth.login(new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            Rol antiguo = roles.findByNombreIgnoreCase("DIRECTOR").orElseGet(Rol::new);
            antiguo.setNombre("DIRECTOR");
            antiguo.setActivo(true);
            roles.save(antiguo);
            var guardado = accesos.findById(acceso.id()).orElseThrow();
            guardado.setRol(antiguo);
            accesos.save(guardado);
        });
        initializer.run(null);
        assertThat(roles.findAllByActivoTrueOrderByNombreAsc()).extracting(Rol::getNombre)
                .containsExactlyInAnyOrder("ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO");
        assertThat(roles.findByNombreIgnoreCase("DIRECTOR").orElseThrow().isActivo()).isFalse();
        assertThat(accesos.findById(acceso.id())).isPresent();
        assertThat(actividad.validar(HashUtils.sha256(session.accessToken()))).isFalse();
        respuesta(post("/api/auth/login"), new AuthDtos.LoginRequest(acceso.username(), "Temporal123!"), 401);
    }
}
