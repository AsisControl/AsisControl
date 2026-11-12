package com.asiscontrol.controller;

import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.MedioPago;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompromisoPagoHttpTest extends ApiIntegrationSupport {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");

    private static final String PATH = "/api/compromisos-pago";

    private Map<String, Object> emision() {
        Long matriculaId = em.createQuery("select m.id from Matricula m where m.alumno.id = :alumno", Long.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        return data("idMatricula", matriculaId, "concepto", "Pensión escolar", "numeroCuota", 1,
                "montoTotal", new BigDecimal("250.50"), "moneda", "PEN",
                "fechaVencimiento", LocalDate.now(ZONA_NEGOCIO).plusDays(10));
    }

    private JsonNode emitir() throws Exception {
        return postOk(PATH, emision());
    }

    @Test
    void emiteCompromisoManualEnSolesConEstudianteAnioCuotaYDosEstados() throws Exception {
        JsonNode compromiso = emitir();
        assertThat(compromiso.path("codigoCompromiso").asText()).isEqualTo("CP-000001");
        assertThat(compromiso.path("estadoAdministrativo").asText()).isEqualTo("ACTIVO");
        assertThat(compromiso.path("estadoFinanciero").asText()).isEqualTo("PENDIENTE");
        assertThat(compromiso.path("numeroCuota").asInt()).isEqualTo(1);
        assertThat(compromiso.path("moneda").asText()).isEqualTo("PEN");
        assertThat(compromiso.path("idAlumno").asLong()).isEqualTo(student.getId());
        assertThat(compromiso.path("anioAcademico").asInt()).isEqualTo(2090);
        assertThat(compromiso.path("codigoAlumno").asText()).isEqualTo(student.getCodigoAlumno());
        assertThat(compromiso.path("montoTotal").decimalValue()).isEqualByComparingTo("250.50");
    }

    @Test
    void asignaCorrelativosConsecutivosYConservaElAvancePersistido() throws Exception {
        JsonNode primero = emitir();
        var siguiente = emision();
        siguiente.put("numeroCuota", 2);
        JsonNode segundo = postOk(PATH, siguiente);
        assertThat(primero.path("codigoCompromiso").asText()).isEqualTo("CP-000001");
        assertThat(segundo.path("codigoCompromiso").asText()).isEqualTo("CP-000002");
        em.flush();
        em.clear();
        assertThat(em.find(com.asiscontrol.entity.ContadorCodigoCompromiso.class, 1).getUltimoValor())
                .isEqualTo(2);
        siguiente.put("numeroCuota", 3);
        assertThat(postOk(PATH, siguiente).path("codigoCompromiso").asText()).isEqualTo("CP-000003");
    }

    @Test
    void conservaCodigosLegadosYContinuaTrasElMayorNumericoInclusoAnulado() throws Exception {
        var historico = new CompromisoPago();
        historico.setMatricula(em.find(Matricula.class, emision().get("idMatricula")));
        historico.setCodigoCompromiso("CP-000019");
        historico.setConcepto("Pensión histórica");
        historico.setMontoTotal(new BigDecimal("175.25"));
        historico.setMontoPagado(BigDecimal.ZERO);
        historico.setFechaVencimiento(LocalDate.now(ZONA_NEGOCIO));
        historico.setEstadoAdministrativo(com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso.ANULADO);
        em.persist(historico);
        var legado = new CompromisoPago();
        legado.setMatricula(historico.getMatricula());
        legado.setCodigoCompromiso("CP-69EE961C-900A-47D2-AA16-463368D6D962");
        legado.setConcepto("Pensión legada");
        legado.setMontoTotal(new BigDecimal("350.00"));
        legado.setMontoPagado(BigDecimal.ZERO);
        legado.setFechaVencimiento(LocalDate.now(ZONA_NEGOCIO));
        em.persist(legado);
        em.flush();
        assertThat(emitir().path("codigoCompromiso").asText()).isEqualTo("CP-000020");
        assertThat(getOk(PATH + "/" + historico.getId()).path("codigoCompromiso").asText())
                .isEqualTo("CP-000019");
        assertThat(getOk(PATH + "/" + legado.getId()).path("codigoCompromiso").asText())
                .isEqualTo("CP-69EE961C-900A-47D2-AA16-463368D6D962");
        assertThat(getOk(PATH + "/" + legado.getId()).path("montoTotal").decimalValue())
                .isEqualByComparingTo("350.00");
    }

    @Test
    void mantieneCompatibilidadConCodigoExternoYReservaSusNumeros() throws Exception {
        var body = emision();
        body.put("codigoCompromiso", "cp-000009");
        assertThat(postOk(PATH, body).path("codigoCompromiso").asText()).isEqualTo("CP-000009");
        body.put("numeroCuota", 2);
        JsonNode duplicado = request("POST", PATH, body, 409);
        assertThat(duplicado.path("code").asText()).isEqualTo("CODIGO_COMPROMISO_DUPLICADO");
        body.remove("codigoCompromiso");
        assertThat(postOk(PATH, body).path("codigoCompromiso").asText()).isEqualTo("CP-000010");
        body.put("numeroCuota", 3);
        body.put("codigoCompromiso", "REFERENCIA-EXTERNA");
        assertThat(postOk(PATH, body).path("codigoCompromiso").asText()).isEqualTo("REFERENCIA-EXTERNA");
        body.put("numeroCuota", 4);
        body.remove("codigoCompromiso");
        assertThat(postOk(PATH, body).path("codigoCompromiso").asText()).isEqualTo("CP-000011");
    }

    @Test
    void identificaCamposObligatoriosSinCrearRegistros() throws Exception {
        var body = emision();
        for (String campo : new String[]{"concepto", "montoTotal", "numeroCuota", "fechaVencimiento"}) {
            body.remove(campo);
        }
        JsonNode error = request("POST", PATH, body, 400);
        assertThat(error.toString()).contains("concepto", "montoTotal", "fechaVencimiento");
        var legadoSinCuota = emision();
        legadoSinCuota.remove("numeroCuota");
        assertThat(request("POST", PATH, legadoSinCuota, 422).path("code").asText())
                .isEqualTo("CONCEPTO_CUOTA_REQUERIDOS");
        assertThat(em.createQuery("select count(c) from CompromisoPago c", Long.class).getSingleResult()).isZero();
    }

    @Test
    void rechazaMontosInvalidosPrecisionCuotasYMonedasDistintasDeSoles() throws Exception {
        for (Object monto : new Object[]{0, -1, new BigDecimal("0.001"), new BigDecimal("10000000000.00")}) {
            var body = emision();
            body.put("montoTotal", monto);
            request("POST", PATH, body, 400);
        }
        for (Object cuota : new Object[]{0, -1}) {
            var body = emision();
            body.put("numeroCuota", cuota);
            request("POST", PATH, body, 400);
        }
        var body = emision();
        body.put("moneda", "USD");
        request("POST", PATH, body, 400);
    }

    @Test
    void exigeMatriculaVigenteEstudianteActivoYAnioConfiguradoActivo() throws Exception {
        var body = emision();
        body.put("idMatricula", Long.MAX_VALUE);
        request("POST", PATH, body, 404);
        body = emision();
        Matricula matricula = em.find(Matricula.class, body.get("idMatricula"));
        matricula.setEstado(EstadoMatricula.RETIRADA);
        em.flush();
        request("POST", PATH, body, 422);
        matricula.setEstado(EstadoMatricula.ACTIVA);
        year.setEstado(EstadoAnioAcademico.CERRADO);
        em.flush();
        request("POST", PATH, body, 422);
        year.setEstado(EstadoAnioAcademico.ACTIVO);
        student.setActivo(false);
        em.flush();
        request("POST", PATH, body, 422);
    }

    @Test
    void bloqueaDuplicidadNormalizandoMayusculasYEspaciosPermiteOtraCuota() throws Exception {
        emitir();
        var body = emision();
        body.put("concepto", "  PENSIÓN   ESCOLAR  ");
        request("POST", PATH, body, 409);
        body.put("numeroCuota", 2);
        postOk(PATH, body);
    }

    @Test
    void noInventaCuotasHistoricasYBloqueaCoincidenciaPendienteDeRevisar() throws Exception {
        JsonNode creado = emitir();
        CompromisoPago historico = em.find(CompromisoPago.class, creado.path("id").asLong());
        historico.setNumeroCuota(null);
        historico.setClaveObligacion(null);
        em.flush();
        request("POST", PATH, emision(), 409);
        assertThat(getOk(PATH + "/" + historico.getId()).path("numeroCuota").isNull()).isTrue();
    }

    @Test
    void conservaMonedaHistoricaBloqueaModificarYPermiteAnularSinConvertirImporte() throws Exception {
        JsonNode creado = emitir();
        CompromisoPago historico = em.find(CompromisoPago.class, creado.path("id").asLong());
        historico.setMoneda("USD");
        em.flush();
        String path = PATH + "/" + historico.getId();
        JsonNode consulta = getOk(path);
        assertThat(consulta.path("moneda").asText()).isEqualTo("USD");
        JsonNode error = request("PUT", path, data("montoTotal", 300,
                "fechaVencimiento", LocalDate.now(ZONA_NEGOCIO).plusDays(20), "motivo", "Corregir monto",
                "version", historico.getVersion()), 422);
        assertThat(error.path("code").asText()).isEqualTo("MONEDA_HISTORICA_REQUIERE_REVISION");
        JsonNode anulado = request("POST", path + "/anulacion", data("motivo", "Registro histórico equivocado",
                "version", historico.getVersion()), 200);
        assertThat(anulado.path("moneda").asText()).isEqualTo("USD");
        assertThat(anulado.path("montoTotal").decimalValue()).isEqualByComparingTo("250.50");
    }

    @Test
    void consultaPorEstudianteAnioConceptoYEstadoYBuscaMatriculasVigentes() throws Exception {
        emitir();
        String filtro = "?idAlumno=" + student.getId() + "&anioAcademico=2090&estadoAdministrativo=ACTIVO&busqueda=Pensi";
        assertThat(getOk(PATH + filtro).path("totalElements").asInt()).isEqualTo(1);
        assertThat(getOk(PATH + "?anioAcademico=2091").path("totalElements").asInt()).isZero();
        JsonNode matriculas = getOk(PATH + "/matriculas?busqueda=" + student.getCodigoAlumno());
        assertThat(matriculas.path("totalElements").asInt()).isEqualTo(1);
        assertThat(matriculas.path("content").get(0).path("alumnoId").asLong()).isEqualTo(student.getId());
        Matricula matricula = em.createQuery("from Matricula m where m.alumno.id=:id", Matricula.class)
                .setParameter("id", student.getId()).getSingleResult();
        matricula.setEstado(EstadoMatricula.FINALIZADA);
        em.flush();
        assertThat(getOk(PATH + "/matriculas").path("totalElements").asInt()).isZero();
    }

    @Test
    void modificaSoloMontoYVencimientoConMotivoYControlDeVersion() throws Exception {
        JsonNode creado = emitir();
        String path = PATH + "/" + creado.path("id").asLong();
        var cambio = data("montoTotal", 300, "fechaVencimiento", LocalDate.now(ZONA_NEGOCIO).plusDays(20),
                "motivo", "Corrección del importe acordado", "version", creado.path("version").asLong());
        JsonNode modificado = putOk(path, cambio);
        assertThat(modificado.path("montoTotal").asInt()).isEqualTo(300);
        assertThat(modificado.path("version").asLong()).isGreaterThan(creado.path("version").asLong());
        request("PUT", path, cambio, 409);
        cambio.put("version", modificado.path("version").asLong());
        cambio.put("motivo", "  ");
        request("PUT", path, cambio, 400);
    }

    @Test
    void cualquierTransaccionAprobadaBloqueaModificarYAnularAunqueCacheSeaCero() throws Exception {
        JsonNode creado = emitir();
        CompromisoPago compromiso = em.find(CompromisoPago.class, creado.path("id").asLong());
        TransaccionPago pago = new TransaccionPago();
        pago.setCompromisoPago(compromiso);
        pago.setApoderado(guardian);
        pago.setNumeroOperacion("US12-" + key());
        pago.setMonto(new BigDecimal("10.00"));
        pago.setEstado(EstadoTransaccionPago.APROBADA);
        pago.setMedioPago(MedioPago.EFECTIVO);
        pago.setFechaTransaccion(Instant.now());
        em.persist(pago);
        em.flush();
        String path = PATH + "/" + compromiso.getId();
        request("PUT", path, data("montoTotal", 300, "fechaVencimiento", LocalDate.now(ZONA_NEGOCIO).plusDays(20),
                "motivo", "Corrección", "version", compromiso.getVersion()), 422);
        request("POST", path + "/anulacion", data("motivo", "Emisión equivocada", "version", compromiso.getVersion()), 422);
    }

    @Test
    void anulaConMotivoConservaConsultaHistoriaYPermiteNuevaEmision() throws Exception {
        JsonNode creado = emitir();
        String path = PATH + "/" + creado.path("id").asLong();
        var body = data("motivo", " ", "version", creado.path("version").asLong());
        request("POST", path + "/anulacion", body, 400);
        body.put("motivo", "Se registró en una cuota equivocada");
        JsonNode anulado = request("POST", path + "/anulacion", body, 200);
        assertThat(anulado.path("estadoAdministrativo").asText()).isEqualTo("ANULADO");
        assertThat(anulado.path("saldo").decimalValue()).isZero();
        assertThat(anulado.path("motivoAnulacion").asText()).isEqualTo(body.get("motivo"));
        assertThat(anulado.path("fechaAnulacion").isNull()).isFalse();
        assertThat(getOk(path).path("id").asLong()).isEqualTo(creado.path("id").asLong());
        assertThat(getOk(PATH + "?estadoAdministrativo=ANULADO").path("totalElements").asInt()).isEqualTo(1);
        request("DELETE", path, null, 422);
        JsonNode reemplazo = emitir();
        assertThat(reemplazo.path("codigoCompromiso").asText()).isEqualTo("CP-000002");
        assertThat(getOk(path).path("codigoCompromiso").asText()).isEqualTo("CP-000001");
        assertThat(em.createQuery("select count(c) from CompromisoPago c", Long.class).getSingleResult()).isEqualTo(2);
        body.put("version", anulado.path("version").asLong());
        request("POST", path + "/anulacion", body, 422);
    }

    @Test
    void unCompromisoConVencimientoHoyEnLimaPermanecePendiente() throws Exception {
        var body = emision();
        body.put("fechaVencimiento", LocalDate.now(ZONA_NEGOCIO));
        JsonNode creado = postOk(PATH, body);
        String path = PATH + "/" + creado.path("id").asLong();

        assertThat(getOk(path).path("estadoFinanciero").asText()).isEqualTo("PENDIENTE");
        assertThat(getOk(PATH + "?estado=PENDIENTE").path("totalElements").asInt()).isEqualTo(1);
        assertThat(getOk(PATH + "?estado=VENCIDO").path("totalElements").asInt()).isZero();
    }

    @Test
    void rechazaEliminarInclusoSinPagosYDerivaEstadoFinancieroDeLaFecha() throws Exception {
        var body = emision();
        body.put("fechaVencimiento", LocalDate.now(ZONA_NEGOCIO).minusDays(1));
        JsonNode creado = postOk(PATH, body);
        String path = PATH + "/" + creado.path("id").asLong();
        request("DELETE", path, null, 422);
        assertThat(getOk(path).path("estadoFinanciero").asText()).isEqualTo("VENCIDO");
        assertThat(getOk(PATH + "?estado=VENCIDO").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void auditaEmisionModificacionYAnulacionConActorMotivoYValores() throws Exception {
        JsonNode creado = emitir();
        String path = PATH + "/" + creado.path("id").asLong();
        JsonNode modificado = putOk(path, data("montoTotal", 300, "fechaVencimiento", LocalDate.now(ZONA_NEGOCIO).plusDays(20),
                "motivo", "Corregir monto", "version", creado.path("version").asLong()));
        request("POST", path + "/anulacion", data("motivo", "Duplicidad administrativa",
                "version", modificado.path("version").asLong()), 200);
        var registros = em.createQuery("from RegistroAuditoria r where r.entidad='CompromisoPago' and r.registroId=:id",
                        com.asiscontrol.entity.RegistroAuditoria.class)
                .setParameter("id", creado.path("id").asText()).getResultList();
        assertThat(registros).hasSize(3).allSatisfy(registro -> {
            assertThat(registro.getActorIdentificador()).isEqualTo(admin.getUsername());
            assertThat(registro.getFechaHora()).isNotNull();
            assertThat(registro.getMotivo()).isNotBlank();
            assertThat(registro.getNuevoValor()).contains("concepto=", "cuota=", "montoTotal=", "estadoAdministrativo=");
        });
        assertThat(registros.stream().filter(registro -> !registro.getAccion().equals("EMITIR_COMPROMISO")))
                .allSatisfy(registro -> assertThat(registro.getValorAnterior()).isNotBlank());
    }

    @Test
    void permisosYRolRestringenEscrituraYLecturaSinFiltrarDatosAUnApoderadoAjeno() throws Exception {
        JsonNode creado = emitir();
        String path = PATH + "/" + creado.path("id").asLong();
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(emision()))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APODERADO"),
                                new SimpleGrantedAuthority("PAGOS_ESCRIBIR"))))
                .andExpect(status().isForbidden());
        var ajeno = jwt().jwt(token -> token.claim("personaId", -123L))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"), new SimpleGrantedAuthority("PAGOS_LEER"));
        mvc.perform(get(path).with(ajeno)).andExpect(status().isForbidden());
        String listado = mvc.perform(get(PATH).with(ajeno)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(listado).path("totalElements").asInt()).isZero();
    }
}
