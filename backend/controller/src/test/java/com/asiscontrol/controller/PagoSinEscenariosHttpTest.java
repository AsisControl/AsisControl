package com.asiscontrol.controller;

import com.asiscontrol.entity.ConceptoCobro;
import com.asiscontrol.entity.enums.PeriodicidadCobro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.pagos.simulador.escenarios-cliente=false",
        "spring.datasource.url=jdbc:h2:mem:pago_sin_escenarios;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
})
class PagoSinEscenariosHttpTest extends PagosFamiliaIntegrationSupport {
    @Test
    void permiteSoloReferenciasDeTarjetaSinAceptarEscenarioDirectoNiDatosSensibles() throws Exception {
        var pension = compromiso(1, "350.00");
        var tarjeta = iniciar("TARJETA", pension.getId());
        var rutaTarjeta = rutaOrden(tarjeta) + "/confirmar-tarjeta";
        var errorTarjeta = familia("POST", rutaTarjeta, data("escenario", "FONDOS_INSUFICIENTES"), 422);
        assertThat(errorTarjeta.path("code").asText()).isEqualTo("ESCENARIO_PAGO_NO_DISPONIBLE");
        assertThat(familia("POST", rutaTarjeta, data(), 422).path("code").asText())
                .isEqualTo("TARJETA_PRUEBA_REQUERIDA");
        var desconocida = familia("POST", rutaTarjeta, data("tarjetaPrueba", "TP-999"), 422);
        assertThat(desconocida.path("code").asText()).isEqualTo("TARJETA_PRUEBA_INVALIDA");
        assertThat(desconocida.path("fieldErrors").get(0).path("field").asText()).isEqualTo("tarjetaPrueba");
        var datosDirectos = familia("POST", rutaTarjeta,
                data("tarjetaPrueba", "TP-001", "cvv", "valor-reservado"), 400);
        assertThat(datosDirectos.path("code").asText()).isEqualTo("FORMATO_INVALIDO");
        assertThat(datosDirectos.toString()).doesNotContain("valor-reservado");
        assertThat(familia("GET", rutaOrden(tarjeta), null, 200).path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(familia("POST", rutaTarjeta, data("tarjetaPrueba", "TP-001"), 200).path("estado").asText())
                .isEqualTo("COMPLETADO");
        assertThat(familia("POST", rutaTarjeta, data("tarjetaPrueba", "TP-001"), 200).path("estado").asText())
                .isEqualTo("COMPLETADO");
        var pagada = familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(pension.getId()), "metodo", "TARJETA"),
                422, UUID.randomUUID().toString());
        assertThat(pagada.path("code").asText()).isEqualTo("OBLIGACION_YA_PAGADA");
        assertThat(contar("Comprobante")).isEqualTo(1);

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

    @ParameterizedTest
    @CsvSource({
            "TP-002, fondos insuficientes",
            "TP-003, vencida",
            "TP-004, No pudimos usar esta tarjeta",
            "TP-005, código de seguridad no coincide",
            "TP-006, supera el límite",
            "TP-007, no está habilitada para compras por internet",
            "TP-008, No se pudo autorizar"
    })
    void rechazosConservanElCargoPendiente(String referencia, String mensaje) throws Exception {
        var pension = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", pension.getId());
        var rechazada = familia("POST", rutaOrden(orden) + "/confirmar-tarjeta",
                data("tarjetaPrueba", referencia), 200);
        assertThat(rechazada.path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(rechazada.path("mensaje").asText()).contains(mensaje);
        var detalle = familia("GET", OBLIGACIONES + "/" + pension.getId(), null, 200);
        assertThat(detalle.path("compromiso").path("estadoFinanciero").asText()).isEqualTo("PENDIENTE");
        assertThat(detalle.path("transacciones").get(0).path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(detalle.path("comprobantes")).isEmpty();
    }

    @Test
    void consultaLaMismaOrdenAnteErrorDeServicioYRespuestaPerdida() throws Exception {
        var pension = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", pension.getId());
        String ruta = rutaOrden(orden);
        assertThat(familia("POST", ruta + "/confirmar-tarjeta", data("tarjetaPrueba", "TP-010"), 503)
                .path("code").asText()).isEqualTo("PAGO_SERVICIO_NO_DISPONIBLE");
        assertThat(familia("GET", ruta, null, 200).path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(contar("Comprobante")).isZero();

        assertThat(familia("POST", ruta + "/confirmar-tarjeta", data("tarjetaPrueba", "TP-011"), 504)
                .path("code").asText()).isEqualTo("PAGO_CONFIRMACION_NO_RECIBIDA");
        assertThat(familia("GET", ruta, null, 200).path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(familia("POST", ruta + "/confirmar-tarjeta", data("tarjetaPrueba", "TP-011"), 200)
                .path("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(contar("OrdenPago")).isEqualTo(1);
        assertThat(contar("Comprobante")).isEqualTo(1);
    }

    @Test
    void unaOrdenMixtaIncluyeElDetalleAcademicoYGeneraUnaConstanciaPorCargo() throws Exception {
        var pension = compromiso(1, "350.00");
        var conceptoPension = em.createQuery("select c from ConceptoCobro c where c.esPension=true", ConceptoCobro.class)
                .getSingleResult();
        pension.setConceptoCobro(conceptoPension);
        pension.setMes(3);
        var actividad = compromiso(2, "75.00");
        var conceptoActividad = new ConceptoCobro();
        conceptoActividad.setCodigo("ACT-" + key());
        conceptoActividad.setNombre("Materiales");
        conceptoActividad.setPeriodicidad(PeriodicidadCobro.UNICO);
        em.persist(conceptoActividad);
        actividad.setConceptoCobro(conceptoActividad);
        actividad.setConcepto("Materiales");
        actividad.setNumeroCuota(null);
        actividad.setReferenciaCobro("Taller de ciencias");
        em.flush();

        var orden = iniciar("TARJETA", pension.getId(), actividad.getId());
        var items = orden.path("items");
        assertThat(items.get(0).path("tipo").asText()).isEqualTo("PENSION");
        assertThat(items.get(0).path("mes").asInt()).isEqualTo(3);
        assertThat(items.get(0).path("anioAcademico").asInt()).isEqualTo(pension.getMatricula().getAnioAcademico());
        assertThat(items.get(1).path("tipo").asText()).isEqualTo("OTRO");
        assertThat(items.get(1).path("referenciaCobro").asText()).isEqualTo("Taller de ciencias");
        var confirmar = familia("POST", rutaOrden(orden) + "/confirmar-tarjeta", data("tarjetaPrueba", "TP-009"), 200);
        assertThat(confirmar.path("estado").asText()).isEqualTo("REQUIERE_AUTENTICACION");
        assertThat(contar("Comprobante")).isZero();
        assertThat(familia("POST", rutaOrden(orden) + "/autenticar", data("aprobar", true), 200)
                .path("estado").asText()).isEqualTo("COMPLETADO");
        var constancias = familia("GET", rutaOrden(orden) + "/comprobante", null, 200);
        assertThat(constancias.path("comprobantes")).hasSize(2);
    }

    @Test
    void unCargoPagadoOConOtroImporteImpideAprobarLaSeleccionCompleta() throws Exception {
        var pension = compromiso(1, "350.00");
        var actividad = compromiso(2, "75.00");
        var orden = iniciar("TARJETA", pension.getId(), actividad.getId());
        actividad.setMontoTotal(new BigDecimal("80.00"));
        em.flush();
        var actualizada = familia("POST", rutaOrden(orden) + "/confirmar-tarjeta", data("tarjetaPrueba", "TP-001"), 200);
        assertThat(actualizada.path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(actualizada.path("mensaje").asText()).contains("importe disponible");

        var otraOrden = iniciar("TARJETA", pension.getId(), actividad.getId());
        actividad.setMontoPagado(new BigDecimal("80.00"));
        em.flush();
        var pagada = familia("POST", rutaOrden(otraOrden) + "/confirmar-tarjeta", data("tarjetaPrueba", "TP-001"), 200);
        assertThat(pagada.path("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(pagada.path("mensaje").asText()).contains("ya está pagado");
        assertThat(pension.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }
}
