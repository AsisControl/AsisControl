package com.asiscontrol.controller;

import com.asiscontrol.entity.OrdenPago;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoOrdenPago;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagoApoderadoFlujoHttpTest extends PagosFamiliaIntegrationSupport {
    @Test
    void iniciarCreaIntentosPendientesCalculaSaldoRestanteYNoRegistraPagoTodavia() throws Exception {
        var parcial = compromiso(student, 1, "350.00", "100.00", LocalDate.now(LIMA).plusDays(3));
        var vencido = compromiso(student, 2, "350.00", "0.00", LocalDate.now(LIMA).minusDays(2));
        var orden = iniciar("TARJETA", parcial.getId(), vencido.getId());
        assertThat(orden.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(orden.path("metodo").asText()).isEqualTo("TARJETA");
        assertThat(orden.path("simulador").asBoolean()).isTrue();
        assertThat(orden.path("montoTotal").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(orden.path("items")).hasSize(2);
        assertThat(orden.path("paymentIntentId").asText()).startsWith("pi_sim_");
        assertThat(orden.path("clientSecret").asText()).isNotBlank();
        assertThat(orden.path("qrUrl").isNull()).isTrue();
        var ttl = Duration.between(Instant.parse(orden.path("creadaEn").asText()),
                Instant.parse(orden.path("expiraEn").asText()));
        assertThat(ttl).isEqualTo(Duration.ofMinutes(5));
        assertThat(parcial.getMontoPagado()).isEqualByComparingTo("100.00");
        assertThat(vencido.getMontoPagado()).isZero();
        assertThat(em.createQuery("from TransaccionPago", TransaccionPago.class).getResultList())
                .hasSize(2).allSatisfy(tx -> assertThat(tx.getEstado()).isEqualTo(EstadoTransaccionPago.PENDIENTE));
        assertThat(contar("Comprobante")).isZero();
        familia("GET", rutaOrden(orden) + "/comprobante", null, 422);
    }

    @Test
    void unReintentoIdempotenteConservaLaOrdenYRechazaReutilizarLaClaveConOtrosDatos() throws Exception {
        var primero = compromiso(1, "350.00");
        var segundo = compromiso(2, "150.00");
        String clave = UUID.randomUUID().toString();
        var orden = iniciar(guardian, "QR", clave, primero.getId(), segundo.getId());
        var repetida = iniciar(guardian, "QR", clave, segundo.getId(), primero.getId());
        assertThat(repetida.path("id").asLong()).isEqualTo(orden.path("id").asLong());
        assertThat(repetida.path("qrUrl").asText()).isEqualTo(orden.path("qrUrl").asText());
        assertThat(contar("OrdenPago")).isEqualTo(1);
        assertThat(contar("TransaccionPago")).isEqualTo(2);
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(primero.getId()), "metodo", "QR"), 409, clave);
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(primero.getId(), segundo.getId()), "metodo", "TARJETA"), 409, clave);
    }

    @Test
    void apruebaUnLoteAtomicoEmiteComprobantesInternosYRepetirNoDuplicaPagos() throws Exception {
        var primero = compromiso(1, "350.00");
        var segundo = compromiso(2, "150.50");
        var orden = iniciar("TARJETA", primero.getId(), segundo.getId());
        var aprobado = confirmar(orden, "APROBADO");
        assertThat(aprobado.path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(aprobado.path("pagadaEn").isNull()).isFalse();
        assertThat(primero.getMontoPagado()).isEqualByComparingTo("350.00");
        assertThat(segundo.getMontoPagado()).isEqualByComparingTo("150.50");
        assertThat(contar("Comprobante")).isEqualTo(2);
        assertThat(contar("TransaccionPago")).isEqualTo(2);
        assertThat(em.createQuery("from TransaccionPago", TransaccionPago.class).getResultList())
                .allSatisfy(tx -> assertThat(tx.getEstado()).isEqualTo(EstadoTransaccionPago.APROBADA));
        assertThat(confirmar(orden, "APROBADO").path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(contar("Comprobante")).isEqualTo(2);
        assertThat(primero.getMontoPagado()).isEqualByComparingTo("350.00");
        var recibo = familia("GET", rutaOrden(orden) + "/comprobante", null, 200);
        assertThat(recibo.path("comprobantes")).hasSize(2);
        assertThat(recibo.path("orden").path("montoTotal").decimalValue()).isEqualByComparingTo("500.50");
        for (JsonNode comprobante : recibo.path("comprobantes")) {
            assertThat(comprobante.path("tipoComprobante").asText()).isEqualTo("CONSTANCIA_PAGO_DIGITAL");
        }
    }

    @Test
    void rechazarUnaTarjetaNoReduceElSaldoYPermiteConsultarElIntentoComoRechazado() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        assertThat(confirmar(orden, "FONDOS_INSUFICIENTES").path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
        var detalle = familia("GET", OBLIGACIONES + "/" + propio.getId(), null, 200);
        assertThat(detalle.path("transacciones")).hasSize(1);
        assertThat(detalle.path("transacciones").get(0).path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(detalle.path("comprobantes")).isEmpty();
        familia("GET", rutaOrden(orden) + "/comprobante", null, 422);
        var nuevoIntento = iniciar("TARJETA", propio.getId());
        assertThat(confirmar(nuevoIntento, "RECHAZADO").path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(familia("GET", PAGOS + "/ordenes", null, 200).path("content")).hasSize(2);
    }

    @Test
    void tresDSExigeDesafioYAutenticarApruebaUnaSolaVez() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        assertThat(confirmar(orden, "REQUIERE_AUTENTICACION").path("estado").asText())
                .isEqualTo("REQUIERE_AUTENTICACION");
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
        var aprobado = familia("POST", rutaOrden(orden) + "/autenticar", data("aprobar", true), 200);
        assertThat(aprobado.path("estado").asText()).isEqualTo("COMPLETADO");
        familia("POST", rutaOrden(orden) + "/autenticar", data("aprobar", true), 200);
        assertThat(contar("Comprobante")).isEqualTo(1);
        assertThat(propio.getMontoPagado()).isEqualByComparingTo("350.00");
    }

    @Test
    void cancelarElDesafioTresDSRechazaSinPagarYNoAceptaAutenticarSinDesafio() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        familia("POST", rutaOrden(orden) + "/autenticar", data("aprobar", true), 422);
        confirmar(orden, "REQUIERE_AUTENTICACION");
        var cancelado = familia("POST", rutaOrden(orden) + "/autenticar", data("aprobar", false), 200);
        assertThat(cancelado.path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void elQrSeConfirmaSinSesionDesdeElCelularYLaConsultaDelPcVeElPago() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("QR", propio.getId());
        String token = tokenQr(orden);
        var billetera = publico("GET", PAGOS + "/qr/" + token, null, 200);
        assertThat(billetera.path("codigo").asText()).isEqualTo(orden.path("codigo").asText());
        assertThat(billetera.path("montoTotal").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(billetera.path("clientSecret").isNull()).isTrue();
        assertThat(billetera.path("paymentIntentId").isNull()).isTrue();
        assertThat(billetera.path("qrUrl").isNull()).isTrue();
        assertThat(billetera.toString()).doesNotContain("numeroDocumento", "correo", "telefono", "idPersona");
        assertThat(publico("POST", PAGOS + "/qr/" + token + "/confirmar",
                data("accion", "APROBAR"), 200).path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(familia("GET", rutaOrden(orden), null, 200).path("estado").asText()).isEqualTo("COMPLETADO");
        publico("POST", PAGOS + "/qr/" + token + "/confirmar", data("accion", "APROBAR"), 200);
        assertThat(propio.getMontoPagado()).isEqualByComparingTo("350.00");
        assertThat(contar("Comprobante")).isEqualTo(1);
    }

    @Test
    void qrRechazadoOTokenManipuladoNoAplicanPagos() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("QR", propio.getId());
        String token = tokenQr(orden);
        String manipulado = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");
        publico("GET", PAGOS + "/qr/" + manipulado, null, 404);
        publico("POST", PAGOS + "/qr/" + manipulado + "/confirmar", data("accion", "APROBAR"), 404);
        publico("GET", PAGOS + "/qr/token-inventado", null, 404);
        assertThat(publico("POST", PAGOS + "/qr/" + token + "/confirmar",
                data("accion", "RECHAZAR"), 200).path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void expiraElQrEnServidorYUnCallbackTardioNoPuedeAprobarlo() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("QR", propio.getId());
        String token = tokenQr(orden);
        expirar(orden);
        var publicoExpirado = publico("GET", PAGOS + "/qr/" + token, null, 200);
        assertThat(publicoExpirado.path("estado").asText()).isEqualTo("EXPIRADO");
        comprobarCapacidadCaducadaSinDatos(publicoExpirado);
        var callbackTardio = publico("POST", PAGOS + "/qr/" + token + "/confirmar",
                data("accion", "APROBAR"), 200);
        assertThat(callbackTardio.path("estado").asText()).isEqualTo("EXPIRADO");
        comprobarCapacidadCaducadaSinDatos(callbackTardio);
        var vistaPrivada = familia("GET", rutaOrden(orden), null, 200);
        assertThat(vistaPrivada.path("estado").asText()).isEqualTo("EXPIRADO");
        assertThat(vistaPrivada.path("apoderado").asText()).isEqualTo(guardian.getPersona().nombreCompleto());
        assertThat(vistaPrivada.path("items")).hasSize(1);
        assertThat(vistaPrivada.path("montoTotal").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(em.createQuery("from TransaccionPago", TransaccionPago.class).getResultList())
                .allSatisfy(tx -> assertThat(tx.getEstado()).isEqualTo(EstadoTransaccionPago.EXPIRADA));
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void unQrCompletadoTambienOcultaIdentidadEImportesAlCaducarPeroElDuenoConservaSuRecibo() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("QR", propio.getId());
        String token = tokenQr(orden);
        publico("POST", PAGOS + "/qr/" + token + "/confirmar", data("accion", "APROBAR"), 200);
        expirar(orden);
        var publicoCaducado = publico("GET", PAGOS + "/qr/" + token, null, 200);
        assertThat(publicoCaducado.path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(publicoCaducado.path("codigo").asText()).isEqualTo(orden.path("codigo").asText());
        assertThat(publicoCaducado.path("pagadaEn").isNull()).isFalse();
        comprobarCapacidadCaducadaSinDatos(publicoCaducado);
        var repetido = publico("POST", PAGOS + "/qr/" + token + "/confirmar", data("accion", "APROBAR"), 200);
        assertThat(repetido.path("estado").asText()).isEqualTo("COMPLETADO");
        comprobarCapacidadCaducadaSinDatos(repetido);
        var recibo = familia("GET", rutaOrden(orden) + "/comprobante", null, 200);
        assertThat(recibo.path("orden").path("apoderado").asText()).isEqualTo(guardian.getPersona().nombreCompleto());
        assertThat(recibo.path("orden").path("montoTotal").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(recibo.path("orden").path("items")).hasSize(1);
        assertThat(recibo.path("comprobantes")).hasSize(1);
        assertThat(propio.getMontoPagado()).isEqualByComparingTo("350.00");
        assertThat(contar("Comprobante")).isEqualTo(1);
    }

    @Test
    void unaTarjetaExpiradaYElMedioIncorrectoNoPuedenConfirmar() throws Exception {
        var propio = compromiso(1, "350.00");
        var tarjeta = iniciar("TARJETA", propio.getId());
        expirar(tarjeta);
        assertThat(confirmar(tarjeta, "APROBADO").path("estado").asText()).isEqualTo("EXPIRADO");
        var qr = iniciar("QR", propio.getId());
        familia("POST", rutaOrden(qr) + "/confirmar-tarjeta", data("escenario", "APROBADO"), 422);
        assertThat(propio.getMontoPagado()).isZero();
    }

    @Test
    void ordenesAnterioresSuperpuestasNoCobranDosVecesYUnLoteConSaldoCambiadoSeRechazaEntero() throws Exception {
        var primero = compromiso(1, "350.00");
        var segundo = compromiso(2, "150.00");
        var individual = iniciar("TARJETA", primero.getId());
        // Reproduce datos anteriores a la protección de inicio: dos órdenes ya existentes.
        var anterior = em.find(OrdenPago.class, individual.path("id").asLong());
        anterior.setEstado(EstadoOrdenPago.RECHAZADO); em.flush();
        var lote = iniciar("TARJETA", primero.getId(), segundo.getId());
        anterior.setEstado(EstadoOrdenPago.PENDIENTE); em.flush();
        confirmar(individual, "APROBADO");
        assertThat(confirmar(lote, "APROBADO").path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(primero.getMontoPagado()).isEqualByComparingTo("350.00");
        assertThat(segundo.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isEqualTo(1);
        assertThat(em.createQuery("select count(t) from TransaccionPago t where t.estado=:estado", Long.class)
                .setParameter("estado", EstadoTransaccionPago.APROBADA).getSingleResult()).isEqualTo(1);
    }

    @Test
    void revalidaAnulacionAntesDeConfirmarYConservaElSaldo() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        propio.setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO); em.flush();
        assertThat(confirmar(orden, "APROBADO").path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void filtraLosEstadosFinancierosYElDetalleMuestraSoloComprobantesAprobados() throws Exception {
        var pendiente = compromiso(1, "350.00");
        compromiso(student, 2, "350.00", "0.00", LocalDate.now(LIMA).minusDays(1));
        compromiso(student, 3, "350.00", "350.00", LocalDate.now(LIMA).plusDays(1));
        compromiso(student, 4, "350.00", "50.00", LocalDate.now(LIMA).plusDays(1));
        for (String estado : List.of("PENDIENTE", "VENCIDO", "PAGADO", "PARCIAL")) {
            var lista = familia("GET", OBLIGACIONES + "?idAlumno=" + student.getId() + "&estado=" + estado,
                    null, 200);
            assertThat(lista.path("content")).hasSize(1);
            assertThat(lista.path("content").get(0).path("estadoFinanciero").asText()).isEqualTo(estado);
        }
        var orden = iniciar("TARJETA", pendiente.getId());
        assertThat(familia("GET", OBLIGACIONES + "/" + pendiente.getId(), null, 200).path("transacciones"))
                .hasSize(1);
        confirmar(orden, "APROBADO");
        var detalle = familia("GET", OBLIGACIONES + "/" + pendiente.getId(), null, 200);
        assertThat(detalle.path("compromiso").path("saldo").decimalValue()).isZero();
        assertThat(detalle.path("comprobantes")).hasSize(1);
    }

    @Test
    void elComprobanteDescargableEscapaTextosYLaAuditoriaNoGuardaSecretos() throws Exception {
        var propio = compromiso(1, "350.00");
        propio.setConcepto("Pensión <script>alert('x')</script>"); em.flush();
        var orden = iniciar("TARJETA", propio.getId());
        confirmar(orden, "APROBADO");
        var response = mvc.perform(get(rutaOrden(orden) + "/comprobante/descargar").with(comoApoderado(guardian)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("attachment");
        String html = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(html.toLowerCase(Locale.ROOT)).contains("constancia de pago", "documento de control institucional", "@media print");
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>");
        var auditoria = em.createQuery("from RegistroAuditoria r where r.entidad='OrdenPago' and r.registroId=:id",
                        RegistroAuditoria.class).setParameter("id", orden.path("id").asText()).getResultList();
        assertThat(auditoria).hasSizeGreaterThanOrEqualTo(2);
        auditoria.forEach(registro -> {
            String contenido = String.valueOf(registro.getValorAnterior()) + registro.getNuevoValor() + registro.getMotivo();
            assertThat(contenido).doesNotContain(orden.path("clientSecret").asText(), "4242424242424242", "cvv", "sk_test_");
        });
    }

    @Test
    void unErrorDeValidacionEnElQrNoExponeLaCapacidadEnElErrorNiEnAuditoria() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("QR", propio.getId());
        String token = tokenQr(orden);
        String firma = token.substring(token.indexOf('.') + 1);
        var error = publico("POST", PAGOS + "/qr/" + token + "/confirmar", data(), 400);
        assertThat(error.path("path").asText()).contains("/api/pagos/qr/")
                .doesNotContain(token, firma.substring(0, 16));
        var registros = em.createQuery("from RegistroAuditoria", RegistroAuditoria.class).getResultList();
        registros.forEach(registro -> {
            String texto = String.valueOf(registro.getAccion()) + registro.getRegistroId()
                    + registro.getValorAnterior() + registro.getNuevoValor() + registro.getMotivo();
            assertThat(texto).doesNotContain(token, firma.substring(0, 16));
        });
        assertThat(propio.getMontoPagado()).isZero();
    }

    private String tokenQr(JsonNode orden) {
        String url = orden.path("qrUrl").asText();
        assertThat(url).contains("/pagar-qr/");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private void comprobarCapacidadCaducadaSinDatos(JsonNode vistaPublica) {
        assertThat(vistaPublica.path("apoderado").asText()).isEmpty();
        assertThat(vistaPublica.path("items")).isEmpty();
        assertThat(vistaPublica.path("montoBase").decimalValue()).isZero();
        assertThat(vistaPublica.path("mora").decimalValue()).isZero();
        assertThat(vistaPublica.path("montoTotal").decimalValue()).isZero();
        assertThat(vistaPublica.path("qrUrl").isNull()).isTrue();
        assertThat(vistaPublica.path("paymentIntentId").isNull()).isTrue();
        assertThat(vistaPublica.path("clientSecret").isNull()).isTrue();
    }

    private void expirar(JsonNode orden) {
        em.find(OrdenPago.class, orden.path("id").asLong()).setExpiraEn(Instant.now().minusSeconds(1));
        em.flush();
    }
}
