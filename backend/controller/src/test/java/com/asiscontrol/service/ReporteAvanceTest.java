package com.asiscontrol.service;

import com.asiscontrol.dto.ReporteAvanceDto;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReporteAvanceTest {
    @Test void generaPdfConPlantillaRealYTextoLargo() throws Exception {
        var filas = new ArrayList<ReporteAvanceDto.Fila>();
        for (int i=1; i<=35; i++) filas.add(new ReporteAvanceDto.Fila(1L, (long) i, (long) i, 1L, (long) i, "Resuelve problemas de cantidad",
                "Evaluación " + i, "15/03/2026", "AD", "30 %", i == 1
                ? "Debe reforzar la interpretación y explicar el procedimiento. ".repeat(16) : "Sin observaciones"));
        var dto = new ReporteAvanceDto("Ana María Pérez", "A001", "Matemática", "José Sánchez",
                2,"A",2026,"Primer bimestre","01/03/2026 - 30/04/2026",Instant.parse("2026-04-30T20:00:00Z"),filas);
        byte[] pdf = new GeneradorReporteAvance().generar(dto);
        assertThat(new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5000);
        Files.createDirectories(Path.of("target", "reportes-prueba"));
        Files.write(Path.of("target", "reportes-prueba", "avance.pdf"), pdf);
    }
}
