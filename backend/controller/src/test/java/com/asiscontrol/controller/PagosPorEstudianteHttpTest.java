package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.OrdenPago;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.Comprobante;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.entity.enums.TipoComprobante;
import com.asiscontrol.entity.enums.Parentesco;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;

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
        assertThat(total(ORDENES)).isEqualTo(1);
        assertThat(familia("GET", ORDENES + "?size=1", null, 200).path("totalPages").asInt()).isEqualTo(1);

        // Solo el primer hijo.
        String primero = "?idAlumno=" + student.getId();
        assertThat(total(OBLIGACIONES + primero)).isEqualTo(1);
        JsonNode historial = familia("GET", HISTORIAL + primero, null, 200);
        assertThat(historial.path("totalElements").asInt()).isEqualTo(1);
        assertThat(historial.path("content").get(0).path("codigoCompromiso").asText())
                .isEqualTo(delPrimero.getCodigoCompromiso());
        assertThat(total(ORDENES + primero)).isZero();

        // Solo el segundo hijo, incluido su pago pendiente.
        String segundo = "?idAlumno=" + hijo.getId();
        assertThat(total(OBLIGACIONES + segundo)).isEqualTo(2);
        assertThat(total(HISTORIAL + segundo)).isEqualTo(1);
        JsonNode ordenes = familia("GET", ORDENES + segundo, null, 200);
        assertThat(ordenes.path("totalElements").asInt()).isEqualTo(1);
        assertThat(ordenes.path("content").findValuesAsText("codigo")).contains(pendiente.path("codigo").asText());
    }

    @Test
    void unPagoConCuotasDeVariosHijosApareceEnElSeguimientoDeCadaUno() throws Exception {
        Alumno hijo = segundoHijo();
        var delPrimero = compromiso(1, "350.00");
        var delSegundo = compromiso(hijo, 1, "200.00", "0.00", LocalDate.now(LIMA).plusDays(10));
        JsonNode orden = iniciar("TARJETA", delPrimero.getId(), delSegundo.getId());
        assertThat(total(ORDENES + "?idAlumno=" + student.getId())).isEqualTo(1);
        assertThat(total(ORDENES + "?idAlumno=" + hijo.getId())).isEqualTo(1);
        confirmar(orden, "APROBADO");
        assertThat(total(ORDENES + "?idAlumno=" + student.getId())).isZero();
        assertThat(total(ORDENES + "?idAlumno=" + hijo.getId())).isZero();
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
        familia("GET", "/api/apoderado/pagos/historial" + filtro, null, 403);
        familia("GET", OBLIGACIONES + filtro, null, 403);
    }

    @Test
    void unaOrdenVencidaSinConsultaPreviaSaleDelSeguimientoYSeConservaEnHistorial() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("QR", propio.getId());
        var entidad = em.find(OrdenPago.class, orden.path("id").asLong());
        entidad.setExpiraEn(Instant.now().minusSeconds(1));
        em.flush();
        assertThat(total(ORDENES)).isZero();
        String filtro = "?idAlumno=" + student.getId() + "&size=1";
        var finalizadas = familia("GET", "/api/apoderado/pagos/historial" + filtro, null, 200);
        assertThat(finalizadas.path("totalElements").asInt()).isEqualTo(1);
        assertThat(finalizadas.path("content").get(0).path("estado").asText()).isEqualTo("VENCIDO");
        assertThat(total(HISTORIAL)).isZero();
    }

    @Test
    void historialUnicoFiltraResultadosAntesDePaginarSinIncluirOrdenesEnCurso() throws Exception {
        var aprobada = compromiso(1, "100.00");
        var rechazada = compromiso(2, "100.00");
        var pendiente = compromiso(3, "100.00");
        confirmar(iniciar("TARJETA", aprobada.getId()), "APROBADO");
        confirmar(iniciar("TARJETA", rechazada.getId()), "RECHAZADO");
        iniciar("QR", pendiente.getId());
        String ruta = "/api/apoderado/pagos/historial";
        assertThat(total(ORDENES)).isEqualTo(1);
        var pagina = familia("GET", ruta + "?size=1", null, 200);
        assertThat(pagina.path("totalElements").asInt()).isEqualTo(2);
        assertThat(pagina.path("totalPages").asInt()).isEqualTo(2);
        var segunda = familia("GET", ruta + "?size=1&page=1", null, 200);
        assertThat(pagina.path("content").get(0).path("id").asLong())
                .isNotEqualTo(segunda.path("content").get(0).path("id").asLong());
        var pagadas = familia("GET", ruta + "?estado=APROBADO&idAlumno=" + student.getId(), null, 200);
        assertThat(pagadas.path("totalElements").asInt()).isEqualTo(1);
        assertThat(pagadas.path("content").get(0).path("idComprobante").isNull()).isFalse();
        var fallidas = familia("GET", ruta + "?estado=RECHAZADO", null, 200);
        assertThat(fallidas.path("totalElements").asInt()).isEqualTo(1);
        assertThat(fallidas.path("content").get(0).path("idComprobante").isNull()).isTrue();
        assertThat(total(ruta + "?estado=VENCIDO")).isZero();
        var otra = otraFamilia();
        assertThat(familia(otra.apoderado(), "GET", ruta, null, 200, null).path("content")).isEmpty();
        familia("GET", ruta + "?estado=PENDIENTE", null, 400);
    }

    @Test
    void historialConservaPagosInstitucionalesConComprobanteSinOrdenDeCheckout() throws Exception {
        var cp = compromiso(1, "100.00");
        var tx = new TransaccionPago();
        tx.setCompromisoPago(cp);
        tx.setNumeroOperacion("PAGO-INSTITUCIONAL-HISTORIAL");
        tx.setMedioPago(MedioPago.EFECTIVO);
        tx.setMonto(new java.math.BigDecimal("100.00"));
        tx.setFechaTransaccion(Instant.now());
        em.persist(tx);
        var comprobante = new Comprobante();
        comprobante.setTransaccionPago(tx);
        comprobante.setSerie("DEMO");
        comprobante.setNumero("HISTORIAL-1");
        comprobante.setTipoComprobante(TipoComprobante.RECIBO);
        comprobante.setMontoTotal(tx.getMonto());
        comprobante.setFechaEmision(Instant.now());
        em.persist(comprobante);
        em.flush();
        var historial = familia("GET", "/api/apoderado/pagos/historial?estado=APROBADO", null, 200);
        assertThat(historial.path("totalElements").asInt()).isEqualTo(1);
        var fila = historial.path("content").get(0);
        assertThat(fila.path("idComprobante").asLong()).isEqualTo(comprobante.getId());
        assertThat(fila.path("idOrden").isNull()).isTrue();
    }
}
