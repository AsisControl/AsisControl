package com.asiscontrol.controller;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompetenciasReutilizacionHttpTest extends ApiIntegrationSupport {
    private static final String IMPORTAR = "/api/competencias/importaciones";
    private static final String COPIAR = "/api/cursos-competencias/copiar";

    @Test void revisaExcelSinEscribirYConfirmaSoloCompetenciasValidas() throws Exception {
        String codigo = "IMP-" + key();
        var archivo = excel(codigo);
        var revision = json.readTree(mvc.perform(multipart(IMPORTAR).file(archivo).with(asAdmin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(revision.path("validas").asInt()).isOne();
        assertThat(revision.path("creadas").asInt()).isZero();
        assertThat(revision.path("omitidas").get(0).path("row").asInt()).isEqualTo(3);
        assertThat(revision.path("errores").get(0).path("row").asInt()).isEqualTo(4);
        assertThat(getOk("/api/competencias?busqueda=" + codigo).path("content").size()).isZero();

        var resultado = json.readTree(mvc.perform(multipart(IMPORTAR).file(archivo)
                        .param("confirmar", "true").with(asAdmin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(resultado.path("creadas").asInt()).isOne();
        assertThat(getOk("/api/competencias?busqueda=" + codigo).path("content").size()).isOne();
    }

    @Test void agregaLaSeleccionEnLoteYSaltarLoExistentePermiteReintentar() throws Exception {
        long destino = postOk("/api/cursos", data("codigo", "REP-" + key(), "nombre", "Comunicación siguiente",
                "horasSemanales", 4)).path("id").asLong();
        var seleccion = data("origen", course.getId(), "destino", destino, "competencias", List.of(competence.getId()));
        assertThat(request("POST", COPIAR, seleccion, 200).path("agregadas").asInt()).isOne();
        assertThat(request("POST", COPIAR, seleccion, 200).path("agregadas").asInt()).isZero();
        assertThat(getOk("/api/cursos-competencias/curso/" + destino).size()).isOne();
        assertThat(getOk("/api/cursos-competencias/curso/" + course.getId()).size()).isOne();
    }

    private MockMultipartFile excel(String codigo) throws Exception {
        try (var libro = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Competencias");
            var filas = List.of(List.of("codigo", "nombre"), List.of(codigo, "Competencia de prueba"),
                    List.of(codigo.toLowerCase(java.util.Locale.ROOT), "Repetida"), List.of("SIN-NOMBRE"));
            for (int i = 0; i < filas.size(); i++) {
                var fila = hoja.createRow(i);
                for (int j = 0; j < filas.get(i).size(); j++) fila.createCell(j).setCellValue(filas.get(i).get(j));
            }
            libro.write(bytes);
            return new MockMultipartFile("archivo", "competencias.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }
}
