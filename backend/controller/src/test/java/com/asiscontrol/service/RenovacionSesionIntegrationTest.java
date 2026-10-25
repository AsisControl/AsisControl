package com.asiscontrol.service;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.SesionAcceso;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.repository.*;
import com.asiscontrol.util.HashUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RenovacionSesionIntegrationTest {
    @Autowired RenovacionSesionService service;
    @Autowired UsuarioService usuarios;
    @Autowired AuthService auth;
    @Autowired RolRepository roles;
    @Autowired SesionAccesoRepository sesiones;
    @Autowired TokenRenovacionRepository tokens;
    @Autowired AccesoUsuarioRepository accesos;
    @Autowired SesionActividadService actividad;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired RegistroAuditoriaRepository auditorias;
    private MockMvc mvc;
    private static final String COOKIE = "asiscontrol_refresh";
    private static final String ORIGIN = "http://localhost:3000";

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private UsuarioDtos.Response crear() {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        var persona = new PersonaDtos.PersonaRequest("Persona", "Prueba", "Renovacion",
                TipoDocumento.PASAPORTE, key, null, Sexo.PREFIERE_NO_INDICAR,
                LocalDate.of(1990, 1, 1), null, key + "@example.test", null);
        return usuarios.provision(new UsuarioDtos.ProvisionRequest(null, persona,
                roles.findByNombreIgnoreCase("ALUMNO").orElseThrow().getId(), "usr" + key,
                "login" + key + "@example.test", "Temporal123!", "EST" + key,
                null, null, null, Set.of()));
    }

    private RenovacionSesionService.Credenciales login(UsuarioDtos.Response u) {
        return service.login(new AuthDtos.LoginRequest(u.username(), "Temporal123!"));
    }

    private void mutate(RenovacionSesionService.Credenciales c, Consumer<SesionAcceso> change) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var s = sesiones.findByTokenHashAndEstado(HashUtils.sha256(c.response().accessToken()),
                    EstadoSesion.VIGENTE).orElseThrow();
            change.accept(s);
            sesiones.saveAndFlush(s);
        });
    }

    private Long sesionId(RenovacionSesionService.Credenciales c) {
        return sesiones.findByTokenHashAndEstado(HashUtils.sha256(c.response().accessToken()),
                EstadoSesion.VIGENTE).orElseThrow().getId();
    }

    private List<com.asiscontrol.entity.RegistroAuditoria> auditoria(Long sesionId, String accion) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("SesionAcceso", sesionId.toString())
                .stream().filter(r -> r.getAccion().equals(accion)).toList();
    }

    @Test void renovacionExitosaNoGeneraAuditoria() {
        var c = login(crear());
        Long id = sesionId(c);
        service.renovar(c.refreshToken());
        assertThat(auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("SesionAcceso", id.toString()))
                .isEmpty();
    }

    @Test void reutilizarTokenSeAuditaANombreDelDuenoDeLaSesion() {
        var user = crear();
        var first = login(user);
        Long id = sesionId(first);
        var second = service.renovar(first.refreshToken());
        assertThatThrownBy(() -> service.renovar(first.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);

        var reutilizacion = auditoria(id, "REUTILIZACION_TOKEN_RENOVACION");
        assertThat(reutilizacion).hasSize(1);
        var registro = reutilizacion.getFirst();
        assertThat(registro.getResultado()).isEqualTo(ResultadoAuditoria.RECHAZADO);
        assertThat(registro.getModulo()).isEqualTo(ModuloAuditoria.SEGURIDAD);
        assertThat(registro.getActorIdentificador()).isEqualTo(user.username());
        assertThat(registro.getValorAnterior()).startsWith("estado=VIGENTE,tokenUsadoEn=");
        assertThat(registro.getNuevoValor()).isEqualTo("estado=REVOCADA");

        // Un intento posterior sobre la sesion ya revocada se registra como rechazo, no como revocacion.
        assertThatThrownBy(() -> service.renovar(second.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        var rechazo = auditoria(id, "RENOVACION_SESION_RECHAZADA");
        assertThat(rechazo).hasSize(1);
        assertThat(rechazo.getFirst().getMotivo()).isEqualTo("La sesion ya estaba REVOCADA");
        assertThat(auditoria(id, "REVOCAR_SESION")).isEmpty();
    }

    @Test void revocarPorInactividadSeAuditaConElMotivo() {
        var user = crear();
        var idle = login(user);
        Long id = sesionId(idle);
        mutate(idle, s -> s.setUltimaActividad(Instant.now().minus(Duration.ofMinutes(31))));
        assertThatThrownBy(() -> service.renovar(idle.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);

        var revocacion = auditoria(id, "REVOCAR_SESION");
        assertThat(revocacion).hasSize(1);
        assertThat(revocacion.getFirst().getMotivo()).isEqualTo("Inactividad mayor a 30 minutos");
        assertThat(revocacion.getFirst().getActorIdentificador()).isEqualTo(user.username());
        assertThat(revocacion.getFirst().getValorAnterior()).isEqualTo("estado=VIGENTE");
    }

    @Test void cerrarConTokenDeRenovacionSeAuditaUnaSolaVez() {
        var c = login(crear());
        Long id = sesionId(c);
        service.cerrar(c.refreshToken(), null);
        service.cerrar(c.refreshToken(), null);

        var cierre = auditoria(id, "CERRAR_SESION");
        assertThat(cierre).hasSize(1);
        assertThat(cierre.getFirst().getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
    }

    @Test void renuevaRotaYConservaElLimiteAbsoluto() {
        var user = crear();
        var original = login(user);
        var renewed = service.renovar(original.refreshToken());
        assertThat(renewed.response().user().id()).isEqualTo(user.id());
        assertThat(renewed.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(renewed.expiresAt()).isEqualTo(original.expiresAt());
        assertThat(Duration.between(Instant.now(), renewed.response().expiresAt()).toMinutes())
                .isBetween(13L, 15L);
        assertThat(tokens.findByTokenHash(original.refreshToken())).isEmpty();
        assertThat(tokens.findByTokenHash(HashUtils.sha256(original.refreshToken()))
                .orElseThrow().getFechaUso()).isNotNull();
        assertThat(actividad.validar(HashUtils.sha256(original.response().accessToken()))).isFalse();
        assertThat(actividad.validar(HashUtils.sha256(renewed.response().accessToken()))).isTrue();
    }

    @Test void reutilizarTokenViejoRevocaTodaLaSesionYSePersisteElRechazo() {
        var first = login(crear());
        var second = service.renovar(first.refreshToken());
        assertThatThrownBy(() -> service.renovar(first.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(actividad.validar(HashUtils.sha256(second.response().accessToken()))).isFalse();
        assertThatThrownBy(() -> service.renovar(second.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void rechazaCookieAusenteMalformadaYDesconocida() {
        for (String raw : Arrays.asList(null, "", "incorrecta", "A".repeat(43))) {
            assertThatThrownBy(() -> service.renovar(raw))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test void cierraConCookieInclusoSinAccessToken() {
        var c = login(crear());
        service.cerrar(c.refreshToken(), null);
        service.cerrar(c.refreshToken(), null); // idempotente
        service.cerrar(null, null);
        assertThatThrownBy(() -> service.renovar(c.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(actividad.validar(HashUtils.sha256(c.response().accessToken()))).isFalse();
    }

    @Test void respetaInactividadExpiracionYCuentaDeshabilitada() {
        var idle = login(crear());
        mutate(idle, s -> s.setUltimaActividad(Instant.now().minus(Duration.ofMinutes(31))));
        assertThatThrownBy(() -> service.renovar(idle.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        var expired = login(crear());
        mutate(expired, s -> s.setFechaExpiracion(Instant.now().minusSeconds(1)));
        assertThatThrownBy(() -> service.renovar(expired.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        var disabled = login(crear());
        mutate(disabled, s -> s.getUsuario().setEstado(EstadoAcceso.DESACTIVADO));
        assertThatThrownBy(() -> service.renovar(disabled.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        var temporary = login(crear());
        mutate(temporary, s -> s.getUsuario().setContrasenaTemporalExpira(Instant.now().minusSeconds(1)));
        assertThatThrownBy(() -> service.renovar(temporary.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void cambiarORestablecerContrasenaRevocaLaRenovacion() {
        var user = crear();
        var c = login(user);
        Jwt jwt = Jwt.withTokenValue(c.response().accessToken()).header("alg", "HS256")
                .subject(user.username()).build();
        auth.changePassword(jwt,
                new AuthDtos.ChangePasswordRequest("Temporal123!", "OtraSegura456!"));
        assertThatThrownBy(() -> service.renovar(c.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        var second = crear();
        var reset = login(second);
        usuarios.resetPassword(second.id(), new UsuarioDtos.ResetPasswordRequest(
                "OtraSegura456!", true, "Verificacion presencial"));
        assertThatThrownBy(() -> service.renovar(reset.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void soloUnaRenovacionConcurrentePuedeConsumirElMismoToken() throws Exception {
        var c = login(crear());
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try { service.renovar(c.refreshToken()); return true; }
            catch (BadCredentialsException expected) { return false; }
        };
        try {
            var one = pool.submit(attempt);
            var two = pool.submit(attempt);
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); }
    }

    @Test void httpEntregaCookieHttpOnlyYRecuperaSesionSinContrasena() throws Exception {
        var user = crear();
        var result = mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + user.username() + "\",\"password\":\"Temporal123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict")
                .doesNotContain("Max-Age").doesNotContain("Expires");
        assertThat(cookie.getMaxAge()).isNegative();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String accessToken = mapper.readTree(result.getResponse().getContentAsByteArray())
                .path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.id()));
        var refreshed = mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin", ORIGIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(user.id())).andReturn();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        String newToken = mapper.readTree(refreshed.getResponse().getContentAsByteArray())
                .path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test void httpBloqueaCsrfYBorraCookieAlCerrarORechazarSesion() throws Exception {
        var c = login(crear());
        var cookie = new Cookie(COOKIE, c.refreshToken());
        mvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin", "https://evil.test"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", ORIGIN))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThatThrownBy(() -> service.renovar(c.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
    }
}
