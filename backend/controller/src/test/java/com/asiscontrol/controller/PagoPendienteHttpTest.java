package com.asiscontrol.controller;

import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.OrdenPago;
import com.asiscontrol.entity.enums.Parentesco;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PagoPendienteHttpTest extends PagosFamiliaIntegrationSupport {
    @Test
    void otraClaveNoCreaPagoDuplicadoNiCambiaElMetodoYLaOriginalSigueSiendoIdempotente() throws Exception {
        var cuota = compromiso(1, "350.00");
        String clave = UUID.randomUUID().toString();
        var original = iniciar(guardian, "TARJETA", clave, cuota.getId());
        for (String metodo : List.of("TARJETA", "QR")) {
            var rechazo = familia(guardian, "POST", PAGOS + "/iniciar",
                    data("obligacionesIds", List.of(cuota.getId()), "metodo", metodo), 409, UUID.randomUUID().toString());
            assertThat(rechazo.path("code").asText()).isEqualTo("PAGO_EN_CURSO");
            assertThat(rechazo.path("details")).isEmpty();
        }
        var repetida = iniciar(guardian, "TARJETA", clave, cuota.getId());
        assertThat(repetida.path("id").asLong()).isEqualTo(original.path("id").asLong());
        assertThat(repetida.path("metodo").asText()).isEqualTo("TARJETA");
        assertThat(contar("OrdenPago")).isEqualTo(1);
        assertThat(contar("TransaccionPago")).isEqualTo(1);
        assertThat(cuota.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void unSolapeRechazaTodoElLotePeroPermiteUnaCuotaIndependiente() throws Exception {
        var primera = compromiso(1, "350.00");
        var segunda = compromiso(2, "250.00");
        var tercera = compromiso(3, "150.00");
        iniciar("QR", primera.getId(), segunda.getId());
        var rechazo = familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(segunda.getId(), tercera.getId()), "metodo", "TARJETA"),
                409, UUID.randomUUID().toString());
        assertThat(rechazo.path("code").asText()).isEqualTo("PAGO_EN_CURSO");
        assertThat(contar("OrdenPago")).isEqualTo(1);
        assertThat(contar("TransaccionPago")).isEqualTo(2);
        iniciar("TARJETA", tercera.getId());
        assertThat(contar("OrdenPago")).isEqualTo(2);
        assertThat(contar("TransaccionPago")).isEqualTo(3);
    }

    @Test
    void unSegundoApoderadoAsociadoNoReservaLaMismaCuotaNiRecibeDatosDeLaOrden() throws Exception {
        var otro = otraFamilia().apoderado();
        var vinculo = new AlumnoApoderado(); vinculo.setAlumno(student); vinculo.setApoderado(otro);
        vinculo.setParentesco(Parentesco.PADRE); em.persist(vinculo); em.flush();
        var cuota = compromiso(1, "350.00");
        var orden = iniciar("QR", cuota.getId());
        var rechazo = familia(otro, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(cuota.getId()), "metodo", "TARJETA"), 409, UUID.randomUUID().toString());
        assertThat(rechazo.path("code").asText()).isEqualTo("PAGO_EN_CURSO");
        assertThat(rechazo.path("details")).isEmpty();
        assertThat(rechazo.toString()).doesNotContain(orden.path("codigo").asText(), orden.path("qrUrl").asText(),
                guardian.getPersona().nombreCompleto());
        assertThat(contar("OrdenPago")).isEqualTo(1);
    }

    @Test
    void laAutenticacionPendienteTambienImpideUnSegundoInicio() throws Exception {
        var cuota = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", cuota.getId());
        confirmar(orden, "REQUIERE_AUTENTICACION");
        var rechazo = familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(cuota.getId()), "metodo", "QR"), 409, UUID.randomUUID().toString());
        assertThat(rechazo.path("code").asText()).isEqualTo("PAGO_EN_CURSO");
        assertThat(contar("OrdenPago")).isEqualTo(1);
        assertThat(cuota.getMontoPagado()).isZero();
    }

    @Test
    void unaOrdenCaducadaNoBloqueaUnNuevoPagoNiPuedeAprobarseDespues() throws Exception {
        var cuota = compromiso(1, "350.00");
        String clave = UUID.randomUUID().toString();
        var anterior = iniciar(guardian, "TARJETA", clave, cuota.getId());
        em.find(OrdenPago.class, anterior.path("id").asLong()).setExpiraEn(Instant.now().minusSeconds(1));
        em.flush();
        // No se consulta primero la orden anterior: debe bastar su fecha en la base.
        var siguiente = iniciar("TARJETA", cuota.getId());
        assertThat(siguiente.path("id").asLong()).isNotEqualTo(anterior.path("id").asLong());
        assertThat(confirmar(anterior, "APROBADO").path("estado").asText()).isEqualTo("EXPIRADO");
        assertThat(cuota.getMontoPagado()).isZero();
        assertThat(iniciar(guardian, "TARJETA", clave, cuota.getId()).path("id").asLong())
                .isEqualTo(anterior.path("id").asLong());
        confirmar(siguiente, "APROBADO");
        assertThat(contar("Comprobante")).isEqualTo(1);
        assertThat(cuota.getMontoPagado()).isEqualByComparingTo("350.00");
    }
}
