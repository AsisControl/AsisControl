package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ASISCTRL-105: autorización por vínculo familiar y contrato de la línea de tiempo. */
class SolicitudMatriculaHistorialHttpTest extends ApiIntegrationSupport {

    private SolicitudMatricula solicitud;
    private SolicitudMatricula ajena;

    @BeforeEach
    void prepararSolicitudes() {
        solicitud = em.createQuery("from SolicitudMatricula s where s.alumno.id = :alumnoId",
                SolicitudMatricula.class).setParameter("alumnoId", student.getId()).getSingleResult();
        Alumno otroAlumno = new Alumno();
        otroAlumno.setPersona(person());
        otroAlumno.setCodigoAlumno("EST" + key());
        em.persist(otroAlumno);
        ajena = new SolicitudMatricula();
        ajena.setAlumno(otroAlumno);
        ajena.setAnioAcademico(2090);
        ajena.setGrado(1);
        ajena.setEstado(EstadoSolicitudMatricula.BORRADOR);
        em.persist(ajena);
        em.flush();
    }

    @Test
    void apoderadoAsociadoConsultaSoloLaSolicitudAutorizadaEnOrdenCronologico() throws Exception {
        // Se insertan en orden inverso para comprobar la ordenación en la base de datos.
        evento("SolicitudMatricula", solicitud.getId(), "ENVIAR_SOLICITUD", "2026-10-30T10:00:00Z");
        evento("SolicitudMatricula", solicitud.getId(), "GUARDAR_SOLICITUD", "2026-10-30T09:00:00Z");
        evento("SolicitudMatricula", ajena.getId(), "EVENTO_AJENO", "2026-10-30T08:00:00Z");
        evento("Matricula", solicitud.getId(), "OTRA_ENTIDAD", "2026-10-30T08:00:00Z");
        em.flush();
        em.clear();

        JsonNode respuesta = consultar(solicitud.getId(), apoderado(), 200);
        assertThat(respuesta).hasSize(2);
        assertThat(respuesta.findValuesAsText("accion"))
                .containsExactly("GUARDAR_SOLICITUD", "ENVIAR_SOLICITUD");
        assertThat(respuesta.findValuesAsText("fecha"))
                .containsExactly("2026-10-30T09:00:00Z", "2026-10-30T10:00:00Z");
        assertThat(respuesta.get(0).has("anterior")).isTrue();
        assertThat(respuesta.get(0).path("nuevo").asText()).isEqualTo("estado=ENVIADA");
        assertThat(respuesta.get(0).has("correlationId")).isFalse();
        assertThat(respuesta.get(0).has("direccionIp")).isFalse();
    }

    @Test
    void elHistorialDeMatriculaTraeValoresLegiblesSinIdentificadoresInternos() throws Exception {
        Long matricula = em.createQuery("select m.id from Matricula m where m.alumno.id = :alumnoId",
                Long.class).setParameter("alumnoId", student.getId()).getSingleResult();
        RegistroAuditoria creacion = evento(
                "Matricula", matricula, "CREAR_MATRICULA", "2026-10-30T09:00:00Z");
        creacion.setNuevoValor("estado=ACTIVA,seccion=" + section.getId());
        creacion.setActorIdentificador(admin.getUsername());
        RegistroAuditoria cambio = evento(
                "Matricula", matricula, "CAMBIAR_ESTADO_MATRICULA", "2026-10-30T10:00:00Z");
        cambio.setValorAnterior("ACTIVA");
        cambio.setNuevoValor("RETIRADA");
        RegistroAuditoria sinSeccion = evento(
                "Matricula", matricula, "GUARDAR_SOLICITUD", "2026-10-30T11:00:00Z");
        sinSeccion.setNuevoValor("estado=BORRADOR,seccion=null");
        em.flush();

        JsonNode historial = request("GET", "/api/matriculas/" + matricula + "/historial", null, 200);

        JsonNode primero = historial.get(0);
        assertThat(primero.path("actorNombre").asText()).isEqualTo(admin.getPersona().nombreCompleto());
        assertThat(primero.path("campos").findValuesAsText("campo")).containsExactly("estado", "seccion");
        assertThat(primero.path("campos").get(0).path("nuevo").asText()).isEqualTo("ACTIVA");
        assertThat(primero.path("campos").get(1).path("nuevo").asText()).isEqualTo("2090 · 1° A");
        JsonNode segundo = historial.get(1).path("campos").get(0);
        assertThat(segundo.path("campo").asText()).isEqualTo("estado");
        assertThat(segundo.path("anterior").asText()).isEqualTo("ACTIVA");
        assertThat(segundo.path("nuevo").asText()).isEqualTo("RETIRADA");
        assertThat(historial.get(2).path("campos").findValuesAsText("campo")).containsExactly("estado");
    }

    @Test
    void conservaAccesoDelAdministrador() throws Exception {
        evento("SolicitudMatricula", ajena.getId(), "GUARDAR_SOLICITUD", "2026-10-30T09:00:00Z");
        assertThat(consultar(ajena.getId(), asAdmin(), 200)).hasSize(1);
    }

    @Test
    void apoderadoConsultaElHistorialDeLaMatriculaDeSuEstudiante() throws Exception {
        Long matricula = matriculaDe(student);
        RegistroAuditoria creacion = evento("Matricula", matricula, "CREAR_MATRICULA", "2026-10-30T09:00:00Z");
        creacion.setNuevoValor("estado=ACTIVA,seccion=" + section.getId());
        em.flush();

        JsonNode historial = json.readTree(mvc.perform(get("/api/matriculas/" + matricula + "/historial")
                        .with(apoderado()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).path("campos").get(1).path("nuevo").asText()).isEqualTo("2090 · 1° A");
    }

    @Test
    void rechazaElHistorialDeMatriculaDeUnEstudianteNoAsociado() throws Exception {
        Long ajenaMatricula = matricular(ajena);
        mvc.perform(get("/api/matriculas/" + ajenaMatricula + "/historial").with(apoderado()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/matriculas/" + Long.MAX_VALUE + "/historial").with(apoderado()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VINCULO", "APODERADO", "PERSONA_APODERADO", "ALUMNO", "PERSONA_ALUMNO"})
    void rechazaHistorialDeMatriculaConVinculosInactivos(String registro) throws Exception {
        Long matricula = matriculaDe(student);
        switch (registro) {
            case "VINCULO" -> em.createQuery("from AlumnoApoderado v where v.alumno.id = :id",
                    AlumnoApoderado.class).setParameter("id", student.getId())
                    .getSingleResult().setActivo(false);
            case "APODERADO" -> guardian.setActivo(false);
            case "PERSONA_APODERADO" -> guardian.getPersona().setActivo(false);
            case "ALUMNO" -> student.setActivo(false);
            default -> student.getPersona().setActivo(false);
        }
        em.flush();
        mvc.perform(get("/api/matriculas/" + matricula + "/historial").with(apoderado()))
                .andExpect(status().isForbidden());
    }

    @Test
    void otrosRolesNoConsultanElHistorialDeMatricula() throws Exception {
        mvc.perform(get("/api/matriculas/" + matriculaDe(student) + "/historial")
                        .with(identidad("DOCENTE", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elApoderadoFiltraPorSuEstudianteSinVerAjenos() throws Exception {
        JsonNode propias = listado("/api/solicitudes-matricula?alumnoId=" + student.getId());
        assertThat(propias.path("content").findValuesAsText("id")).contains(solicitud.getId().toString());
        JsonNode ajenas = listado("/api/solicitudes-matricula?alumnoId=" + ajena.getAlumno().getId());
        assertThat(ajenas.path("content")).isEmpty();
    }

    @Test
    void permiteConsultarHistorialVacio() throws Exception {
        assertThat(consultar(solicitud.getId(), apoderado(), 200)).isEmpty();
    }

    @Test
    void rechazaApoderadoNoAsociadoAunqueConozcaElId() throws Exception {
        JsonNode respuesta = consultar(ajena.getId(), apoderado(), 403);
        assertThat(respuesta.path("status").asInt()).isEqualTo(403);
        assertThat(respuesta.has("accion")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VINCULO", "APODERADO", "PERSONA_APODERADO", "ALUMNO", "PERSONA_ALUMNO", "SOLICITUD"})
    void rechazaVinculosORegistrosInactivos(String registro) throws Exception {
        switch (registro) {
            case "VINCULO" -> em.createQuery("from AlumnoApoderado v where v.alumno.id = :id",
                    AlumnoApoderado.class).setParameter("id", student.getId())
                    .getSingleResult().setActivo(false);
            case "APODERADO" -> guardian.setActivo(false);
            case "PERSONA_APODERADO" -> guardian.getPersona().setActivo(false);
            case "ALUMNO" -> student.setActivo(false);
            case "PERSONA_ALUMNO" -> student.getPersona().setActivo(false);
            default -> solicitud.setActivo(false);
        }
        em.flush();
        consultar(solicitud.getId(), apoderado(), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCENTE", "ALUMNO"})
    void rechazaOtrosRolesAunqueTenganPermisoDeLectura(String rol) throws Exception {
        consultar(solicitud.getId(), identidad(rol, true), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "APODERADO"})
    void exigePermisoDeLecturaAdemasDelRol(String rol) throws Exception {
        consultar(solicitud.getId(), identidad(rol, false), 403);
    }

    @Test
    void exigeAutenticacion() throws Exception {
        mvc.perform(get(ruta(solicitud.getId()))).andExpect(status().isUnauthorized());
    }

    @Test
    void noRevelaExistenciaDeSolicitudesAjenas() throws Exception {
        consultar(Long.MAX_VALUE, apoderado(), 403);
        consultar(Long.MAX_VALUE, asAdmin(), 404);
    }

    @Test
    void solicitudEliminadaNoEntregaSuHistorialAlAdministrador() throws Exception {
        solicitud.setActivo(false);
        em.flush();
        consultar(solicitud.getId(), asAdmin(), 404);
    }

    private JsonNode listado(String ruta) throws Exception {
        return json.readTree(mvc.perform(get(ruta).with(apoderado()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private Long matriculaDe(Alumno alumno) {
        return em.createQuery("select m.id from Matricula m where m.alumno.id = :alumnoId", Long.class)
                .setParameter("alumnoId", alumno.getId()).getSingleResult();
    }

    private Long matricular(SolicitudMatricula origen) {
        Matricula matricula = new Matricula();
        matricula.setAlumno(origen.getAlumno());
        matricula.setSeccion(section);
        matricula.setSolicitudMatricula(origen);
        matricula.setCodigoMatricula("MAT" + key());
        matricula.setAnioAcademico(2090);
        matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        em.persist(matricula);
        em.flush();
        return matricula.getId();
    }

    private JsonNode consultar(Long id, JwtRequestPostProcessor identidad, int esperado) throws Exception {
        var respuesta = mvc.perform(get(ruta(id)).with(identidad))
                .andExpect(status().is(esperado)).andReturn().getResponse();
        return json.readTree(respuesta.getContentAsByteArray());
    }

    private String ruta(Long id) {
        return "/api/solicitudes-matricula/" + id + "/historial";
    }

    private JwtRequestPostProcessor apoderado() {
        return identidad("APODERADO", true);
    }

    private JwtRequestPostProcessor identidad(String rol, boolean lectura) {
        var identidad = jwt().jwt(token -> token.subject("usuario-prueba")
                .claim("personaId", guardian.getPersona().getId())
                .claim("requiereCambioContrasena", false));
        return lectura
                ? identidad.authorities(new SimpleGrantedAuthority("ROLE_" + rol),
                        new SimpleGrantedAuthority("MATRICULA_LEER"))
                : identidad.authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private RegistroAuditoria evento(String entidad, Long id, String accion, String fecha) {
        RegistroAuditoria evento = new RegistroAuditoria();
        evento.setActorIdentificador("administrador-prueba");
        evento.setRolActor(admin.getRol());
        evento.setEntidad(entidad);
        evento.setRegistroId(id.toString());
        evento.setAccion(accion);
        evento.setModulo("MATRICULA");
        evento.setFechaHora(Instant.parse(fecha));
        evento.setResultado(ResultadoAuditoria.EXITOSO);
        evento.setNuevoValor("estado=ENVIADA");
        evento.setCorrelacionId("prueba-" + key());
        em.persist(evento);
        return evento;
    }
}
