package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Transacciones HTTP reales en hilos independientes; no comparten la transacción de JUnit. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pago_apoderado_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PagoApoderadoConcurrencyIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private Datos datos;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        datos = new TransactionTemplate(transactionManager).execute(status -> prepararFamilia());
    }

    @Test
    void dosConfirmacionesSimultaneasDeUnaOrdenAplicanUnSoloPagoYComprobante() throws Exception {
        long ordenId = iniciar(UUID.randomUUID().toString()).path("id").asLong();
        var respuestas = simultaneas(() -> confirmar(ordenId), () -> confirmar(ordenId));
        assertThat(respuestas).allSatisfy(response -> assertThat(response.path("estado").asText()).isEqualTo("COMPLETADO"));
        verificarSaldoYTransacciones(1, 0);
    }

    @Test
    void ordenesAnterioresCompetidorasNoPuedenPagarDosVecesElMismoSaldo() throws Exception {
        long primera = iniciar(UUID.randomUUID().toString()).path("id").asLong();
        // Simula dos órdenes antiguas: el inicio nuevo ya no permite crearlas superpuestas.
        cambiarEstado(primera, EstadoOrdenPago.RECHAZADO);
        long segunda = iniciar(UUID.randomUUID().toString()).path("id").asLong();
        cambiarEstado(primera, EstadoOrdenPago.PENDIENTE);
        var respuestas = simultaneas(() -> confirmar(primera), () -> confirmar(segunda));
        assertThat(respuestas.stream().map(response -> response.path("estado").asText()))
                .containsExactlyInAnyOrder("COMPLETADO", "RECHAZADO");
        verificarSaldoYTransacciones(1, 1);
    }

    @Test
    void clavesDistintasSimultaneasSoloCreanUnaOrdenVigente() throws Exception {
        var respuestas = simultaneas(() -> intentarIniciar(UUID.randomUUID().toString(), datos),
                () -> intentarIniciar(UUID.randomUUID().toString(), datos));
        verificarUnSoloInicio(respuestas);
    }

    @Test
    void apoderadosDistintosDelMismoAlumnoSoloPuedenCrearUnaOrdenVigente() throws Exception {
        var otro = new TransactionTemplate(transactionManager).execute(status -> {
            var apoderado = new Apoderado(); apoderado.setPersona(persona()); em.persist(apoderado);
            var alumno = em.find(CompromisoPago.class, datos.compromisoId()).getMatricula().getAlumno();
            var link = new AlumnoApoderado(); link.setAlumno(alumno); link.setApoderado(apoderado);
            link.setParentesco(Parentesco.MADRE); em.persist(link); em.flush();
            return new Datos(apoderado.getId(), apoderado.getPersona().getId(), datos.compromisoId());
        });
        var respuestas = simultaneas(() -> intentarIniciar(UUID.randomUUID().toString(), datos),
                () -> intentarIniciar(UUID.randomUUID().toString(), otro));
        verificarUnSoloInicio(respuestas);
    }

    @Test
    void confirmarMientrasSeIntentaOtraOperacionNoSeInterbloqueaNiDuplicaElPago() throws Exception {
        long orden = iniciar(UUID.randomUUID().toString()).path("id").asLong();
        var respuestas = simultaneas(() -> confirmar(orden),
                () -> intentarIniciar(UUID.randomUUID().toString(), datos));
        assertThat(respuestas.get(0).path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(respuestas.get(1).path("httpStatus").asInt()).isIn(409, 422);
        verificarSaldoYTransacciones(1, 0);
    }

    private void cambiarEstado(long orden, EstadoOrdenPago estado) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                em.find(OrdenPago.class, orden).setEstado(estado));
    }

    private void verificarUnSoloInicio(List<JsonNode> respuestas) {
        assertThat(respuestas.stream().map(row -> row.path("httpStatus").asInt()))
                .containsExactlyInAnyOrder(201, 409);
        var rechazo = respuestas.stream().filter(row -> row.path("httpStatus").asInt() == 409).findFirst().orElseThrow();
        assertThat(rechazo.path("body").path("code").asText()).isEqualTo("PAGO_EN_CURSO");
        assertThat(rechazo.path("body").path("details")).isEmpty();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(d) from OrdenPagoDetalle d where d.compromiso.id=:id", Long.class)
                    .setParameter("id", datos.compromisoId()).getSingleResult()).isEqualTo(1);
            assertThat(em.createQuery("select count(t) from TransaccionPago t where t.compromisoPago.id=:id", Long.class)
                    .setParameter("id", datos.compromisoId()).getSingleResult()).isEqualTo(1);
            assertThat(em.find(CompromisoPago.class, datos.compromisoId()).getMontoPagado()).isZero();
        });
    }

    private JsonNode intentarIniciar(String clave, Datos familia) throws Exception {
        var response = mvc.perform(post("/api/pagos/iniciar").with(sesion(familia))
                        .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("obligacionesIds", List.of(familia.compromisoId()), "metodo", "TARJETA"))))
                .andReturn().getResponse();
        var resultado = json.createObjectNode().put("httpStatus", response.getStatus());
        resultado.set("body", json.readTree(response.getContentAsByteArray()));
        return resultado;
    }

    @Test
    void iniciarDosVecesConLaMismaClaveEnParaleloDevuelveLaMismaOrden() throws Exception {
        String clave = UUID.randomUUID().toString();
        var respuestas = simultaneas(() -> iniciar(clave), () -> iniciar(clave));
        assertThat(respuestas.get(0).path("id").asLong()).isEqualTo(respuestas.get(1).path("id").asLong());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(o) from OrdenPago o where o.apoderado.id=:id", Long.class)
                    .setParameter("id", datos.apoderadoId()).getSingleResult()).isEqualTo(1);
            assertThat(em.createQuery("select count(t) from TransaccionPago t where t.compromisoPago.id=:id", Long.class)
                    .setParameter("id", datos.compromisoId()).getSingleResult()).isEqualTo(1);
            assertThat(em.find(CompromisoPago.class, datos.compromisoId()).getMontoPagado()).isZero();
        });
    }

    private JsonNode iniciar(String clave) throws Exception {
        return json.readTree(mvc.perform(post("/api/pagos/iniciar").with(sesion())
                        .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("obligacionesIds", List.of(datos.compromisoId()), "metodo", "TARJETA"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode confirmar(long id) throws Exception {
        var response = mvc.perform(post("/api/pagos/ordenes/" + id + "/confirmar-tarjeta").with(sesion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("escenario", "APROBADO"))))
                .andReturn().getResponse();
        assertThat(response.getStatus())
                .as("Confirmación concurrente de orden %s: %s", id, response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(200);
        return json.readTree(response.getContentAsByteArray());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor sesion() {
        return sesion(datos);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor sesion(Datos familia) {
        return jwt().jwt(token -> token.subject("familia-concurrente-" + familia.apoderadoId())
                        .claim("personaId", familia.personaId()).claim("requiereCambioContrasena", false))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"), new SimpleGrantedAuthority("PAGOS_LEER"));
    }

    private List<JsonNode> simultaneas(Callable<JsonNode> first, Callable<JsonNode> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> correr(first, ready, start));
            var b = executor.submit(() -> correr(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }

    private JsonNode correr(Callable<JsonNode> task, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la prueba concurrente");
        return task.call();
    }

    private void verificarSaldoYTransacciones(int aprobadas, int rechazadas) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(em.find(CompromisoPago.class, datos.compromisoId()).getMontoPagado()).isEqualByComparingTo("350.00");
            var transacciones = em.createQuery("from TransaccionPago t where t.compromisoPago.id=:id", TransaccionPago.class)
                    .setParameter("id", datos.compromisoId()).getResultList();
            assertThat(transacciones.stream().filter(tx -> tx.getEstado() == EstadoTransaccionPago.APROBADA).count())
                    .isEqualTo(aprobadas);
            assertThat(transacciones.stream().filter(tx -> tx.getEstado() == EstadoTransaccionPago.RECHAZADA).count())
                    .isEqualTo(rechazadas);
            assertThat(em.createQuery("select count(c) from Comprobante c where c.transaccionPago.compromisoPago.id=:id", Long.class)
                    .setParameter("id", datos.compromisoId()).getSingleResult()).isEqualTo(1);
        });
    }

    private Datos prepararFamilia() {
        var anios = em.createQuery("from AnioAcademico a where a.anio=2082", AnioAcademico.class).getResultList();
        AnioAcademico anio;
        if (anios.isEmpty()) {
            anio = new AnioAcademico(); anio.setAnio(2082); anio.setEstado(EstadoAnioAcademico.ACTIVO);
            anio.setFechaInicio(LocalDate.of(2082, 1, 1)); anio.setFechaFin(LocalDate.of(2082, 12, 31)); em.persist(anio);
        } else anio = anios.getFirst();
        var apoderado = new Apoderado(); apoderado.setPersona(persona()); em.persist(apoderado);
        var alumno = new Alumno(); alumno.setPersona(persona()); alumno.setCodigoAlumno("CONC-" + key()); em.persist(alumno);
        var link = new AlumnoApoderado(); link.setApoderado(apoderado); link.setAlumno(alumno);
        link.setParentesco(Parentesco.PADRE); em.persist(link);
        var seccion = new Seccion(); seccion.setNombre(key()); seccion.setGrado(1); seccion.setAnioAcademico(anio.getAnio());
        seccion.setCapacidadMaxima(2); em.persist(seccion);
        var solicitud = new SolicitudMatricula(); solicitud.setAlumno(alumno); solicitud.setSeccion(seccion);
        solicitud.setAnioAcademico(2082); solicitud.setGrado(1); solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        var matricula = new Matricula(); matricula.setAlumno(alumno); matricula.setSeccion(seccion);
        matricula.setSolicitudMatricula(solicitud); matricula.setCodigoMatricula("MAT-CONC-" + key());
        matricula.setAnioAcademico(2082); matricula.setFechaMatricula(Instant.now()); matricula.setEstado(EstadoMatricula.ACTIVA);
        em.persist(matricula);
        var compromiso = new CompromisoPago(); compromiso.setMatricula(matricula);
        compromiso.setCodigoCompromiso("CP-CONC-" + key()); compromiso.setConcepto("Pensión concurrente");
        compromiso.setNumeroCuota(1); compromiso.setMontoTotal(new BigDecimal("350.00"));
        compromiso.setMontoPagado(BigDecimal.ZERO.setScale(2)); compromiso.setMoneda("PEN");
        compromiso.setFechaVencimiento(LocalDate.of(2082, 4, 30)); compromiso.setEstado(EstadoCompromisoPago.PENDIENTE);
        em.persist(compromiso); em.flush();
        return new Datos(apoderado.getId(), apoderado.getPersona().getId(), compromiso.getId());
    }

    private Persona persona() {
        var persona = new Persona(); persona.setNombres("Persona"); persona.setApellidoPaterno("Concurrente");
        persona.setTipoDocumento(TipoDocumento.PASAPORTE); persona.setNumeroDocumento(key());
        persona.setFechaNacimiento(LocalDate.of(2010, 1, 1)); em.persist(persona); return persona;
    }

    private String key() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private record Datos(Long apoderadoId, Long personaId, Long compromisoId) {}
}
