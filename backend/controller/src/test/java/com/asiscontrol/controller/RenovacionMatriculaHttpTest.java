package com.asiscontrol.controller;

import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.support.TarifasTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RenovacionMatriculaHttpTest extends PagosFamiliaIntegrationSupport {
    private static final String RUTA = "/api/renovaciones";
    private Matricula anterior;
    private Seccion destino;

    @BeforeEach
    void prepararOferta() {
        anterior = em.createQuery("select m from Matricula m where m.alumno.id = :alumno", Matricula.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        anterior.setEstado(EstadoMatricula.FINALIZADA);
        year.setEstado(EstadoAnioAcademico.CERRADO);
        year.setClaveActiva(null);
        year.setAdmisionAbierta(false);
        em.flush();

        var anio = new AnioAcademico();
        anio.setAnio(2091);
        anio.setFechaInicio(LocalDate.of(2091, 1, 1));
        anio.setFechaFin(LocalDate.of(2091, 12, 31));
        anio.setAdmisionAbierta(true);
        anio.setEstado(EstadoAnioAcademico.ACTIVO);
        anio.setClaveActiva(1);
        em.persist(anio);
        var grado = new GradoAcademico();
        grado.setAnioAcademico(anio);
        grado.setNumero(2);
        grado.setCapacidadDefault(1);
        em.persist(grado);
        destino = new Seccion();
        destino.setAnioAcademico(2091);
        destino.setGrado(2);
        destino.setNombre("A");
        destino.setCapacidadMaxima(1);
        em.persist(destino);
        TarifasTestSupport.configurar(em, 2091, 2);
        em.flush();
    }

    @Test
    void apruebaManualTrasValidarYGeneraMatriculaYDiezPensiones() throws Exception {
        promocion("PROMOVIDO");
        compromiso(student, 1, "300", "0", LocalDate.now(LIMA)); // Vence hoy, aún no está vencido.
        var anulado = compromiso(student, 2, "300", "0", LocalDate.now(LIMA).minusDays(2));
        anulado.setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO);
        var opciones = familiaRenovacion(guardian, false, RUTA + "/opciones", null, 200);
        assertThat(opciones).hasSize(1);
        assertThat(opciones.get(0).path("gradoDestino").asInt()).isEqualTo(2);
        var solicitud = enviar();
        assertThat(solicitud.path("estado").asText()).isEqualTo("EN_REVISION");
        assertThat(contarDestino("Matricula m", "m.anioAcademico")).isZero();
        unaFamiliaNoPuedeDecidir(solicitud);

        var plan = getOk(ruta(solicitud) + "/plan");
        assertThat(plan.path("items")).hasSize(10);
        var aprobada = decidir(solicitud, plan.path("firmaPlan").asText());
        assertThat(aprobada.path("estado").asText()).isEqualTo("APROBADA");
        assertThat(aprobada.path("idMatricula").isIntegralNumber()).isTrue();
        assertThat(contarDestino("Matricula m", "m.anioAcademico")).isOne();
        assertThat(contarDestino("CompromisoPago m", "m.matricula.anioAcademico")).isEqualTo(10);
        var consulta = familiaRenovacion(guardian, false, ruta(solicitud), null, 200);
        assertThat(consulta.path("estado").asText()).isEqualTo("APROBADA");
        request("POST", ruta(solicitud) + "/aprobar", data("version", aprobada.path("version").asLong()), 409);
        request("PUT", RUTA + "/promociones/" + anterior.getId(), data(
                "version", anterior.getVersion(), "resultado", "NO_PROMOVIDO", "motivo", "Cambio tardío"), 409);
    }

    @Test
    void rechazaPorGradoNoAprobadoYConservaMotivo() throws Exception {
        promocion("NO_PROMOVIDO");
        verificarRechazo(enviar(), "GRADO_NO_APROBADO", "no aprobó");
    }

    @Test
    void rechazaSiNoExisteResultadoOficial() throws Exception {
        verificarRechazo(enviar(), "GRADO_NO_APROBADO", "No se acreditó");
    }

    @Test
    void rechazaPorDeudaVencidaInclusoSiHuboUnAbono() throws Exception {
        promocion("PROMOVIDO");
        compromiso(student, 1, "300", "100", LocalDate.now(LIMA).minusDays(1));
        verificarRechazo(enviar(), "DEUDA_VENCIDA", "obligaciones vencidas");
    }

    @Test
    void vuelveAComprobarVacanteAlAprobar() throws Exception {
        promocion("PROMOVIDO");
        var solicitud = enviar();
        ocuparDestino();
        verificarRechazo(solicitud, "SECCION_SIN_VACANTES", "vacante disponible");
    }

    @Test
    void permiteElegirOtraSeccionCuandoLaSolicitadaNoTieneVacantes() throws Exception {
        promocion("PROMOVIDO");
        var solicitud = enviar();
        ocuparDestino();
        var alternativa = seccionAlternativa(destino.getGrado());
        var otroGrado = seccionAlternativa(destino.getGrado() + 1);
        String path = ruta(solicitud) + "/aprobar";
        long version = solicitud.path("version").asLong();
        var sinElegir = request("POST", path, data("version", version), 422);
        assertThat(sinElegir.path("code").asText()).isEqualTo("SECCION_SIN_VACANTES");
        request("POST", path, data("version", version, "idSeccion", otroGrado.getId()), 422);
        assertThat(getOk(ruta(solicitud)).path("estado").asText()).isEqualTo("EN_REVISION");
        String firma = getOk(ruta(solicitud) + "/plan").path("firmaPlan").asText();
        var aprobada = request("POST", path,
                data("version", version, "firmaPlan", firma, "idSeccion", alternativa.getId()), 200);
        assertThat(aprobada.path("estado").asText()).isEqualTo("APROBADA");
        assertThat(aprobada.path("idSeccion").asLong()).isEqualTo(alternativa.getId());
        assertThat(getOk("/api/matriculas/" + aprobada.path("idMatricula").asLong()).path("seccionId").asLong())
                .isEqualTo(alternativa.getId());
    }

    private Seccion seccionAlternativa(int grado) {
        var seccion = new Seccion();
        seccion.setNombre("Alternativa " + grado);
        seccion.setGrado(grado);
        seccion.setAnioAcademico(2091);
        seccion.setCapacidadMaxima(5);
        em.persist(seccion);
        em.flush();
        return seccion;
    }

    @Test
    void noPermiteOtraFamiliaDuplicadosNiUsarIngresoParaEvitarLaRevision() throws Exception {
        var otra = otraFamilia();
        familiaRenovacion(otra.apoderado(), true, RUTA, datosEnvio(), 403);
        var solicitud = enviar();
        familiaRenovacion(otra.apoderado(), false, ruta(solicitud), null, 403);
        assertThat(familiaRenovacion(otra.apoderado(), false, RUTA, null, 200).path("content")).isEmpty();
        familiaRenovacion(guardian, true, RUTA, datosEnvio(), 409);
        var problema = familiaRenovacion(guardian, true, "/api/solicitudes-matricula",
                data("alumnoId", student.getId(), "anioAcademico", 2091, "grado", 2), 422);
        assertThat(problema.path("code").asText()).isEqualTo("RENOVACION_REQUERIDA");
    }

    @Test
    void permiteReintentarConNuevoTramiteTrasResolverElMotivo() throws Exception {
        var primera = enviar();
        verificarRechazo(primera, "GRADO_NO_APROBADO", "No se acreditó");
        promocion("PROMOVIDO");
        var nueva = enviar();
        assertThat(nueva.path("id")).isNotEqualTo(primera.path("id"));
        assertThat(familiaRenovacion(guardian, false, RUTA + "?estado=RECHAZADA", null, 200).path("content")).hasSize(1);
    }

    @Test
    void rechazoAdministrativoRequiereMotivoYVersion() throws Exception {
        var solicitud = enviar();
        String path = ruta(solicitud) + "/rechazar";
        request("POST", path, data("version", solicitud.path("version").asLong(), "motivo", " "), 400);
        request("POST", path, data("version", 999, "motivo", "Solicitud observada por administración."), 409);
        var respuesta = request("POST", path,
                data("version", solicitud.path("version").asLong(), "motivo", "Solicitud duplicada en revisión documental."), 200);
        assertThat(respuesta.path("codigoMotivo").asText()).isEqualTo("RECHAZO_ADMINISTRATIVO");
        assertThat(respuesta.path("estado").asText()).isEqualTo("RECHAZADA");
    }

    private void promocion(String resultado) throws Exception {
        var matriculas = getOk(RUTA + "/promociones?busqueda=" + student.getCodigoAlumno()).path("content");
        assertThat(matriculas).hasSize(1);
        var registro = putOk(RUTA + "/promociones/" + anterior.getId(), data(
                "version", matriculas.get(0).path("version").asLong(),
                "resultado", resultado, "motivo", "Resultado según acta de fin de año."));
        assertThat(registro.path("promocion").asText()).isEqualTo(resultado);
    }

    private JsonNode enviar() throws Exception {
        return familiaRenovacion(guardian, true, RUTA, datosEnvio(), 201);
    }

    private Object datosEnvio() {
        return data("idMatriculaAnterior", anterior.getId(), "idSeccion", destino.getId());
    }

    private String ruta(JsonNode solicitud) {
        return RUTA + "/" + solicitud.path("id").asLong();
    }

    private JsonNode decidir(JsonNode solicitud, String firma) throws Exception {
        return request("POST", ruta(solicitud) + "/aprobar",
                data("version", solicitud.path("version").asLong(), "firmaPlan", firma), 200);
    }

    private void verificarRechazo(JsonNode solicitud, String codigo, String motivo) throws Exception {
        var resuelta = decidir(solicitud, null);
        assertThat(resuelta.path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(resuelta.path("codigoMotivo").asText()).isEqualTo(codigo);
        assertThat(resuelta.path("motivo").asText()).contains(motivo);
        assertThat(getOk(ruta(solicitud)).path("motivo")).isEqualTo(resuelta.path("motivo"));
        assertThat(contarDestino("Matricula m", "m.anioAcademico")
                - (codigo.equals("SECCION_SIN_VACANTES") ? 1 : 0)).isZero();
        assertThat(contarDestino("CompromisoPago m", "m.matricula.anioAcademico")).isZero();
    }

    private long contarDestino(String origen, String campo) {
        return em.createQuery("select count(m) from " + origen + " where " + campo + " = 2091", Long.class).getSingleResult();
    }

    private void unaFamiliaNoPuedeDecidir(JsonNode solicitud) throws Exception {
        familiaRenovacion(guardian, true, ruta(solicitud) + "/aprobar", data("version", 0), 403);
        familiaRenovacion(guardian, false, ruta(solicitud) + "/plan", null, 403);
    }

    private JsonNode familiaRenovacion(Apoderado apoderado, boolean escritura, String path, Object body, int esperado)
            throws Exception {
        var req = (escritura ? post(path) : get(path)).with(jwt()
                .jwt(j -> j.subject("familiar").claim("personaId", apoderado.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"), new SimpleGrantedAuthority("MATRICULA_LEER"),
                        new SimpleGrantedAuthority("SOLICITUDES_ESCRIBIR")));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return json.readTree(mvc.perform(req).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsByteArray());
    }

    private void ocuparDestino() {
        var otra = otraFamilia();
        var solicitud = new SolicitudMatricula();
        solicitud.setAlumno(otra.alumno());
        solicitud.setSeccion(destino);
        solicitud.setGrado(2);
        solicitud.setAnioAcademico(2091);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        var nueva = new Matricula();
        nueva.setAlumno(otra.alumno());
        nueva.setAnioAcademico(2091);
        nueva.setCodigoMatricula("OCUPADA-" + key());
        nueva.setFechaMatricula(Instant.now());
        nueva.setSeccion(destino);
        nueva.setSolicitudMatricula(solicitud);
        nueva.setEstado(EstadoMatricula.ACTIVA);
        em.persist(nueva);
        em.flush();
    }
}
