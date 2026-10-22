package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.*;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.repository.*;
import com.asiscontrol.security.PasswordPolicy;
import com.asiscontrol.util.HashUtils;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "app.mail.enabled=true",
            "app.mail.from=colegio@example.test",
            "app.frontend-url=https://colegio.example.test"
        })
@ActiveProfiles("test")
@Import(SeguridadRecuperacionIntegrationTest.MailConfig.class)
class SeguridadRecuperacionIntegrationTest {
    @TestConfiguration
    static class MailConfig {
        @Bean
        CapturingMail sender() {
            return new CapturingMail();
        }
    }

    static class CapturingMail extends JavaMailSenderImpl {
        final List<SimpleMailMessage> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void send(SimpleMailMessage... messages) {
            sent.addAll(Arrays.asList(messages));
        }

        @Override
        public void send(SimpleMailMessage message) {
            sent.add(message);
        }
    }

    @Autowired UsuarioService users;
    @Autowired AuthService auth;
    @Autowired RecuperacionService recovery;
    @Autowired AccesoUsuarioRepository accounts;
    @Autowired TokenRecuperacionRepository tokens;
    @Autowired SesionAccesoRepository sessions;
    @Autowired RolRepository roles;
    @Autowired CapturingMail mail;
    @Autowired PlatformTransactionManager manager;
    @Autowired SesionActividadService activity;
    @Autowired LimiteRecuperacionService limits;

    private UsuarioDtos.Response create(boolean verified) {
        String k = UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        var p =
                new PersonaDtos.PersonaRequest(
                        "Persona",
                        "Prueba",
                        "Sistema",
                        TipoDocumento.PASAPORTE,
                        k,
                        null,
                        Sexo.PREFIERE_NO_INDICAR,
                        LocalDate.of(1990, 1, 1),
                        null,
                        k + "@example.test",
                        null);
        return users.provision(
                new UsuarioDtos.ProvisionRequest(
                        null,
                        p,
                        roles.findByNombreIgnoreCase("DOCENTE").orElseThrow().getId(),
                        "doc" + k,
                        "cuenta" + k + "@example.test",
                        "Temporal123!",
                        null,
                        "DOC" + k,
                        LocalDate.now(),
                        Especialidad.MATEMATICA,
                        Set.of(),
                        verified));
    }

    private void mutate(Long id, java.util.function.Consumer<AccesoUsuario> c) {
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        s -> {
                            var u = accounts.findById(id).orElseThrow();
                            c.accept(u);
                            accounts.saveAndFlush(u);
                        });
    }

    private String link(UsuarioDtos.Response user) {
        int before = mail.sent.size();
        recovery.solicitar(user.username(), UUID.randomUUID().toString());
        assertThat(mail.sent.size()).isEqualTo(before + 1);
        String content = mail.sent.get(before).getText();
        assertThat(content).startsWith("Se solicitó recuperar");
        return content.split("#token=")[1].split("\\s", 2)[0];
    }

    private AuthDtos.AuthResponse login(UsuarioDtos.Response user, String password) {
        return auth.login(new AuthDtos.LoginRequest(user.username(), password));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void conservaCincoFallosYNoProlongaBloqueo() {
        var u = create(true);
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(() -> login(u, "Equivocada123!"))
                    .isInstanceOf(BadCredentialsException.class);
        var blocked = accounts.findById(u.id()).orElseThrow();
        assertThat(blocked.getIntentosFallidos()).isEqualTo(5);
        Instant until = blocked.getBloqueadoHasta();
        assertThatThrownBy(() -> login(u, "Temporal123!"))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(accounts.findById(u.id()).orElseThrow().getBloqueadoHasta()).isEqualTo(until);
        mutate(u.id(), a -> a.setBloqueadoHasta(Instant.now().minusSeconds(1)));
        assertThat(login(u, "Temporal123!").user().id()).isEqualTo(u.id());
        assertThat(accounts.findById(u.id()).orElseThrow().getIntentosFallidos()).isZero();
    }

    @Test
    void almacenaHashRecuperaUnaVezYRevocaSesiones() {
        var u = create(true);
        var session = login(u, "Temporal123!");
        String token = link(u);
        assertThat(tokens.findByTokenHash(HashUtils.sha256(token))).isPresent();
        assertThat(tokens.findByTokenHash(token)).isEmpty();
        recovery.validar(token);
        recovery.confirmar(
                new AuthDtos.ResetRecoveryRequest(token, "NuevaSegura123!", "NuevaSegura123!"));
        assertThat(activity.validar(HashUtils.sha256(session.accessToken()))).isFalse();
        assertThat(login(u, "NuevaSegura123!").user().requiereCambioContrasena()).isFalse();
        assertThatThrownBy(
                        () ->
                                recovery.confirmar(
                                        new AuthDtos.ResetRecoveryRequest(
                                                token, "OtraSegura123!", "OtraSegura123!")))
                .hasMessageContaining("enlace");
    }

    @Test
    void reemplazaEnlaceYRechazaVencidoSinCambiarClave() {
        var u = create(true);
        String old = link(u), recent = link(u);
        assertThatThrownBy(() -> recovery.validar(old)).hasMessageContaining("enlace");
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        s -> {
                            var t = tokens.findByTokenHash(HashUtils.sha256(recent)).orElseThrow();
                            t.setExpira(Instant.now().minusSeconds(1));
                            tokens.saveAndFlush(t);
                        });
        assertThatThrownBy(
                        () ->
                                recovery.confirmar(
                                        new AuthDtos.ResetRecoveryRequest(
                                                recent, "NuevaSegura123!", "NuevaSegura123!")))
                .hasMessageContaining("enlace");
        assertThat(login(u, "Temporal123!").user().id()).isEqualTo(u.id());
    }

    @Test
    void noEnviaParaCorreoNoConfirmadoOAccesoDesactivado() {
        var u = create(false);
        int before = mail.sent.size();
        recovery.solicitar(u.username(), UUID.randomUUID().toString());
        assertThat(mail.sent.size()).isEqualTo(before);
        mutate(
                u.id(),
                a -> {
                    a.setCorreoVerificado(true);
                    a.setEstado(EstadoAcceso.DESACTIVADO);
                });
        recovery.solicitar(u.username(), UUID.randomUUID().toString());
        recovery.solicitar(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertThat(mail.sent.size()).isEqualTo(before);
    }

    @Test
    void limitaPorIdentificadorYPorIp() {
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++)
            assertThat(limits.permitir(key, "IP" + UUID.randomUUID())).isTrue();
        assertThat(limits.permitir(key, "IP" + UUID.randomUUID())).isFalse();
        String ip = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++)
            assertThat(limits.permitir(UUID.randomUUID().toString(), ip)).isTrue();
        assertThat(limits.permitir(UUID.randomUUID().toString(), ip)).isFalse();
    }

    @Test
    void rechazaTemporalVencidaYRecuperacionRehabilita() {
        var u = create(true);
        var temporalSession = login(u, "Temporal123!");
        mutate(u.id(), a -> a.setContrasenaTemporalExpira(Instant.now().minusSeconds(1)));
        assertThat(activity.validar(HashUtils.sha256(temporalSession.accessToken()))).isFalse();
        assertThatThrownBy(() -> login(u, "Temporal123!"))
                .isInstanceOf(BadCredentialsException.class);
        String token = link(u);
        recovery.confirmar(
                new AuthDtos.ResetRecoveryRequest(token, "NuevaSegura123!", "NuevaSegura123!"));
        assertThat(login(u, "NuevaSegura123!").user().requiereCambioContrasena()).isFalse();
    }

    @Test
    void aplicaPoliticaYConservaEnlaceAnteClaveInvalida() {
        var u = create(true);
        String token = link(u);
        assertThatThrownBy(
                        () ->
                                recovery.confirmar(
                                        new AuthDtos.ResetRecoveryRequest(
                                                token, "Temporal123!", "Temporal123!")))
                .hasMessageContaining("diferente");
        assertThatThrownBy(
                        () ->
                                recovery.confirmar(
                                        new AuthDtos.ResetRecoveryRequest(
                                                token, "abcdefgh", "abcdefgh")))
                .hasMessageContaining("8 a 64");
        recovery.validar(token);
        PasswordPolicy.validate("Abcdefg1", "usuario");
        assertThatThrownBy(() -> PasswordPolicy.validate("Usuario1", "Usuario1"))
                .hasMessageContaining("usuario");
    }

    @Test
    void rechazaEntradaUtf8ExcesivaSinErrorInterno() {
        var u = create(true);
        assertThatThrownBy(() -> login(u, "Á".repeat(60) + "ab1"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void confirmaSoloUnaVezBajoConcurrencia() throws Exception {
        var u = create(true);
        String token = link(u);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> task =
                () -> {
                    start.await();
                    try {
                        recovery.confirmar(
                                new AuthDtos.ResetRecoveryRequest(
                                        token, "NuevaSegura123!", "NuevaSegura123!"));
                        return true;
                    } catch (com.asiscontrol.exception.BusinessRuleException e) {
                        return false;
                    }
                };
        var first = pool.submit(task);
        var second = pool.submit(task);
        start.countDown();
        try {
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aplicaInactividadTreintaMinutosYExpiracionAbsoluta() {
        var u = create(true);
        var response = login(u, "Temporal123!");
        String hash = HashUtils.sha256(response.accessToken());
        assertThat(Duration.between(Instant.now(), response.expiresAt()).toMinutes()).isBetween(13L, 15L);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        s -> {
                            var session =
                                    sessions.findByTokenHashAndEstado(hash, EstadoSesion.VIGENTE)
                                            .orElseThrow();
                            assertThat(Duration.between(Instant.now(), session.getFechaExpiracion()).toHours())
                                    .isEqualTo(7);
                            session.setUltimaActividad(Instant.now().minus(Duration.ofMinutes(31)));
                            sessions.saveAndFlush(session);
                        });
        assertThat(activity.validar(hash)).isFalse();
    }
}
