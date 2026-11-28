// correccion marcacion docente
package com.asiscontrol.controller;

import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * US-32: el administrador corrige la hora o el estado de una marcación docente existente con motivo;
 * se conserva el valor anterior, queda la auditoría y el reporte muestra el valor corregido.
 */
class CorreccionMarcacionDocenteHttpTest extends ApiIntegrationSupport {
    @Autowired
    private Clock reloj;
    private LocalDate fechaJornada;

    @BeforeEach
    void prepararFechaJornada() {
        fechaJornada = LocalDate.now(reloj).minusDays(1);
    }

    private AsistenciaDocente marcacion() {
        var a = new AsistenciaDocente();
        a.setDocente(teacher);
        a.setFechaJornada(fechaJornada);
        a.setFechaHoraMarcada(fechaJornada.atTime(8, 10));
        a.setLimitePuntualidad(fechaJornada.atTime(8, 0));
        a.setVentanaApertura(fechaJornada.atTime(7, 0));
        a.setVentanaCierre(fechaJornada.atTime(14, 0));
        a.setEstado(EstadoAsistencia.TARDANZA);
        em.persist(a);
        em.flush();
        return a;
    }

    private String ruta(AsistenciaDocente a) {
        return "/api/asistencias/docentes/" + a.getId();
    }

    @Test void corregirLaHoraRecalculaElEstadoConservaElAnteriorYActualizaElReporte() throws Exception {
        var a = marcacion();
        var corregida = putOk(ruta(a) + "/correccion", data("hora", "07:55", "motivo", "  Falla del lector QR  ",
                "version", a.getVersion()));
        assertThat(corregida.path("estado").asText()).isEqualTo("PRESENTE");
        assertThat(corregida.path("fechaHoraMarcada").asText()).isEqualTo(fechaJornada + "T07:55:00");

        var respuesta = getOk(ruta(a) + "/correcciones");
        assertThat(respuesta.path("marcacion").path("estado").asText()).isEqualTo("PRESENTE");
        var historial = respuesta.path("correcciones");
        assertThat(historial).hasSize(1);
        var c = historial.get(0);
        assertThat(c.path("horaAnterior").asText()).isEqualTo(fechaJornada + "T08:10:00");
        assertThat(c.path("estadoAnterior").asText()).isEqualTo("TARDANZA");
        assertThat(c.path("horaNueva").asText()).isEqualTo(fechaJornada + "T07:55:00");
        assertThat(c.path("estadoNuevo").asText()).isEqualTo("PRESENTE");
        assertThat(c.path("motivo").asText()).isEqualTo("Falla del lector QR");
        assertThat(c.path("actor").asText()).isNotBlank();

        var registros = getOk("/api/reportes/asistencia?tipo=DOCENTES").path("registros");
        var fila = StreamSupport.stream(registros.spliterator(), false)
                .filter(r -> r.path("id").asLong() == a.getId()).findFirst().orElseThrow();
        assertThat(fila.path("estado").asText()).isEqualTo("PRESENTE");
        assertThat(fila.path("hora").asText()).startsWith("07:55");

        var auditoria = getOk("/api/auditoria/registros?modulo=ASISTENCIA&size=50").path("content");
        assertThat(auditoria.findValuesAsText("accion")).contains("CORREGIR_ASISTENCIA_DOCENTE");
        assertThat(auditoria.findValuesAsText("motivo")).contains("Falla del lector QR");
    }

    @Test void elEstadoElegidoSeRespetaAunqueLaHoraNoCambie() throws Exception {
        var a = marcacion();
        var corregida = putOk(ruta(a) + "/correccion", data("estado", "PRESENTE", "motivo", "Autorizado por dirección",
                "version", a.getVersion()));
        assertThat(corregida.path("estado").asText()).isEqualTo("PRESENTE");
        assertThat(corregida.path("fechaHoraMarcada").asText()).isEqualTo(fechaJornada + "T08:10:00");
    }

    @Test void rechazaSinMotivoSinCambiosEstadoInvalidoYVersionVieja() throws Exception {
        var a = marcacion();
        request("PUT", ruta(a) + "/correccion", data("estado", "PRESENTE", "motivo", "  ", "version", a.getVersion()), 400);
        assertThat(request("PUT", ruta(a) + "/correccion", data("hora", "08:10", "estado", "TARDANZA",
                "motivo", "Sin cambio", "version", a.getVersion()), 422).path("code").asText())
                .isEqualTo("CORRECCION_SIN_CAMBIO");
        assertThat(request("PUT", ruta(a) + "/correccion", data("estado", "TARDANZA_JUSTIFICADA",
                "motivo", "Se justificó", "version", a.getVersion()), 422).path("code").asText())
                .isEqualTo("CORRECCION_ESTADO_INVALIDO");
        assertThat(request("PUT", ruta(a) + "/correccion", data("estado", "PRESENTE",
                "motivo", "Versión vieja", "version", a.getVersion() + 5), 409).path("code").asText())
                .isEqualTo("VERSION_DESACTUALIZADA");
        assertThat(getOk(ruta(a) + "/correcciones").path("correcciones")).isEmpty();
    }

    @Test void puedeCorregirseAInasistencia() throws Exception {
        var a = marcacion();
        var corregida = putOk(ruta(a) + "/correccion", data("estado", "INASISTENCIA", "motivo", "Marcó y se retiró",
                "version", a.getVersion()));
        assertThat(corregida.path("estado").asText()).isEqualTo("INASISTENCIA");
    }

    @Test void sinVentanaGuardadaUsaLaDeLaJornadaYSinJornadaPideElEstado() throws Exception {
        var a = marcacion();
        a.setLimitePuntualidad(null); a.setVentanaApertura(null); a.setVentanaCierre(null); a.setContingencia(true);
        a.setEstado(EstadoAsistencia.PRESENTE); a.setFechaHoraMarcada(fechaJornada.atTime(7, 50));
        em.flush();
        assertThat(request("PUT", ruta(a) + "/correccion", data("hora", "22:00", "motivo", "Llegó de noche",
                "version", a.getVersion()), 422).path("code").asText()).isEqualTo("CORRECCION_ESTADO_REQUERIDO");

        var jornada = new com.asiscontrol.entity.JornadaMarcacionDocente();
        jornada.setDocente(teacher); jornada.setFecha(fechaJornada); jornada.setApertura(fechaJornada.atTime(7, 0));
        jornada.setLimitePuntualidad(fechaJornada.atTime(7, 45)); jornada.setCierre(fechaJornada.atTime(23, 0));
        em.persist(jornada); em.flush();
        var corregida = putOk(ruta(a) + "/correccion", data("hora", "22:00", "motivo", "Llegó de noche",
                "version", a.getVersion()));
        assertThat(corregida.path("estado").asText()).isEqualTo("TARDANZA");
    }

    @Test void soloElAdministradorCorrige() throws Exception {
        var a = marcacion();
        var cuerpo = json.writeValueAsString(data("estado", "PRESENTE", "motivo", "Intento", "version", a.getVersion()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(ruta(a) + "/correccion")
                        .contentType("application/json").content(cuerpo)
                        .with(jwt().jwt(j -> j.subject("DOC" + key()).claim("personaId", teacher.getPersona().getId()))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                                        new SimpleGrantedAuthority("ASISTENCIA_LEER"),
                                        new SimpleGrantedAuthority("ASISTENCIA_ESCRIBIR")))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
