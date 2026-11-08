package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dos familias sin relación permiten comprobar aislamiento por registro, no solo por rol. */
abstract class PagosFamiliaIntegrationSupport extends ApiIntegrationSupport {
    protected static final String OBLIGACIONES = "/api/apoderado/obligaciones";
    protected static final String PAGOS = "/api/pagos";
    protected static final ZoneId LIMA = ZoneId.of("America/Lima");

    protected JwtRequestPostProcessor comoApoderado(Apoderado apoderado) {
        return jwt().jwt(token -> token.subject("familia-" + apoderado.getId())
                        .claim("personaId", apoderado.getPersona().getId())
                        .claim("requiereCambioContrasena", false))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"),
                        new SimpleGrantedAuthority("PAGOS_LEER"));
    }

    protected JsonNode familia(String method, String path, Object body, int expected) throws Exception {
        return familia(guardian, method, path, body, expected, null);
    }

    protected JsonNode familia(Apoderado apoderado, String method, String path,
            Object body, int expected, String idempotencia) throws Exception {
        var builder = builder(method, path).with(comoApoderado(apoderado));
        return response(builder, body, expected, idempotencia);
    }

    protected JsonNode publico(String method, String path, Object body, int expected) throws Exception {
        return response(builder(method, path), body, expected, null);
    }

    private MockHttpServletRequestBuilder builder(String method, String path) {
        return switch (method) {
            case "POST" -> post(path);
            default -> get(path);
        };
    }

    private JsonNode response(MockHttpServletRequestBuilder builder, Object body,
            int expected, String idempotencia) throws Exception {
        if (idempotencia != null) builder.header("Idempotency-Key", idempotencia);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsByteArray().length == 0
                ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }

    protected JsonNode iniciar(String metodo, Long... ids) throws Exception {
        return iniciar(guardian, metodo, UUID.randomUUID().toString(), ids);
    }

    protected JsonNode iniciar(Apoderado apoderado, String metodo, String idempotencia, Long... ids) throws Exception {
        return familia(apoderado, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(ids), "metodo", metodo), 201, idempotencia);
    }

    protected String rutaOrden(JsonNode orden) {
        return PAGOS + "/ordenes/" + orden.path("id").asLong();
    }

    protected JsonNode confirmar(JsonNode orden, String escenario) throws Exception {
        return familia("POST", rutaOrden(orden) + "/confirmar-tarjeta", data("escenario", escenario), 200);
    }

    protected CompromisoPago compromiso(int cuota, String monto) {
        return compromiso(student, cuota, monto, "0.00", LocalDate.now(LIMA).plusDays(10));
    }

    protected CompromisoPago compromiso(Alumno alumno, int cuota, String monto, String pagado, LocalDate vence) {
        var matricula = em.createQuery("select m from Matricula m where m.alumno.id=:id", Matricula.class)
                .setParameter("id", alumno.getId()).getSingleResult();
        var compromiso = new CompromisoPago();
        compromiso.setMatricula(matricula);
        compromiso.setCodigoCompromiso("CP-PRUEBA-" + key());
        compromiso.setConcepto("Pensión de prueba");
        compromiso.setNumeroCuota(cuota);
        compromiso.setMontoTotal(new BigDecimal(monto));
        compromiso.setMontoPagado(new BigDecimal(pagado));
        compromiso.setMoneda("PEN");
        compromiso.setFechaVencimiento(vence);
        compromiso.setEstado(new BigDecimal(monto).compareTo(new BigDecimal(pagado)) == 0
                ? EstadoCompromisoPago.PAGADO
                : vence.isBefore(LocalDate.now(LIMA)) ? EstadoCompromisoPago.VENCIDO
                : new BigDecimal(pagado).signum() > 0 ? EstadoCompromisoPago.PARCIAL : EstadoCompromisoPago.PENDIENTE);
        em.persist(compromiso);
        em.flush();
        return compromiso;
    }

    protected FamiliaAjena otraFamilia() {
        var apoderado = new Apoderado(); apoderado.setPersona(person()); em.persist(apoderado);
        var alumno = new Alumno(); alumno.setPersona(person()); alumno.setCodigoAlumno("AJENO-" + key()); em.persist(alumno);
        var link = new AlumnoApoderado(); link.setAlumno(alumno); link.setApoderado(apoderado);
        link.setPrincipal(true); link.setParentesco(Parentesco.PADRE); em.persist(link);
        var solicitud = new SolicitudMatricula(); solicitud.setAlumno(alumno); solicitud.setSeccion(section);
        solicitud.setAnioAcademico(2090); solicitud.setGrado(1);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA); em.persist(solicitud);
        var matricula = new Matricula(); matricula.setAlumno(alumno); matricula.setSeccion(section);
        matricula.setSolicitudMatricula(solicitud); matricula.setCodigoMatricula("MAT-AJENO-" + key());
        matricula.setAnioAcademico(2090); matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA); em.persist(matricula); em.flush();
        return new FamiliaAjena(apoderado, alumno);
    }

    protected long contar(String entidad) {
        return em.createQuery("select count(e) from " + entidad + " e", Long.class).getSingleResult();
    }

    protected record FamiliaAjena(Apoderado apoderado, Alumno alumno) {}
}
