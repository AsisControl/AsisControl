package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.enums.Parentesco;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Selector de estudiante en Pagos: Obligaciones, Historial y Seguimiento se filtran por hijo o se consolidan. */
class PagosPorEstudianteHttpTest extends PagosFamiliaIntegrationSupport {
    private static final String HISTORIAL = "/api/apoderado/comprobantes";
    private static final String ORDENES = PAGOS + "/ordenes";

    /** Un segundo hijo del apoderado de prueba, con su propia matrícula. */
    private Alumno segundoHijo() {
        Alumno hijo = otraFamilia().alumno();
        var vinculo = new AlumnoApoderado();
        vinculo.setAlumno(hijo);
        vinculo.setApoderado(guardian);
        vinculo.setParentesco(Parentesco.MADRE);
        vinculo.setPrincipal(true);
        em.persist(vinculo);
        em.flush();
        return hijo;
    }

    private int total(String path) throws Exception {
        return familia("GET", path, null, 200).path("totalElements").asInt();
    }

    @Test
    void obligacionesHistorialYSeguimientoSeFiltranPorEstudianteOSeConsolidan() throws Exception {
        Alumno hijo = segundoHijo();
        var delPrimero = compromiso(1, "350.00");
        var delSegundo = compromiso(hijo, 1, "200.00", "0.00", LocalDate.now(LIMA).plusDays(10));
        var otroDelSegundo = compromiso(hijo, 2, "200.00", "0.00", LocalDate.now(LIMA).plusDays(40));
        confirmar(iniciar("TARJETA", delPrimero.getId()), "APROBADO");
        confirmar(iniciar("TARJETA", delSegundo.getId()), "APROBADO");
        JsonNode pendiente = iniciar("QR", otroDelSegundo.getId());

        // Estado de cuenta consolidado: todo lo de la familia.
        assertThat(total(OBLIGACIONES)).isEqualTo(3);
        assertThat(total(HISTORIAL)).isEqualTo(2);
        assertThat(total(ORDENES)).isEqualTo(3);

        // Solo el primer hijo.
        String primero = "?idAlumno=" + student.getId();
        assertThat(total(OBLIGACIONES + primero)).isEqualTo(1);
        JsonNode historial = familia("GET", HISTORIAL + primero, null, 200);
        assertThat(historial.path("totalElements").asInt()).isEqualTo(1);
        assertThat(historial.path("content").get(0).path("codigoCompromiso").asText())
                .isEqualTo(delPrimero.getCodigoCompromiso());
        assertThat(total(ORDENES + primero)).isEqualTo(1);

        // Solo el segundo hijo, incluido su pago pendiente.
        String segundo = "?idAlumno=" + hijo.getId();
        assertThat(total(OBLIGACIONES + segundo)).isEqualTo(2);
        assertThat(total(HISTORIAL + segundo)).isEqualTo(1);
        JsonNode ordenes = familia("GET", ORDENES + segundo, null, 200);
        assertThat(ordenes.path("totalElements").asInt()).isEqualTo(2);
        assertThat(ordenes.path("content").findValuesAsText("codigo")).contains(pendiente.path("codigo").asText());
    }

    @Test
    void unPagoConCuotasDeVariosHijosApareceEnElSeguimientoDeCadaUno() throws Exception {
        Alumno hijo = segundoHijo();
        var delPrimero = compromiso(1, "350.00");
        var delSegundo = compromiso(hijo, 1, "200.00", "0.00", LocalDate.now(LIMA).plusDays(10));
        JsonNode orden = iniciar("TARJETA", delPrimero.getId(), delSegundo.getId());
        confirmar(orden, "APROBADO");
        assertThat(total(ORDENES + "?idAlumno=" + student.getId())).isEqualTo(1);
        assertThat(total(ORDENES + "?idAlumno=" + hijo.getId())).isEqualTo(1);
        assertThat(total(HISTORIAL + "?idAlumno=" + student.getId())).isEqualTo(1);
        assertThat(total(HISTORIAL + "?idAlumno=" + hijo.getId())).isEqualTo(1);
    }

    @Test
    void noPermiteConsultarElHistorialNiElSeguimientoDeUnEstudianteAjeno() throws Exception {
        var ajena = otraFamilia();
        compromiso(1, "350.00");
        String filtro = "?idAlumno=" + ajena.alumno().getId();
        familia("GET", HISTORIAL + filtro, null, 403);
        familia("GET", ORDENES + filtro, null, 403);
        familia("GET", OBLIGACIONES + filtro, null, 403);
    }
}
