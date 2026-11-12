package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfertasCobroHttpTest extends PagosFamiliaIntegrationSupport {
    private static final String ADMIN = "/api/configuracion-pagos/ofertas";
    private static final String FAMILIA = "/api/apoderado/ofertas";

    @Test
    void publicarNoGeneraDeudaYAceptarCreaUnaSolaObligacionSinCuotaConImporteDeServidor() throws Exception {
        var propuesta = publicar(concepto(true), "Visita cultural");
        assertThat(contar("OfertaCobro")).isEqualTo(1);
        assertThat(contar("CompromisoPago")).isZero();
        assertThat(propuesta.path("monto").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(propuesta.path("firmaCotizacion").asText()).isNotBlank();
        var lista = familia("GET", FAMILIA, null, 200);
        assertThat(lista.path("totalElements").asInt()).isEqualTo(1);
        var aceptada = aceptar(propuesta);
        assertThat(aceptada.path("estado").asText()).isEqualTo("ACEPTADA");
        var compromiso = em.find(CompromisoPago.class, aceptada.path("idCompromiso").asLong());
        assertThat(compromiso.getNumeroCuota()).isNull();
        assertThat(compromiso.getMontoTotal()).isEqualByComparingTo("400.00");
        assertThat(compromiso.getMontoPagado()).isZero();
        assertThat(compromiso.getConcepto()).isEqualTo("Visita cultural");
        assertThat(compromiso.getCodigoCompromiso()).startsWith("CP-");
        assertThat(aceptar(propuesta).path("idCompromiso").asLong()).isEqualTo(compromiso.getId());
        assertThat(contar("CompromisoPago")).isEqualTo(1);
        assertThat(contar("TransaccionPago")).isZero();
        assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void otraFamiliaNoPuedeLeerAceptarNiRechazarUnaPropuestaAjena() throws Exception {
        var propuesta = publicar(concepto(true), "Taller de fotografía");
        var otra = otraFamilia().apoderado();
        assertThat(familia(otra, "GET", FAMILIA, null, 200, null).path("totalElements").asInt()).isZero();
        for (String operacion : List.of("", "/aceptar", "/rechazar")) {
            familia(otra, operacion.isEmpty() ? "GET" : "POST", ruta(propuesta) + operacion,
                    operacion.equals("/aceptar") ? consentimiento(propuesta) : null, 403, null);
        }
        familia(otra, "GET", FAMILIA + "?idAlumno=" + student.getId(), null, 403, null);
        assertThat(contar("CompromisoPago")).isZero();
        var vinculo = em.createQuery("from AlumnoApoderado v where v.alumno.id=:alumno and v.apoderado.id=:apoderado", AlumnoApoderado.class)
                .setParameter("alumno", student.getId()).setParameter("apoderado", guardian.getId()).getSingleResult();
        vinculo.setActivo(false); em.flush();
        familia("GET", ruta(propuesta), null, 403);
    }

    @Test
    void rechazarYCancelarNoCreanDeudasNiPermitenAceptarDespues() throws Exception {
        var primera = publicar(concepto(true), "Actividad de teatro");
        var rechazada = familia("POST", ruta(primera) + "/rechazar", data("version", primera.path("version").asLong()), 200);
        assertThat(rechazada.path("estado").asText()).isEqualTo("RECHAZADA");
        assertThat(familia("POST", ruta(primera) + "/rechazar", null, 200).path("estado").asText()).isEqualTo("RECHAZADA");
        familia("POST", ruta(primera) + "/aceptar", consentimiento(primera), 409);
        var segunda = publicar(concepto(true), "Excursión cultural");
        var cancelada = request("POST", ADMIN + "/" + segunda.path("id").asLong() + "/cancelar", data("motivo", "Actividad suspendida"), 200);
        assertThat(cancelada.path("estado").asText()).isEqualTo("CANCELADA");
        request("POST", ADMIN + "/" + segunda.path("id").asLong() + "/cancelar", null, 200);
        familia("POST", ruta(segunda) + "/aceptar", consentimiento(segunda), 409);
        assertThat(contar("CompromisoPago")).isZero();
    }

    @Test
    void laCotizacionDetectaBecaNuevaYElImporteAceptadoQuedaConservado() throws Exception {
        var concepto = concepto(true);
        var propuesta = publicar(concepto, "Participación en feria");
        beneficio(concepto, "25.00");
        var error = familia("POST", ruta(propuesta) + "/aceptar", consentimiento(propuesta), 409);
        assertThat(error.path("code").asText()).isEqualTo("OFERTA_ACTUALIZADA");
        assertThat(contar("CompromisoPago")).isZero();
        var actual = familia("GET", ruta(propuesta), null, 200);
        assertThat(actual.path("montoBase").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(actual.path("montoDescuento").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(actual.path("monto").decimalValue()).isEqualByComparingTo("300.00");
        var aceptada = aceptar(actual);
        assertThat(aceptada.path("monto").decimalValue()).isEqualByComparingTo("300.00");
        var beneficio = em.createQuery("from BeneficioEstudiante b where b.alumno.id=:id", BeneficioEstudiante.class)
                .setParameter("id", student.getId()).getSingleResult();
        beneficio.setValor(new BigDecimal("50.00")); em.flush();
        var historial = familia("GET", ruta(propuesta), null, 200);
        assertThat(historial.path("monto").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(historial.path("firmaCotizacion").isNull()).isTrue();
    }

    @Test
    void becaTotalAceptaUnaExoneracionSinFingirPagoNiComprobante() throws Exception {
        var concepto = concepto(true); beneficio(concepto, "100.00");
        var oferta = publicar(concepto, "Taller con beca completa");
        assertThat(oferta.path("monto").decimalValue()).isZero();
        var aceptada = aceptar(oferta);
        var compromiso = em.find(CompromisoPago.class, aceptada.path("idCompromiso").asLong());
        assertThat(compromiso.getEstado().name()).isEqualTo("EXONERADO");
        assertThat(compromiso.getMontoTotal()).isZero();
        assertThat(compromiso.getMontoPagado()).isZero();
        assertThat(contar("TransaccionPago")).isZero(); assertThat(contar("Comprobante")).isZero();
    }

    @Test
    void dosApoderadosAsociadosAceptanLaMismaOfertaSinDuplicarElCompromiso() throws Exception {
        var oferta = publicar(concepto(true), "Taller compartido");
        var otro = otraFamilia().apoderado();
        var vinculo = new AlumnoApoderado(); vinculo.setAlumno(student); vinculo.setApoderado(otro);
        vinculo.setParentesco(Parentesco.PADRE); em.persist(vinculo); em.flush();
        var primera = aceptar(oferta);
        var repetida = familia(otro, "POST", ruta(oferta) + "/aceptar", consentimiento(oferta), 200, null);
        assertThat(repetida.path("idCompromiso").asLong()).isEqualTo(primera.path("idCompromiso").asLong());
        assertThat(contar("CompromisoPago")).isEqualTo(1);
        request("POST", ADMIN + "/" + oferta.path("id").asLong() + "/cancelar", null, 409);
    }

    @Test
    void unReintentoDelLoteNoPublicaOtraOfertaPeroUnaReferenciaConImporteDistintoSeRechaza() throws Exception {
        var concepto = concepto(true); var cuerpo = cuerpo(concepto, "Actividad anual");
        var original = postOk(ADMIN, cuerpo);
        assertThat(postOk(ADMIN, cuerpo).get(0).path("id").asLong()).isEqualTo(original.get(0).path("id").asLong());
        cuerpo.put("monto", 999);
        assertThat(request("POST", ADMIN, cuerpo, 409).path("code").asText()).isEqualTo("OFERTA_DUPLICADA");
        assertThat(contar("OfertaCobro")).isEqualTo(1);
        assertThat(getOk(ADMIN).path("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void validaDestinatariosYFechasAntesDePublicarCualquierFilaDelLote() throws Exception {
        var concepto = concepto(true); var cuerpo = cuerpo(concepto, "Actividad validada");
        cuerpo.put("idMatriculas", List.of(matricula().getId(), Long.MAX_VALUE));
        request("POST", ADMIN, cuerpo, 404); assertThat(contar("OfertaCobro")).isZero();
        cuerpo.put("idMatriculas", List.of(matricula().getId(), matricula().getId()));
        request("POST", ADMIN, cuerpo, 422); assertThat(contar("OfertaCobro")).isZero();
        cuerpo.put("idMatriculas", List.of(matricula().getId()));
        cuerpo.put("fechaLimiteAceptacion", "2091-01-01"); request("POST", ADMIN, cuerpo, 422);
        cuerpo.put("fechaLimiteAceptacion", "2000-01-01"); request("POST", ADMIN, cuerpo, 422);
        cuerpo.put("referencia", "---"); cuerpo.remove("fechaLimiteAceptacion"); request("POST", ADMIN, cuerpo, 422);
    }

    @Test
    void unaOfertaVencidaInactivaOSinMatriculaVigenteNoPuedeConvertirseEnDeuda() throws Exception {
        var propuesta = publicar(concepto(true), "Oferta con plazo");
        var oferta = em.find(OfertaCobro.class, propuesta.path("id").asLong());
        oferta.setFechaLimiteAceptacion(LocalDate.now(LIMA).minusDays(1)); em.flush();
        assertThat(familia("GET", ruta(propuesta), null, 200).path("estado").asText()).isEqualTo("VENCIDA");
        familia("POST", ruta(propuesta) + "/aceptar", consentimiento(propuesta), 422);
        var nueva = publicar(concepto(true), "Oferta vigente");
        matricula().setEstado(EstadoMatricula.RETIRADA); em.flush();
        familia("POST", ruta(nueva) + "/aceptar", consentimiento(nueva), 422);
        var inactiva = em.find(OfertaCobro.class, nueva.path("id").asLong()); inactiva.setActivo(false); em.flush();
        familia("GET", ruta(nueva), null, 403); assertThat(contar("CompromisoPago")).isZero();
    }

    @Test
    void soloConceptosOpcionalesAdmitenOfertasYUnaFirmaInventadaNoAutorizaAceptar() throws Exception {
        var concepto = concepto(false); request("POST", ADMIN, cuerpo(concepto, "No opcional"), 422);
        var oferta = publicar(concepto(true), "Oferta protegida");
        familia("POST", ruta(oferta) + "/aceptar", data("version", oferta.path("version").asLong(), "firmaCotizacion", "inventada"), 409);
        familia("POST", ruta(oferta) + "/aceptar", data("version", -1, "firmaCotizacion", "inventada"), 400);
        familia("POST", ADMIN, cuerpo(concepto, "No autorizado"), 403);
        assertThat(contar("CompromisoPago")).isZero();
    }

    private ConceptoCobro concepto(boolean opcional) {
        var c = new ConceptoCobro(); c.setCodigo("ACT-" + key()); c.setNombre("Actividades complementarias");
        c.setPeriodicidad(PeriodicidadCobro.UNICO); c.setRequiereAceptacion(opcional);
        em.persist(c); em.flush(); return c;
    }

    private void beneficio(ConceptoCobro concepto, String porcentaje) {
        var b = new BeneficioEstudiante(); b.setAlumno(student); b.setConcepto(concepto); b.setAnioAcademico(2090);
        b.setNombre("Beneficio autorizado"); b.setTipo(TipoBeneficio.PORCENTAJE); b.setValor(new BigDecimal(porcentaje));
        b.setMesInicio(1); b.setMesFin(12); b.setMotivo("Convenio aprobado para la actividad"); em.persist(b); em.flush();
    }

    private Matricula matricula() {
        return em.createQuery("from Matricula m where m.alumno.id=:alumno", Matricula.class)
                .setParameter("alumno", student.getId()).getSingleResult();
    }

    private java.util.Map<String, Object> cuerpo(ConceptoCobro c, String titulo) {
        return data("idConcepto", c.getId(), "idMatriculas", List.of(matricula().getId()), "referencia", "ACT-2090",
                "titulo", titulo, "descripcion", "Condiciones y alcance de la actividad", "monto", 400,
                "fechaVencimiento", "2090-08-31");
    }

    private JsonNode publicar(ConceptoCobro c, String titulo) throws Exception { return postOk(ADMIN, cuerpo(c, titulo)).get(0); }
    private String ruta(JsonNode oferta) { return FAMILIA + "/" + oferta.path("id").asLong(); }
    private java.util.Map<String, Object> consentimiento(JsonNode oferta) {
        return data("version", oferta.path("version").asLong(), "firmaCotizacion", oferta.path("firmaCotizacion").asText());
    }
    private JsonNode aceptar(JsonNode oferta) throws Exception { return familia("POST", ruta(oferta) + "/aceptar", consentimiento(oferta), 200); }
}
