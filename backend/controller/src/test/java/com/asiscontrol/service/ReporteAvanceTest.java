package com.asiscontrol.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.asiscontrol.dto.ReporteAvanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReporteAvanceTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repiteElNombreRealDelBloqueEnLaPaginaSiguiente(boolean parcial) throws Exception {
        var filas = new ArrayList<ReporteAvanceDto.Fila>();
        for (long i = 1; i <= 41; i++) {
            filas.add(new ReporteAvanceDto.Fila(1L, i, 50L, 1L, i, "Competencia del examen",
                    "Examen", "04/04/2027", "A", "30 %", "Observación del examen", "EXAMEN", "PUBLICADA"));
        }
        filas.add(new ReporteAvanceDto.Fila(1L, 49L, 60L, 49L, 49L, "Competencia de la prueba",
                "Prueba", "04/06/2027", "AD", "70 %", "Observación de la prueba", "EXAMEN", "PUBLICADA"));
        var dto = new ReporteAvanceDto("Ana María Pérez", "A001", "Matemática", "José Sánchez", 2, "A", 2027,
                "Primer período | Version 1 | PUBLICADA", "01/04/2027 - 30/06/2027", Instant.parse("2027-06-30T20:00:00Z"), filas);
        var generador = new GeneradorReporteAvance();
        byte[] pdf = parcial ? generador.generar(dto, Set.of(60L, 50L)) : generador.generar(dto);
        try (var reader = new PdfReader(pdf)) {
            var extractor = new PdfTextExtractor(reader);
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            String titulo = parcial ? "Informe parcial de progreso" : "Informe de progreso por competencias";
            assertThat(extractor.getTextFromPage(1)).contains(titulo, "Ana María Pérez | Código: A001",
                    "Primer período | 2027", "Docente: José Sánchez", "Versión 1 | PUBLICADA | 01/04/2027 - 30/06/2027");
            assertThat(extractor.getTextFromPage(2)).containsSubsequence("Competencia del examen", "Examen", "Fecha: 04/04/2027");
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                String text = extractor.getTextFromPage(page);
                assertThat(text).doesNotContain("EVALUACIÓN 1", "EVALUACIÓN 2", "Título:");
                if (text.contains("Examen")) {
                    assertThat(text).contains("Competencia del examen", "Nivel", "Peso", "Observación")
                            .doesNotContain("Evaluación / fecha");
                }
                if (text.contains("Prueba")) {
                    assertThat(text).containsSubsequence("Competencia de la prueba", "Prueba", "Fecha: 04/06/2027");
                }
            }
            assertThat(extractor.getTextFromPage(reader.getNumberOfPages())).contains("Prueba", "Competencia de la prueba");
        }
        Files.createDirectories(Path.of("target", "reportes-prueba"));
        String nombre = parcial ? "avance-parcial-multipagina.pdf" : "avance-completo-multipagina.pdf";
        Files.write(Path.of("target", "reportes-prueba", nombre), pdf);
    }

    @Test void generaPdfConPlantillaRealYTextoLargo() throws Exception {
        var filas = new ArrayList<ReporteAvanceDto.Fila>();
        for (int i=1; i<=35; i++) {
            long evaluacion = (i - 1) % 7 + 1L;
            filas.add(new ReporteAvanceDto.Fila(1L, (long) i, evaluacion, 1L, (long) i, "Resuelve problemas de cantidad",
                    "Evaluación " + evaluacion, "15/03/2026", "AD", "30 %", i == 1
                    ? "Debe reforzar la interpretación y explicar el procedimiento. ".repeat(16) + "Fin de la observación."
                    : "Sin observaciones", "EXAMEN", "PUBLICADA"));
        }
        var dto = new ReporteAvanceDto("Ana María Pérez", "A001", "Matemática", "José Sánchez",
                2,"A",2026,"Primer bimestre | Version 1 | PUBLICADA","01/03/2026 - 30/04/2026",Instant.parse("2026-04-30T20:00:00Z"),filas);
        byte[] pdf = new GeneradorReporteAvance().generar(dto);
        assertThat(new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5000);
        try (var reader = new PdfReader(pdf)) {
            var extractor = new PdfTextExtractor(reader);
            var text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                String pageText = extractor.getTextFromPage(page);
                assertThat(pageText).contains("Evaluación", "Resuelve problemas de cantidad", "Observación");
                text.append(pageText);
            }
            assertThat(text.toString()).contains("Versión 1 | PUBLICADA", "Fin de la observación.");
            for (int evaluation = 1; evaluation <= 7; evaluation++) {
                assertThat(text.toString()).contains("Evaluación " + evaluation);
            }
        }
        Files.createDirectories(Path.of("target", "reportes-prueba"));
        Files.write(Path.of("target", "reportes-prueba", "avance.pdf"), pdf);
    }
}
