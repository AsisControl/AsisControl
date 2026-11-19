package com.asiscontrol.controller;

import com.asiscontrol.entity.Comprobante;
import com.asiscontrol.entity.OrdenPago;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.entity.enums.TipoComprobante;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComprobantesFamiliaHttpTest extends PagosFamiliaIntegrationSupport {
    private static final String HISTORIAL = "/api/apoderado/comprobantes";

    @Test
    void historialIncluyeComprobantesAnterioresYDeLoteConPaginacionSinDuplicarNiInventar() throws Exception {
        var anterior = legado(1, MedioPago.EFECTIVO);
        var primero = compromiso(2, "350.00");
        var segundo = compromiso(3, "250.00");
        var orden = iniciar("TARJETA", primero.getId(), segundo.getId());
        confirmar(orden, "APROBADO");
        var historial = familia("GET", HISTORIAL + "?size=2&page=0", null, 200);
        assertThat(historial.path("totalElements").asInt()).isEqualTo(3);
        assertThat(historial.path("content")).hasSize(2);
        assertThat(familia("GET", HISTORIAL + "?size=2&page=1", null, 200).path("content")).hasSize(1);
        var legacy = familia("GET", HISTORIAL + "/" + anterior.getId(), null, 200);
        assertThat(legacy.path("codigo").asText()).matches("REC-[0-9]{6,}");
        assertThat(legacy.path("codigoOrden").isNull()).isTrue();
        assertThat(legacy.path("idOrden").isNull()).isTrue();
        assertThat(legacy.path("montoTotal").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(contar("Comprobante")).isEqualTo(3);
        var todos = familia("GET", HISTORIAL + "?size=10", null, 200).path("content");
        int vinculados = 0;
        for (var c : todos) if (!c.path("codigoOrden").isNull()) {
            vinculados++;
            assertThat(c.path("codigoOrden").asText()).isEqualTo(orden.path("codigo").asText());
        }
        assertThat(vinculados).isEqualTo(2);
    }

    @Test
    void historialYDescargaExigenPermisosYAsociacionVigenteNoExponenFamiliasAjenas() throws Exception {
        var anterior = legado(1, MedioPago.YAPE);
        var ajena = otraFamilia();
        assertThat(familia(ajena.apoderado(), "GET", HISTORIAL, null, 200, null).path("content")).isEmpty();
        familia(ajena.apoderado(), "GET", HISTORIAL + "/" + anterior.getId(), null, 403, null);
        mvc.perform(get(HISTORIAL + "/" + anterior.getId() + "/descargar").with(comoApoderado(ajena.apoderado())))
                .andExpect(status().isForbidden());
        publico("GET", HISTORIAL, null, 401);
        mvc.perform(get(HISTORIAL).with(jwt())).andExpect(status().isForbidden());
        familia("GET", HISTORIAL + "/999999999", null, 403);
        em.createQuery("update AlumnoApoderado v set v.activo=false where v.apoderado.id=:id")
                .setParameter("id", guardian.getId()).executeUpdate();
        em.flush(); em.clear();
        assertThat(familia("GET", HISTORIAL, null, 200).path("content")).isEmpty();
        familia("GET", HISTORIAL + "/" + anterior.getId(), null, 403);
    }

    @Test
    void noPublicaComprobantesInactivosNiTransaccionesRechazadasYPermiteRegistroInstitucional() throws Exception {
        var c = legado(1, MedioPago.TRANSFERENCIA_BANCARIA);
        c.getTransaccionPago().setApoderado(null);
        em.flush();
        assertThat(familia("GET", HISTORIAL + "/" + c.getId(), null, 200).path("apoderado").asText())
                .isEqualTo("Registro institucional");
        c.getTransaccionPago().setEstado(EstadoTransaccionPago.RECHAZADA); em.flush();
        assertThat(familia("GET", HISTORIAL, null, 200).path("content")).isEmpty();
        familia("GET", HISTORIAL + "/" + c.getId(), null, 403);
        c.getTransaccionPago().setEstado(EstadoTransaccionPago.APROBADA);
        c.getTransaccionPago().setActivo(false); em.flush();
        familia("GET", HISTORIAL + "/" + c.getId(), null, 403);
        c.getTransaccionPago().setActivo(true); c.setActivo(false); em.flush();
        familia("GET", HISTORIAL + "/" + c.getId(), null, 403);
    }

    @Test
    void descargaProfesionalEscapaDatosIncluyeImportesFechaPeruYReferenciasSinAlterarSerieLegada() throws Exception {
        var c = legado(1, MedioPago.TARJETA_CREDITO);
        c.getTransaccionPago().getCompromisoPago().setConcepto("Pensión <script>mal</script>"); em.flush();
        var response = mvc.perform(get(HISTORIAL + "/" + c.getId() + "/descargar").with(comoApoderado(guardian)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String html = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getHeader("Content-Disposition")).contains("attachment", ".html");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(html).contains("Constancia de pago", "Documento de control institucional", "@media print", "PAGADO", "S/ 350.00", "hora de Perú", "&lt;script&gt;")
                .doesNotContain("<script>", "simulado", "RUC", "SUNAT");
        assertThat(c.getSerie()).isEqualTo("ANTERIOR");
        assertThat(c.getNumero()).startsWith("LEGADO-");
    }

    @Test
    void descargaLegadaConservaMonedaOriginalAunqueCheckoutSoloAdmitaSoles() throws Exception {
        var c = legado(1, MedioPago.PLIN);
        c.getTransaccionPago().getCompromisoPago().setMoneda("USD");
        c.getTransaccionPago().getCompromisoPago().setNumeroCuota(null); em.flush();
        var response = mvc.perform(get(HISTORIAL + "/" + c.getId() + "/descargar").with(comoApoderado(guardian)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("USD 350.00", "Plin")
                .doesNotContain("S/ 350.00", "Soles (PEN)", "Cuota null");
        assertThat(familia("GET", HISTORIAL + "/" + c.getId(), null, 200).path("moneda").asText()).isEqualTo("USD");
    }

    @Test
    void codigoCortoPorIdNoCambiaLaCapacidadQrYConservaCompatibilidadDeOrdenAnterior() throws Exception {
        var c = compromiso(1, "350.00");
        var orden = iniciar("QR", c.getId());
        assertThat(orden.path("codigo").asText()).isEqualTo(String.format("OP-%06d", orden.path("id").asLong()));
        var entidad = em.find(OrdenPago.class, orden.path("id").asLong());
        String referenciaAntigua = entidad.getQrReferencia();
        assertThat(referenciaAntigua).matches("OP-[A-F0-9]{32}");
        String token = orden.path("qrUrl").asText().substring(orden.path("qrUrl").asText().lastIndexOf('/') + 1);
        assertThat(token).startsWith(referenciaAntigua + ".");
        // Simula un registro previo a V12 en el perfil H2 sin Flyway.
        entidad.setCodigo(referenciaAntigua); entidad.setQrReferencia(null); em.flush();
        var repetida = iniciar(guardian, "QR", entidad.getClaveIdempotencia(), c.getId());
        assertThat(repetida.path("codigo")).isEqualTo(orden.path("codigo"));
        assertThat(repetida.path("qrUrl")).isEqualTo(orden.path("qrUrl"));
        assertThat(publico("GET", PAGOS + "/qr/" + token, null, 200).path("codigo")).isEqualTo(orden.path("codigo"));
        publico("POST", PAGOS + "/qr/" + token + "/confirmar", data("accion", "APROBAR"), 200);
        assertThat(c.getMontoPagado()).isEqualByComparingTo("350.00");
    }

    @Test
    void nuevasConstanciasCompartenFolioConSuIdAunqueHayaIntentosRechazados() throws Exception {
        var c = compromiso(1, "350.00");
        confirmar(iniciar("TARJETA", c.getId()), "RECHAZADO");
        var orden = iniciar("TARJETA", c.getId());
        confirmar(orden, "APROBADO");
        var recibo = familia("GET", rutaOrden(orden) + "/comprobante", null, 200);
        var comprobante = recibo.path("comprobantes").get(0);
        assertThat(comprobante.path("serie").asText()).isEqualTo("REC");
        assertThat(comprobante.path("numero").asText()).isEqualTo(String.format("%06d", comprobante.path("id").asLong()));
        assertThat(familia("GET", HISTORIAL, null, 200).path("totalElements").asLong()).isEqualTo(1);
    }

    private Comprobante legado(int cuota, MedioPago medio) {
        var compromiso = compromiso(cuota, "350.00");
        compromiso.setMontoPagado(new BigDecimal("350.00"));
        var tx = new TransaccionPago(); tx.setCompromisoPago(compromiso); tx.setApoderado(guardian);
        tx.setNumeroOperacion("LEGADO-" + UUID.randomUUID()); tx.setMonto(new BigDecimal("350.00"));
        tx.setMedioPago(medio); tx.setEstado(EstadoTransaccionPago.APROBADA); tx.setFechaTransaccion(Instant.now());
        em.persist(tx);
        var c = new Comprobante(); c.setTransaccionPago(tx); c.setTipoComprobante(TipoComprobante.CONSTANCIA_PAGO_DIGITAL);
        c.setSerie("ANTERIOR"); c.setNumero("LEGADO-" + key()); c.setMontoTotal(tx.getMonto()); c.setFechaEmision(tx.getFechaTransaccion());
        em.persist(c); em.flush(); return c;
    }
}
