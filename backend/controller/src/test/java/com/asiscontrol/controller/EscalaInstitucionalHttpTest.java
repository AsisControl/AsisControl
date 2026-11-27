package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EscalaInstitucionalHttpTest extends ApiIntegrationSupport {
    private static final String ESCALA = "/api/escala-institucional";
    private static final String SIMULAR = "/api/reglas-calificacion/vista-previa-numerica";

    @Test void guardaBorradorIncompletoYValidaActivacionYVersiones() throws Exception {
        var docente = jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"));
        mvc.perform(get(ESCALA).with(docente)).andExpect(status().isForbidden());
        var borrador = putOk(ESCALA + "/borrador", data("rangos", List.of(rango("C", 0, 8))));
        String activar = ESCALA + "/" + borrador.path("id").asLong() + "/activar";
        var mensaje = data("version", borrador.path("version").asLong(), "motivo", "Validación de pruebas");
        assertThat(request("POST", activar, mensaje, 422).path("code").asText()).isEqualTo("RANGOS_ESCALA_INVALIDOS");
        assertThat(request("PUT", ESCALA + "/borrador", data("version", borrador.path("version").asLong(),
                "rangos", List.of(rango("C", 0, 9), rango("B", 9, 12))), 422).path("code").asText())
                .isEqualTo("RANGOS_ESCALA_INVALIDOS");
        assertThat(request("PUT", ESCALA + "/borrador", data("rangos", rangos()), 409).path("code").asText())
                .isEqualTo("VERSION_DESACTUALIZADA");
        var completa = putOk(ESCALA + "/borrador", data("version", borrador.path("version").asLong(), "rangos", rangos()));
        mensaje.put("version", completa.path("version").asLong());
        var vigente = request("POST", activar, mensaje, 200);
        assertThat(vigente.path("numeroVersion").asInt()).isEqualTo(1);
        assertThat(vigente.path("actorAplicacion").asText()).isNotBlank();
        assertThat(vigente.path("fechaAplicacion").asText()).isNotBlank();
        assertThat(vigente.path("motivoAplicacion").asText()).isEqualTo("Validación de pruebas");
        var iguales = new java.util.ArrayList<>(rangos());
        java.util.Collections.reverse(iguales);
        assertThat(request("PUT", ESCALA + "/borrador", data("rangos", iguales), 422).path("code").asText())
                .isEqualTo("ESCALA_CONFIGURACION_VIGENTE");
        var distintos = List.of(rango("C", 0, 8), rango("B", 9, 12), rango("A", 13, 17), rango("AD", 18, 20));
        var nueva = putOk(ESCALA + "/borrador", data("rangos", distintos));
        assertThat(putOk(ESCALA + "/borrador", data("rangos", distintos)).path("id")).isEqualTo(nueva.path("id"));
        request("POST", ESCALA + "/" + nueva.path("id").asLong() + "/activar",
                data("version", nueva.path("version").asLong(), "motivo", "Cambio de escala"), 200);
        assertThat(getOk(ESCALA).findValuesAsText("estado")).containsExactly("VIGENTE", "HISTORICA");
        assertThat(putOk(ESCALA + "/borrador", data("rangos", iguales)).path("numeroVersion").asInt()).isEqualTo(3);
    }

    @Test void simulaConEscalaVigenteSinRedondearAntesYConservaElCero() throws Exception {
        var componentes = List.of(
                data("tipoEvaluacion", "EXAMEN", "nombre", "Exámenes", "peso", 50),
                data("tipoEvaluacion", "PROYECTO", "nombre", "Proyectos", "peso", 50));
        var entrada = data("formula", "PROMEDIO_PONDERADO", "componentes", componentes, "muestra", List.of(
                data("tipoEvaluacion", "EXAMEN", "nota", "17.49"),
                data("tipoEvaluacion", "EXAMEN", "nota", "17.51"),
                data("tipoEvaluacion", "PROYECTO", "nota", "17.50")));
        assertThat(request("POST", SIMULAR, entrada, 422).path("code").asText()).isEqualTo("ESCALA_INSTITUCIONAL_NO_VIGENTE");
        activarNueva();
        var resultado = request("POST", SIMULAR, entrada, 200);
        assertThat(resultado.path("promedioPonderado").decimalValue()).isEqualByComparingTo("17.5");
        assertThat(resultado.path("notaRedondeada").asInt()).isEqualTo(18);
        assertThat(resultado.path("nivelConsolidado").asText()).isEqualTo("AD");
        assertThat(resultado.path("numeroVersionEscala").asInt()).isEqualTo(1);
        entrada.put("muestra", List.of(data("tipoEvaluacion", "EXAMEN", "nota", 0),
                data("tipoEvaluacion", "PROYECTO", "nota", null)));
        resultado = request("POST", SIMULAR, entrada, 200);
        assertThat(resultado.path("evaluacionesConsideradas").asInt()).isEqualTo(1);
        assertThat(resultado.path("notaRedondeada").isNull()).isTrue();
        assertThat(resultado.path("componentesFaltantes").get(0).asText()).isEqualTo("Proyectos");
        entrada.put("muestra", List.of(data("tipoEvaluacion", "EXAMEN", "nota", "20.1")));
        request("POST", SIMULAR, entrada, 400);
    }

    @Test void fijaLaEscalaAlPublicarYUsaLaMismaEnUnaCorreccion() throws Exception {
        activarNueva();
        long evaluacion = postOk("/api/evaluaciones", data("idAsignacionCurso", assignment.getId(),
                "titulo", "Proyecto numérico", "tipoEvaluacion", "PROYECTO", "periodoEvaluacion", "BIMESTRAL",
                "fechaEvaluacion", "2090-03-10", "idsCompetencia", List.of(competence.getId()))).path("id").asLong();
        var entrada = data("idEvaluacion", evaluacion, "idAlumno", student.getId(), "idCompetencia", competence.getId(),
                "notaNumerica", "17.49", "observacion", "Comentario del docente");
        assertThat(request("POST", "/api/calificaciones", entrada, 422).path("code").asText())
                .isEqualTo("NIVEL_CUALITATIVO_REQUERIDO");
        em.find(com.asiscontrol.entity.Evaluacion.class, evaluacion).setModeloCualitativo(false);
        em.flush();
        var registrada = postOk("/api/calificaciones", entrada);
        assertThat(registrada.path("nivelLogro").asText()).isEqualTo("A");

        var borrador = putOk(ESCALA + "/borrador", data("rangos",
                List.of(rango("C", 0, 9), rango("B", 10, 12), rango("A", 13, 16), rango("AD", 17, 20))));
        request("POST", ESCALA + "/" + borrador.path("id").asLong() + "/activar",
                data("version", borrador.path("version").asLong(), "motivo", "Cambio antes de publicar"), 200);
        putOk("/api/evaluaciones/" + evaluacion + "/estado", data("estado", "PUBLICADA"));
        activarNueva();

        String planillaUrl = "/api/evaluaciones/" + evaluacion + "/planilla";
        var planilla = getOk(planillaUrl);
        assertThat(planilla.path("numeroVersionEscala").asInt()).isEqualTo(2);
        var nota = planilla.path("alumnos").get(0).path("notas").get(0);
        assertThat(nota.path("notaNumerica").decimalValue()).isEqualByComparingTo("17.49");
        assertThat(nota.path("nivelLogro").asText()).isEqualTo("AD");
        request("POST", "/api/calificaciones/correcciones", data("motivo", "Corrección de digitación", "correcciones",
                List.of(data("idCalificacion", nota.path("idCalificacion").asLong(), "version", nota.path("version").asLong(),
                        "notaNumerica", "17.01"))), 200);
        var corregida = getOk(planillaUrl).path("alumnos").get(0).path("notas").get(0);
        assertThat(corregida.path("notaNumerica").decimalValue()).isEqualByComparingTo("17.01");
        assertThat(corregida.path("nivelLogro").asText()).isEqualTo("AD");
        var historial = getOk("/api/calificaciones/" + nota.path("idCalificacion").asLong() + "/correcciones");
        assertThat(historial.get(0).path("notaAnterior").decimalValue()).isEqualByComparingTo("17.49");
    }

    private void activarNueva() throws Exception {
        var borrador = putOk(ESCALA + "/borrador", data("rangos", rangos()));
        request("POST", ESCALA + "/" + borrador.path("id").asLong() + "/activar",
                data("version", borrador.path("version").asLong(), "motivo", "Escala de prueba"), 200);
    }

    private List<?> rangos() {
        return List.of(rango("C", 0, 9), rango("B", 10, 12), rango("A", 13, 17), rango("AD", 18, 20));
    }

    private Object rango(String nivel, int minimo, int maximo) {
        return data("nivel", nivel, "minimo", minimo, "maximo", maximo);
    }
}
