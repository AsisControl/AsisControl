package com.asiscontrol.controller;

import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ASISCTRL-241: equipamiento del aula como texto libre opcional de hasta 500 caracteres. */
class AulaEquipamientoHttpTest extends ApiIntegrationSupport {

    private static final String AULAS = "/api/aulas";

    @Autowired RegistroAuditoriaRepository auditorias;

    private Map<String, Object> aula(Object... extra) {
        Map<String, Object> cuerpo = data("codigo", "EQ-" + key(), "nombre", "Aula equipada",
                "tipoAula", "AULA_REGULAR", "capacidad", 30, "ubicacion", null);
        cuerpo.putAll(data(extra));
        return cuerpo;
    }

    /** PUT con los datos actuales del aula; el equipamiento solo viaja si se indica. */
    private Map<String, Object> edicion(JsonNode actual, Object... extra) {
        Map<String, Object> cuerpo = data("codigo", actual.path("codigo").asText(),
                "nombre", actual.path("nombre").asText(), "tipoAula", actual.path("tipoAula").asText(),
                "capacidad", actual.path("capacidad").asInt(), "ubicacion", actual.path("ubicacion").asText(),
                "version", actual.path("version").asLong());
        cuerpo.putAll(data(extra));
        return cuerpo;
    }

    private String ruta(JsonNode aula) {
        return AULAS + "/" + aula.path("id").asLong();
    }

    @Test
    void registraElEquipamientoRecortadoYLoDevuelveEnElDetalleYElListado() throws Exception {
        JsonNode creada = postOk(AULAS, aula("equipamiento", "  Proyector, pizarra y 20 computadoras \n "));

        assertThat(creada.path("equipamiento").asText()).isEqualTo("Proyector, pizarra y 20 computadoras");
        assertThat(getOk(ruta(creada)).path("equipamiento").asText())
                .isEqualTo("Proyector, pizarra y 20 computadoras");
        assertThat(getOk(AULAS + "?size=1000").path("content"))
                .anySatisfy(fila -> assertThat(fila.path("equipamiento").asText()).startsWith("Proyector"));
        assertThat(auditoria(creada)).anySatisfy(registro -> assertThat(registro.getNuevoValor())
                .contains("equipamiento=Proyector, pizarra y 20 computadoras"));
    }

    @Test
    void esOpcionalYEnBlancoEquivaleASinEquipamiento() throws Exception {
        assertThat(postOk(AULAS, aula()).path("equipamiento").isNull()).isTrue();
        assertThat(postOk(AULAS, aula("equipamiento", null)).path("equipamiento").isNull()).isTrue();
        assertThat(postOk(AULAS, aula("equipamiento", "  \n ")).path("equipamiento").isNull()).isTrue();
    }

    @Test
    void admite500CaracteresYaRecortadosYRechaza501() throws Exception {
        String justo = "a".repeat(500);
        JsonNode creada = postOk(AULAS, aula("equipamiento", "   " + justo + "   "));
        assertThat(creada.path("equipamiento").asText()).isEqualTo(justo);

        JsonNode rechazoAlta = request("POST", AULAS, aula("equipamiento", "b".repeat(501)), 422);
        assertThat(rechazoAlta.path("code").asText()).isEqualTo("AULA_EQUIPAMIENTO_LARGO");
        assertThat(rechazoAlta.path("fieldErrors").path(0).path("field").asText()).isEqualTo("equipamiento");
        assertThat(rechazoAlta.path("fieldErrors").path(0).path("message").asText())
                .isEqualTo("El equipamiento no puede superar 500 caracteres.");

        JsonNode rechazoEdicion = request("PUT", ruta(creada),
                edicion(creada, "equipamiento", "c".repeat(501)), 422);
        assertThat(rechazoEdicion.path("code").asText()).isEqualTo("AULA_EQUIPAMIENTO_LARGO");
        assertThat(getOk(ruta(creada)).path("equipamiento").asText()).isEqualTo(justo);
    }

    @Test
    void unPutQueOmiteOAnulaElEquipamientoLoConserva() throws Exception {
        JsonNode creada = postOk(AULAS, aula("equipamiento", "Proyector"));

        JsonNode sinCampo = putOk(ruta(creada), edicion(creada, "nombre", "Aula renombrada"));
        assertThat(sinCampo.path("equipamiento").asText()).isEqualTo("Proyector");

        JsonNode conNulo = putOk(ruta(creada), edicion(sinCampo, "capacidad", 40, "equipamiento", null));
        assertThat(conNulo.path("equipamiento").asText()).isEqualTo("Proyector");
        assertThat(getOk(ruta(creada)).path("equipamiento").asText()).isEqualTo("Proyector");
    }

    @Test
    void unPutLoReemplazaOLoBorraConTextoEnBlancoYLoAudita() throws Exception {
        JsonNode creada = postOk(AULAS, aula("equipamiento", "Proyector"));

        JsonNode cambiada = putOk(ruta(creada), edicion(creada, "equipamiento", " Pizarra digital "));
        assertThat(cambiada.path("equipamiento").asText()).isEqualTo("Pizarra digital");

        JsonNode borrada = putOk(ruta(creada), edicion(cambiada, "equipamiento", "  "));
        assertThat(borrada.path("equipamiento").isNull()).isTrue();

        assertThat(auditoria(creada)).filteredOn(r -> r.getAccion().equals("ACTUALIZAR_AULA"))
                .extracting(RegistroAuditoria::getValorAnterior, RegistroAuditoria::getNuevoValor)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "equipamiento=Proyector", "equipamiento=Pizarra digital"),
                        org.assertj.core.groups.Tuple.tuple(
                                "equipamiento=Pizarra digital", "equipamiento=null"));
    }

    @Test
    void soloQuienEscribeEnAcademicoRegistraElEquipamiento() throws Exception {
        JsonNode creada = postOk(AULAS, aula("equipamiento", "Proyector"));
        byte[] alta = json.writeValueAsBytes(aula("equipamiento", "Pizarra"));
        byte[] cambio = json.writeValueAsBytes(edicion(creada, "equipamiento", "Pizarra"));

        mvc.perform(post(AULAS).contentType(MediaType.APPLICATION_JSON).content(alta))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(ruta(creada)).contentType(MediaType.APPLICATION_JSON).content(cambio))
                .andExpect(status().isUnauthorized());
        for (String rol : new String[] {"DOCENTE", "ALUMNO", "APODERADO"}) {
            var lector = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol),
                    new SimpleGrantedAuthority("ACADEMICO_LEER"));
            mvc.perform(post(AULAS).with(lector).contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isForbidden());
            mvc.perform(put(ruta(creada)).with(lector).contentType(MediaType.APPLICATION_JSON).content(cambio))
                    .andExpect(status().isForbidden());
        }
        assertThat(getOk(ruta(creada)).path("equipamiento").asText()).isEqualTo("Proyector");
    }

    private java.util.List<RegistroAuditoria> auditoria(JsonNode aula) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("Aula", aula.path("id").asText());
    }
}
