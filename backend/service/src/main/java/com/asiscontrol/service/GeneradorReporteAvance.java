package com.asiscontrol.service;

import com.asiscontrol.dto.ReporteAvanceDto;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class GeneradorReporteAvance {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private volatile JasperReport plantilla;

    public byte[] generar(ReporteAvanceDto reporte) {
        return generar(reporte, null);
    }

    public byte[] generar(ReporteAvanceDto reporte, Set<Long> seleccionadas) {
        try {
            // La plantilla solo referencia campos/parametros/variables directamente.
            // Formatear aqui evita depender de javac y su classpath al ejecutar el JAR.
            Map<String, Object> parametros = new HashMap<>();
            boolean parcial = seleccionadas != null;
            parametros.put("titulo", parcial ? "REPORTE DE EVALUACIONES SELECCIONADAS" : "Informe de progreso por competencias");
            String[] periodo = reporte.periodo().split(" \\| ", 3);
            parametros.put("alumno", reporte.alumno() + " | Código: " + Objects.toString(reporte.codigoAlumno(), "Sin código"));
            parametros.put("curso", reporte.curso() + " | " + reporte.grado() + "° " + reporte.seccion()
                    + " | " + periodo[0] + " | " + reporte.anio());
            parametros.put("docente", "Docente: " + reporte.docente());
            parametros.put("periodo", periodo.length == 3
                    ? periodo[1].replace("Version ", "Versión ") + " | " + periodo[2] + " | " + reporte.fechas()
                    : reporte.fechas());
            parametros.put("generado", "Generado: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.of("America/Lima")).format(reporte.generadoEn()) + " (Lima)");
            Collection<Map<String, ?>> filas = new ArrayList<>();
            var ordenadas = reporte.filas().stream()
                    .filter(fila -> seleccionadas == null || seleccionadas.contains(fila.idEvaluacion()))
                    .sorted(Comparator
                    .comparing((ReporteAvanceDto.Fila fila) -> java.time.LocalDate.parse(fila.fecha(), FECHA))
                    .thenComparing(ReporteAvanceDto.Fila::idEvaluacion)).toList();
            for (var fila : ordenadas) {
                filas.add(Map.of("competencia", fila.competencia(), "idEvaluacion", fila.idEvaluacion(),
                        "evaluacion", fila.evaluacion(), "fecha", "Fecha: " + fila.fecha(),
                        "resultado", fila.resultado(), "ponderacion", fila.ponderacion(),
                        "observacion", fila.observacion()));
            }
            var documento = JasperFillManager.fillReport(plantilla(), parametros, new JRMapCollectionDataSource(filas));
            return JasperExportManager.exportReportToPdf(documento);
        } catch (JRException | IOException e) {
            throw new IllegalStateException("No se pudo generar el informe de progreso", e);
        }
    }

    private synchronized JasperReport plantilla() throws JRException, IOException {
        if (plantilla == null) {
            try (var entrada = getClass().getResourceAsStream("/reports/reporte-avance.jrxml")) {
                if (entrada == null) throw new IOException("No se encontro la plantilla del informe");
                plantilla = JasperCompileManager.compileReport(entrada);
            }
        }
        return plantilla;
    }
}
