package com.asiscontrol.controller;

import com.asiscontrol.entity.AlumnoApoderado;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagoApoderadoSeguridadHttpTest extends PagosFamiliaIntegrationSupport {
    @Test
    void exigeSesionRolApoderadoYPermisoParaConsultarEIniciar() throws Exception {
        mvc.perform(get(OBLIGACIONES)).andExpect(status().isUnauthorized());
        mvc.perform(get(OBLIGACIONES).with(asAdmin())).andExpect(status().isForbidden());
        mvc.perform(get(OBLIGACIONES).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APODERADO"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(PAGOS + "/iniciar").with(asAdmin())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("obligacionesIds", List.of(1), "metodo", "TARJETA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadaFamiliaSoloVeSusEstudiantesYObligacionesAunqueManipuleParametros() throws Exception {
        var propio = compromiso(1, "350.00");
        var otra = otraFamilia();
        var ajeno = compromiso(otra.alumno(), 1, "900.00", "0.00", LocalDate.now(LIMA).plusDays(10));
        var alumnos = familia("GET", OBLIGACIONES + "/estudiantes", null, 200);
        assertThat(alumnos).hasSize(1);
        assertThat(alumnos.get(0).path("id").asLong()).isEqualTo(student.getId());
        var lista = familia("GET", OBLIGACIONES + "?apoderadoId=" + otra.apoderado().getId(), null, 200);
        assertThat(lista.path("content")).hasSize(1);
        assertThat(lista.path("content").get(0).path("id").asLong()).isEqualTo(propio.getId());
        familia("GET", OBLIGACIONES + "/" + ajeno.getId(), null, 403);
        familia("GET", OBLIGACIONES + "?idAlumno=" + otra.alumno().getId(), null, 403);
        var vistaAjena = familia(otra.apoderado(), "GET", OBLIGACIONES, null, 200, null);
        assertThat(vistaAjena.path("content")).hasSize(1);
        assertThat(vistaAjena.path("content").get(0).path("id").asLong()).isEqualTo(ajeno.getId());
    }

    @Test
    void rechazaElLoteCompletoSiUnaObligacionEsAjenaSinCrearOrdenesNiTransacciones() throws Exception {
        var propio = compromiso(1, "350.00");
        var otra = otraFamilia();
        var ajeno = compromiso(otra.alumno(), 1, "900.00", "0.00", LocalDate.now(LIMA).plusDays(10));
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(propio.getId(), ajeno.getId()), "metodo", "QR"),
                403, UUID.randomUUID().toString());
        assertThat(contar("OrdenPago")).isZero();
        assertThat(contar("TransaccionPago")).isZero();
        assertThat(propio.getMontoPagado()).isZero();
    }

    @Test
    void noConfiaEnElMontoNiEnLaIdentidadDeApoderadoEnviadosPorElCliente() throws Exception {
        var propio = compromiso(1, "350.00");
        var otra = otraFamilia();
        var orden = familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(propio.getId()), "metodo", "TARJETA",
                        "montoTotal", 0.01, "apoderadoId", otra.apoderado().getId()),
                201, UUID.randomUUID().toString());
        assertThat(orden.path("montoTotal").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(orden.path("montoBase").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(orden.path("mora").decimalValue()).isZero();
        assertThat(orden.path("moneda").asText()).isEqualTo("PEN");
        var responsable = em.createQuery("select o.apoderado.id from OrdenPago o where o.id=:id", Long.class)
                .setParameter("id", orden.path("id").asLong()).getSingleResult();
        assertThat(responsable).isEqualTo(guardian.getId());
    }

    @Test
    void noPermiteConsultarConfirmarNiDescargarLaOrdenDeOtraFamilia() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        var otra = otraFamilia();
        String ruta = rutaOrden(orden);
        familia(otra.apoderado(), "GET", ruta, null, 403, null);
        familia(otra.apoderado(), "POST", ruta + "/confirmar-tarjeta",
                data("escenario", "APROBADO"), 403, null);
        familia(otra.apoderado(), "POST", ruta + "/autenticar", data("aprobar", true), 403, null);
        familia(otra.apoderado(), "GET", ruta + "/comprobante", null, 403, null);
        mvc.perform(get(ruta + "/comprobante/descargar").with(comoApoderado(otra.apoderado())))
                .andExpect(status().isForbidden());
        assertThat(familia(otra.apoderado(), "GET", PAGOS + "/ordenes", null, 200, null).path("content"))
                .isEmpty();
    }

    @Test
    void unaAsociacionDesactivadaImpideLaLecturaYLaConfirmacionPosterior() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        var link = em.createQuery("from AlumnoApoderado v where v.alumno.id=:id", AlumnoApoderado.class)
                .setParameter("id", student.getId()).getSingleResult();
        link.setActivo(false); em.flush();
        assertThat(familia("GET", OBLIGACIONES, null, 200).path("content")).isEmpty();
        familia("GET", rutaOrden(orden), null, 403);
        familia("GET", rutaOrden(orden) + "/comprobante", null, 403);
        assertThat(familia("GET", PAGOS + "/ordenes", null, 200).path("content")).isEmpty();
        familia("POST", rutaOrden(orden) + "/confirmar-tarjeta", data("escenario", "APROBADO"), 403);
        assertThat(propio.getMontoPagado()).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void validaSeleccionClaveYEstadosSinCrearObligacionesExtras() throws Exception {
        var pendiente = compromiso(1, "350.00");
        var pagado = compromiso(student, 2, "350.00", "350.00", LocalDate.now(LIMA).plusDays(10));
        var anulado = compromiso(3, "350.00");
        anulado.setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO); em.flush();
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(), "metodo", "TARJETA"), 400, UUID.randomUUID().toString());
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(pendiente.getId(), pendiente.getId()), "metodo", "QR"),
                400, UUID.randomUUID().toString());
        familia("POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(pendiente.getId()), "metodo", "TARJETA"), 400);
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(pagado.getId()), "metodo", "TARJETA"),
                422, UUID.randomUUID().toString());
        familia(guardian, "POST", PAGOS + "/iniciar",
                data("obligacionesIds", List.of(anulado.getId()), "metodo", "QR"),
                422, UUID.randomUUID().toString());
        assertThat(contar("OrdenPago")).isZero();
        assertThat(contar("CompromisoPago")).isEqualTo(3);
    }

    @Test
    void apoderadoNoPuedeSaltarseElCheckoutRegistrandoUnPagoManualAprobado() throws Exception {
        var propio = compromiso(1, "350.00");
        var body = data("idCompromisoPago", propio.getId(), "idApoderado", guardian.getId(),
                "numeroOperacion", "INTENTO-BYPASS", "monto", 350, "medioPago", "EFECTIVO");
        mvc.perform(post("/api/transacciones-pago").with(comoApoderado(guardian))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isForbidden());
        var conPermiso = jwt().jwt(token -> token.claim("personaId", guardian.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"),
                        new SimpleGrantedAuthority("PAGOS_LEER"), new SimpleGrantedAuthority("PAGOS_ESCRIBIR"));
        mvc.perform(post("/api/transacciones-pago").with(conPermiso)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isForbidden());
        assertThat(contar("TransaccionPago")).isZero();
        // El registro institucional existente conserva su permiso y sus validaciones.
        var manual = postOk("/api/transacciones-pago", body);
        assertThat(manual.path("estado").asText()).isEqualTo("APROBADA");
        var recibo = data("idTransaccionPago", manual.path("id").asLong(),
                "tipoComprobante", "BOLETA", "serie", "B-PRUEBA", "numero", "1");
        mvc.perform(post("/api/comprobantes").with(conPermiso)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(recibo)))
                .andExpect(status().isForbidden());
        assertThat(postOk("/api/comprobantes", recibo).path("id").asLong()).isPositive();
    }

    @Test
    void elPerfilDeApoderadoInactivoNoPuedeUsarRutasNuevasNiLecturasLegadas() throws Exception {
        var propio = compromiso(1, "350.00");
        guardian.setActivo(false); em.flush();
        familia("GET", OBLIGACIONES, null, 403);
        familia("GET", OBLIGACIONES + "/estudiantes", null, 403);
        familia("GET", "/api/compromisos-pago/" + propio.getId(), null, 403);
        assertThat(familia("GET", "/api/compromisos-pago", null, 200).path("content")).isEmpty();
    }

    @Test
    void serPropietarioNoSustituyeElPermisoParaLeerTransaccionOComprobanteLegados() throws Exception {
        var propio = compromiso(1, "350.00");
        var orden = iniciar("TARJETA", propio.getId());
        confirmar(orden, "APROBADO");
        var detalle = familia("GET", OBLIGACIONES + "/" + propio.getId(), null, 200);
        long transaccionId = detalle.path("transacciones").get(0).path("id").asLong();
        long comprobanteId = detalle.path("comprobantes").get(0).path("id").asLong();
        String transaccion = "/api/transacciones-pago/" + transaccionId;
        String comprobante = "/api/comprobantes/" + comprobanteId;
        var sinLectura = jwt().jwt(token -> token.claim("personaId", guardian.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"));
        mvc.perform(get(transaccion).with(sinLectura)).andExpect(status().isForbidden());
        mvc.perform(get(comprobante).with(sinLectura)).andExpect(status().isForbidden());
        assertThat(familia("GET", transaccion, null, 200).path("id").asLong()).isEqualTo(transaccionId);
        assertThat(familia("GET", comprobante, null, 200).path("id").asLong()).isEqualTo(comprobanteId);
    }
}
