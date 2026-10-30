package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.SolicitudMatricula;
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
    void conservaAccesoDelAdministrador() throws Exception {
        evento("SolicitudMatricula", ajena.getId(), "GUARDAR_SOLICITUD", "2026-10-30T09:00:00Z");
        assertThat(consultar(ajena.getId(), asAdmin(), 200)).hasSize(1);
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

    private void evento(String entidad, Long id, String accion, String fecha) {
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
    }
}
