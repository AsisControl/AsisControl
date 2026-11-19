package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.pagos.simulador.escenarios-cliente=false",
        "spring.datasource.url=jdbc:h2:mem:pago_sin_escenarios;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
class PagoSinEscenariosHttpTest extends PagosFamiliaIntegrationSupport {
    @Test
    void interfazConfirmaPagosYCualquierEscenarioDePruebaQuedaDeshabilitado() throws Exception {
        var pension = compromiso(1, "350.00");
        var tarjeta = iniciar("TARJETA", pension.getId());
        var rutaTarjeta = rutaOrden(tarjeta) + "/confirmar-tarjeta";
        var errorTarjeta = familia("POST", rutaTarjeta, data("escenario", "FONDOS_INSUFICIENTES"), 422);
        assertThat(errorTarjeta.path("code").asText()).isEqualTo("ESCENARIO_PAGO_NO_DISPONIBLE");
        assertThat(familia("GET", rutaOrden(tarjeta), null, 200).path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(familia("POST", rutaTarjeta, data(), 200).path("estado").asText()).isEqualTo("COMPLETADO");

        var segunda = compromiso(2, "150.00");
        var qr = iniciar("QR", segunda.getId());
        var enlace = qr.path("qrUrl").asText();
        var qrToken = enlace.substring(enlace.lastIndexOf('/') + 1);
        var rutaQr = PAGOS + "/qr/" + qrToken + "/confirmar";
        assertThat(publico("POST", rutaQr, data("accion", "RECHAZAR"), 422).path("code").asText())
                .isEqualTo("ESCENARIO_PAGO_NO_DISPONIBLE");
        assertThat(publico("POST", rutaQr, data("accion", "APROBAR"), 200).path("estado").asText())
                .isEqualTo("COMPLETADO");
        assertThat(contar("Comprobante")).isEqualTo(2);
    }
}
