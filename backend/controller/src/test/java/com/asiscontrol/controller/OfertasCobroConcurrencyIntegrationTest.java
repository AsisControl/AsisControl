package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ofertas_concurrencia;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OfertasCobroConcurrencyIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void dosApoderadosQueAceptanAlMismoTiempoSoloCreanUnaObligacion() throws Exception {
        var datos = new TransactionTemplate(transactions).execute(status -> preparar());
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var publicada = json.readTree(mvc.perform(post("/api/configuracion-pagos/ofertas")
                        .with(jwt().jwt(j -> j.subject("ofertas-admin").claim("requiereCambioContrasena", false))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"),
                                        new SimpleGrantedAuthority("PAGOS_LEER"), new SimpleGrantedAuthority("PAGOS_ESCRIBIR")))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                                "idConcepto", datos.concepto(), "idMatriculas", List.of(datos.matricula()),
                                "referencia", "CONCURRENT-2086", "titulo", "Actividad concurrente", "monto", 250,
                                "fechaVencimiento", "2086-10-31"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).get(0);
        var inicio = new CountDownLatch(1);
        var listos = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primero = executor.submit(() -> aceptar(mvc, publicada, datos.personaUno(), listos, inicio));
            var segundo = executor.submit(() -> aceptar(mvc, publicada, datos.personaDos(), listos, inicio));
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            var respuestaUno = primero.get(30, TimeUnit.SECONDS);
            var respuestaDos = segundo.get(30, TimeUnit.SECONDS);
            assertThat(respuestaUno.path("estado").asText()).isEqualTo("ACEPTADA");
            assertThat(respuestaDos.path("idCompromiso").asLong()).isEqualTo(respuestaUno.path("idCompromiso").asLong());
        }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var compromisos = em.createQuery("from CompromisoPago c where c.conceptoCobro.id=:id", CompromisoPago.class)
                    .setParameter("id", datos.concepto()).getResultList();
            assertThat(compromisos).hasSize(1);
            assertThat(compromisos.getFirst().getMontoPagado()).isZero();
            assertThat(compromisos.getFirst().getMontoTotal()).isEqualByComparingTo("250.00");
            assertThat(em.createQuery("select count(t) from TransaccionPago t", Long.class).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(c) from Comprobante c", Long.class).getSingleResult()).isZero();
        });
    }

    private JsonNode aceptar(MockMvc mvc, JsonNode oferta, Long persona, CountDownLatch listos,
            CountDownLatch inicio) throws Exception {
        listos.countDown();
        if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No inició la prueba concurrente.");
        return json.readTree(mvc.perform(post("/api/apoderado/ofertas/" + oferta.path("id").asLong() + "/aceptar")
                        .with(jwt().jwt(j -> j.subject("ofertas-familia-" + persona).claim("personaId", persona)
                                        .claim("requiereCambioContrasena", false))
                                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"), new SimpleGrantedAuthority("PAGOS_LEER")))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                                "version", oferta.path("version").asLong(), "firmaCotizacion", oferta.path("firmaCotizacion").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private Datos preparar() {
        var anio = new AnioAcademico(); anio.setAnio(2086); anio.setFechaInicio(LocalDate.of(2086, 1, 1));
        anio.setFechaFin(LocalDate.of(2086, 12, 31)); anio.setEstado(EstadoAnioAcademico.ACTIVO); em.persist(anio);
        var alumno = new Alumno(); alumno.setPersona(persona()); alumno.setCodigoAlumno("OFC-" + clave()); em.persist(alumno);
        var uno = new Apoderado(); uno.setPersona(persona()); em.persist(uno);
        var dos = new Apoderado(); dos.setPersona(persona()); em.persist(dos);
        for (var apoderado : List.of(uno, dos)) {
            var v = new AlumnoApoderado(); v.setAlumno(alumno); v.setApoderado(apoderado); v.setParentesco(Parentesco.PADRE); em.persist(v);
        }
        var seccion = new Seccion(); seccion.setNombre("OC"); seccion.setGrado(1); seccion.setAnioAcademico(2086);
        seccion.setCapacidadMaxima(20); em.persist(seccion);
        var solicitud = new SolicitudMatricula(); solicitud.setAlumno(alumno); solicitud.setSeccion(seccion);
        solicitud.setAnioAcademico(2086); solicitud.setGrado(1); solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA); em.persist(solicitud);
        var matricula = new Matricula(); matricula.setAlumno(alumno); matricula.setSolicitudMatricula(solicitud); matricula.setSeccion(seccion);
        matricula.setAnioAcademico(2086); matricula.setEstado(EstadoMatricula.ACTIVA); matricula.setFechaMatricula(Instant.now());
        matricula.setCodigoMatricula("MAT-OC-" + clave()); em.persist(matricula);
        var concepto = new ConceptoCobro(); concepto.setCodigo("COBRO-" + clave()); concepto.setNombre("Actividad opcional");
        concepto.setPeriodicidad(PeriodicidadCobro.UNICO); concepto.setRequiereAceptacion(true); em.persist(concepto); em.flush();
        return new Datos(matricula.getId(), concepto.getId(), uno.getPersona().getId(), dos.getPersona().getId());
    }

    private Persona persona() {
        var persona = new Persona(); persona.setNombres("Persona"); persona.setApellidoPaterno("Oferta");
        persona.setTipoDocumento(TipoDocumento.PASAPORTE); persona.setNumeroDocumento(clave());
        persona.setFechaNacimiento(LocalDate.of(2000, 1, 1)); em.persist(persona); return persona;
    }
    private String clave() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private record Datos(Long matricula, Long concepto, Long personaUno, Long personaDos) {}
}
