package com.asiscontrol.controller;

import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.service.SaldoCompromisoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-44: estados Pendiente, Parcial, Pagado y Vencido, consultas filtradas e indicadores de morosidad. */
class CompromisoEstadosMorosidadHttpTest extends ApiIntegrationSupport {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");
    private static final String PATH = "/api/compromisos-pago";
    private static final String TRANSACCIONES = "/api/transacciones-pago";

    @Autowired SaldoCompromisoService saldos;

    private int cuota;

    private LocalDate hoy() {
        return LocalDate.now(ZONA_NEGOCIO);
    }

    private JsonNode emitir(String monto, LocalDate vencimiento) throws Exception {
        Long matriculaId = em.createQuery("select m.id from Matricula m where m.alumno.id = :alumno", Long.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        return postOk(PATH, data("idMatricula", matriculaId, "concepto", "Pensión escolar",
                "numeroCuota", ++cuota, "montoTotal", new BigDecimal(monto), "moneda", "PEN",
                "fechaVencimiento", vencimiento));
    }

    private JsonNode pagar(JsonNode compromiso, String monto) throws Exception {
        return postOk(TRANSACCIONES, data("idCompromisoPago", compromiso.path("id").asLong(),
                "idApoderado", guardian.getId(), "numeroOperacion", "OP-" + key(),
                "monto", new BigDecimal(monto), "medioPago", MedioPago.EFECTIVO));
    }

    /** Transacción que no llegó a aprobarse (pago en línea pendiente, rechazado o expirado). */
    private TransaccionPago transaccionNoAprobada(JsonNode compromiso, String monto, EstadoTransaccionPago estado) {
        TransaccionPago transaccion = new TransaccionPago();
        transaccion.setCompromisoPago(em.find(CompromisoPago.class, compromiso.path("id").asLong()));
        transaccion.setApoderado(guardian);
        transaccion.setNumeroOperacion("NA-" + key());
        transaccion.setMonto(new BigDecimal(monto));
        transaccion.setMedioPago(MedioPago.EFECTIVO);
        transaccion.setEstado(estado);
        transaccion.setFechaTransaccion(Instant.now());
        em.persist(transaccion);
        em.flush();
        return transaccion;
    }

    private JsonNode consultar(JsonNode compromiso) throws Exception {
        return getOk(PATH + "/" + compromiso.path("id").asLong());
    }

    private String filtroAlumno() {
        return "?idAlumno=" + student.getId();
    }

    private Map<String, JsonNode> porEstado(JsonNode resumen) {
        Map<String, JsonNode> estados = new HashMap<>();
        resumen.path("porEstado").forEach(fila -> estados.put(fila.path("estado").asText(), fila));
        return estados;
    }

    @Test
    void activoSinPagosAprobadosYNoVencidoSeMuestraPendiente() throws Exception {
        JsonNode compromiso = consultar(emitir("300.00", hoy().plusDays(10)));
        assertThat(compromiso.path("estadoFinanciero").asText()).isEqualTo("PENDIENTE");
        assertThat(compromiso.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(compromiso.path("montoPagado").decimalValue()).isZero();
        assertThat(compromiso.path("saldo").decimalValue()).isEqualByComparingTo("300.00");
    }

    @Test
    void pagoAprobadoMenorQueElMontoSeMuestraParcialConSaldo() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().plusDays(10));
        pagar(emitido, "120.50");
        JsonNode compromiso = consultar(emitido);
        assertThat(compromiso.path("estadoFinanciero").asText()).isEqualTo("PARCIAL");
        assertThat(compromiso.path("montoPagado").decimalValue()).isEqualByComparingTo("120.50");
        assertThat(compromiso.path("saldo").decimalValue()).isEqualByComparingTo("179.50");
    }

    @Test
    void pagosAprobadosQueCubrenElMontoSeMuestranPagadoConSaldoCero() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().plusDays(10));
        pagar(emitido, "100.00");
        pagar(emitido, "200.00");
        JsonNode compromiso = consultar(emitido);
        assertThat(compromiso.path("estadoFinanciero").asText()).isEqualTo("PAGADO");
        assertThat(compromiso.path("saldo").decimalValue()).isZero();
        assertThat(request("POST", TRANSACCIONES, data("idCompromisoPago", emitido.path("id").asLong(),
                "idApoderado", guardian.getId(), "numeroOperacion", "OP-" + key(),
                "monto", new BigDecimal("1.00"), "medioPago", MedioPago.EFECTIVO), 422)
                .path("code").asText()).isEqualTo("COMPROMISO_NO_ADMITE_PAGOS");
    }

    @Test
    void despuesDelVencimientoConSaldoSeMuestraVencidoInclusoSiTienePagoParcial() throws Exception {
        JsonNode sinPagos = emitir("300.00", hoy().minusDays(1));
        JsonNode conAbono = emitir("300.00", hoy().minusDays(5));
        pagar(conAbono, "50.00");
        JsonNode venceHoy = emitir("300.00", hoy());
        assertThat(consultar(sinPagos).path("estadoFinanciero").asText()).isEqualTo("VENCIDO");
        JsonNode abonado = consultar(conAbono);
        assertThat(abonado.path("estadoFinanciero").asText()).isEqualTo("VENCIDO");
        assertThat(abonado.path("saldo").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(consultar(venceHoy).path("estadoFinanciero").asText()).isEqualTo("PENDIENTE");
        assertThat(getOk(PATH + filtroAlumno() + "&estado=VENCIDO").path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void transaccionesPendientesRechazadasExpiradasYAnuladasNoSeAplicanAlSaldo() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().plusDays(10));
        transaccionNoAprobada(emitido, "300.00", EstadoTransaccionPago.PENDIENTE);
        transaccionNoAprobada(emitido, "300.00", EstadoTransaccionPago.RECHAZADA);
        transaccionNoAprobada(emitido, "300.00", EstadoTransaccionPago.EXPIRADA);
        transaccionNoAprobada(emitido, "300.00", EstadoTransaccionPago.ANULADA);
        pagar(emitido, "40.00");
        JsonNode compromiso = consultar(emitido);
        assertThat(compromiso.path("estadoFinanciero").asText()).isEqualTo("PARCIAL");
        assertThat(compromiso.path("montoPagado").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(compromiso.path("saldo").decimalValue()).isEqualByComparingTo("260.00");
    }

    @Test
    void filtraPorAnioGradoSeccionEstudianteEstadoYVencimientoMostrandoMontosPagosYSaldos() throws Exception {
        JsonNode marzo = emitir("300.00", hoy().plusDays(10));
        emitir("200.00", hoy().plusDays(40));
        pagar(marzo, "100.00");
        String base = filtroAlumno() + "&anioAcademico=2090&grado=" + section.getGrado() + "&idSeccion=" + section.getId();
        JsonNode pagina = getOk(PATH + base + "&estado=PARCIAL&vencimientoDesde=" + hoy()
                + "&vencimientoHasta=" + hoy().plusDays(20));
        assertThat(pagina.path("totalElements").asInt()).isEqualTo(1);
        JsonNode fila = pagina.path("content").get(0);
        assertThat(fila.path("montoTotal").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(fila.path("montoPagado").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(fila.path("saldo").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(fila.path("grado").asInt()).isEqualTo(section.getGrado());
        assertThat(fila.path("seccion").asText()).isEqualTo(section.getNombre());
        assertThat(getOk(PATH + base).path("totalElements").asInt()).isEqualTo(2);
        assertThat(getOk(PATH + base + "&vencimientoDesde=" + hoy().plusDays(30)).path("totalElements").asInt())
                .isEqualTo(1);
        assertThat(getOk(PATH + filtroAlumno() + "&grado=" + (section.getGrado() + 1)).path("totalElements").asInt())
                .isZero();
        assertThat(getOk(PATH + filtroAlumno() + "&idSeccion=" + Long.MAX_VALUE).path("totalElements").asInt())
                .isZero();
    }

    @Test
    void resumenCalculaCantidadesYMontosPorEstadoEIndicadoresDeMorosidad() throws Exception {
        emitir("300.00", hoy().plusDays(10));                       // Pendiente
        pagar(emitir("300.00", hoy().plusDays(10)), "100.00");      // Parcial
        pagar(emitir("150.00", hoy().minusDays(3)), "150.00");      // Pagado aunque ya venció
        emitir("200.00", hoy().minusDays(2));                       // Vencido
        pagar(emitir("250.00", hoy().minusDays(1)), "50.00");       // Vencido con abono

        JsonNode resumen = getOk(PATH + "/resumen" + filtroAlumno());
        Map<String, JsonNode> estados = porEstado(resumen);
        assertThat(estados.get("PENDIENTE").path("cantidad").asLong()).isEqualTo(1);
        assertThat(estados.get("PARCIAL").path("cantidad").asLong()).isEqualTo(1);
        assertThat(estados.get("PARCIAL").path("saldo").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(estados.get("PAGADO").path("cantidad").asLong()).isEqualTo(1);
        assertThat(estados.get("PAGADO").path("saldo").decimalValue()).isZero();
        assertThat(estados.get("VENCIDO").path("cantidad").asLong()).isEqualTo(2);
        assertThat(estados.get("VENCIDO").path("montoTotal").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(estados.get("VENCIDO").path("montoPagado").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(estados.get("VENCIDO").path("saldo").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(resumen.path("cantidad").asLong()).isEqualTo(5);
        assertThat(resumen.path("montoTotal").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(resumen.path("montoPagado").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(resumen.path("saldo").decimalValue()).isEqualByComparingTo("900.00");
        assertThat(resumen.path("morosidad").path("montoVencido").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(resumen.path("morosidad").path("compromisosVencidos").asLong()).isEqualTo(2);
        assertThat(resumen.path("morosidad").path("estudiantesMorosos").asLong()).isEqualTo(1);

        JsonNode soloVencidos = getOk(PATH + "/resumen" + filtroAlumno() + "&estado=VENCIDO");
        assertThat(soloVencidos.path("cantidad").asLong()).isEqualTo(2);
        assertThat(porEstado(soloVencidos).get("PENDIENTE").path("cantidad").asLong()).isZero();
    }

    @Test
    void detalleMuestraLaSecuenciaDeTransaccionesYComprobantesInternos() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().plusDays(10));
        transaccionNoAprobada(emitido, "300.00", EstadoTransaccionPago.RECHAZADA);
        JsonNode primerPago = pagar(emitido, "100.00");
        pagar(emitido, "50.00");
        JsonNode comprobante = postOk("/api/comprobantes", data("idTransaccionPago", primerPago.path("id").asLong(),
                "tipoComprobante", "BOLETA", "serie", "B001", "numero", key()));

        JsonNode detalle = getOk(PATH + "/" + emitido.path("id").asLong() + "/detalle");
        assertThat(detalle.path("compromiso").path("estadoFinanciero").asText()).isEqualTo("PARCIAL");
        JsonNode movimientos = detalle.path("movimientos");
        assertThat(movimientos).hasSize(3);
        assertThat(movimientos.get(0).path("secuencia").asInt()).isEqualTo(1);
        assertThat(movimientos.get(0).path("transaccion").path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(movimientos.get(0).path("aplicaAlSaldo").asBoolean()).isFalse();
        assertThat(movimientos.get(0).path("saldoResultante").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(movimientos.get(1).path("aplicaAlSaldo").asBoolean()).isTrue();
        assertThat(movimientos.get(1).path("saldoResultante").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(movimientos.get(1).path("comprobante").path("id").asLong())
                .isEqualTo(comprobante.path("id").asLong());
        assertThat(movimientos.get(2).path("pagadoAcumulado").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(movimientos.get(2).path("saldoResultante").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(movimientos.get(2).path("comprobante").isNull()).isTrue();

        var apoderado = jwt().jwt(token -> token.claim("personaId", guardian.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"), new SimpleGrantedAuthority("PAGOS_LEER"));
        mvc.perform(get(PATH + "/" + emitido.path("id").asLong() + "/detalle").with(apoderado))
                .andExpect(status().isForbidden());
    }

    @Test
    void alAprobarseOCorregirseUnaTransaccionSeRecalculanSaldoEIndicadores() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().minusDays(1));
        TransaccionPago enLinea = transaccionNoAprobada(emitido, "300.00", EstadoTransaccionPago.PENDIENTE);
        assertThat(getOk(PATH + "/resumen" + filtroAlumno()).path("morosidad").path("estudiantesMorosos").asLong())
                .isEqualTo(1);

        CompromisoPago compromiso = em.find(CompromisoPago.class, emitido.path("id").asLong());
        enLinea.setEstado(EstadoTransaccionPago.APROBADA);
        saldos.recalcular(compromiso);
        em.flush();
        assertThat(consultar(emitido).path("estadoFinanciero").asText()).isEqualTo("PAGADO");
        JsonNode resumen = getOk(PATH + "/resumen" + filtroAlumno());
        assertThat(resumen.path("morosidad").path("estudiantesMorosos").asLong()).isZero();
        assertThat(resumen.path("morosidad").path("montoVencido").decimalValue()).isZero();

        enLinea.setEstado(EstadoTransaccionPago.ANULADA);
        saldos.recalcular(compromiso);
        em.flush();
        JsonNode corregido = consultar(emitido);
        assertThat(corregido.path("estadoFinanciero").asText()).isEqualTo("VENCIDO");
        assertThat(corregido.path("saldo").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(getOk(PATH + "/resumen" + filtroAlumno()).path("morosidad").path("montoVencido").decimalValue())
                .isEqualByComparingTo("300.00");
    }

    @Test
    void transaccionesSeFiltranPorEstudianteEstadoYFechaConDatosDelCompromiso() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().plusDays(10));
        pagar(emitido, "100.00");
        TransaccionPago rechazada = transaccionNoAprobada(emitido, "50.00", EstadoTransaccionPago.RECHAZADA);
        rechazada.setFechaTransaccion(hoy().minusDays(10).atStartOfDay(ZONA_NEGOCIO).plusHours(9).toInstant());
        em.flush();

        String porAlumno = TRANSACCIONES + "?idAlumno=" + student.getId();
        assertThat(getOk(porAlumno).path("totalElements").asInt()).isEqualTo(2);
        JsonNode aprobadas = getOk(porAlumno + "&estado=APROBADA");
        assertThat(aprobadas.path("totalElements").asInt()).isEqualTo(1);
        JsonNode fila = aprobadas.path("content").get(0);
        assertThat(fila.path("alumno").asText()).isNotBlank();
        assertThat(fila.path("codigoAlumno").asText()).isEqualTo(student.getCodigoAlumno());
        assertThat(fila.path("codigoCompromiso").asText()).isEqualTo(emitido.path("codigoCompromiso").asText());
        assertThat(fila.path("concepto").asText()).isEqualTo("Pensión escolar");

        String dia = hoy().minusDays(10).toString();
        JsonNode delDia = getOk(porAlumno + "&fechaDesde=" + dia + "&fechaHasta=" + dia);
        assertThat(delDia.path("totalElements").asInt()).isEqualTo(1);
        assertThat(delDia.path("content").get(0).path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(getOk(TRANSACCIONES + "?busqueda=" + student.getCodigoAlumno()).path("totalElements").asInt())
                .isEqualTo(2);
        assertThat(request("GET", porAlumno + "&fechaDesde=" + hoy() + "&fechaHasta=" + hoy().minusDays(1), null, 422)
                .path("code").asText()).isEqualTo("RANGO_FECHAS_INVALIDO");
    }

    @Test
    void rechazaQueUnAdministradorCambieElEstadoFinanciero() throws Exception {
        JsonNode emitido = emitir("300.00", hoy().plusDays(10));
        String path = PATH + "/" + emitido.path("id").asLong();
        for (String metodo : new String[]{"PUT", "PATCH", "POST"}) {
            assertThat(request(metodo, path + "/estado", data("estado", "PAGADO"), 422).path("code").asText())
                    .isEqualTo("ESTADO_FINANCIERO_CALCULADO");
        }
        var cambio = data("montoTotal", 300, "fechaVencimiento", hoy().plusDays(20),
                "motivo", "Marcar como pagado", "version", emitido.path("version").asLong(), "estado", "PAGADO");
        assertThat(request("PUT", path, cambio, 422).path("code").asText()).isEqualTo("ESTADO_FINANCIERO_CALCULADO");
        cambio.remove("estado");
        cambio.put("montoPagado", 300);
        assertThat(request("PUT", path, cambio, 422).path("code").asText()).isEqualTo("ESTADO_FINANCIERO_CALCULADO");
        JsonNode sinCambios = consultar(emitido);
        assertThat(sinCambios.path("estadoFinanciero").asText()).isEqualTo("PENDIENTE");
        assertThat(sinCambios.path("version").asLong()).isEqualTo(emitido.path("version").asLong());
    }
}
