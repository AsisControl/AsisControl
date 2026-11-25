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
    private volatile JasperReport plantilla;

    public byte[] generar(ReporteAvanceDto reporte) {
        try {
            // La plantilla solo referencia campos/parametros/variables directamente.
            // Formatear aqui evita depender de javac y su classpath al ejecutar el JAR.
            Map<String, Object> parametros = new HashMap<>();
            parametros.put("alumno", reporte.alumno() + " | Codigo: " + reporte.codigoAlumno());
            parametros.put("curso", reporte.curso() + " | " + reporte.grado() + "° " + reporte.seccion() + " | " + reporte.anio());
            parametros.put("docente", "Docente: " + reporte.docente());
            var regla = reporte.reglaCalificacion();
            parametros.put("periodo", reporte.periodo() + " | " + reporte.fechas()
                    + (regla == null ? "" : " | Regla v" + regla.numeroVersion()));
            parametros.put("generado", "Generado: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.of("America/Lima")).format(reporte.generadoEn()) + " (Lima)");
            Collection<Map<String, ?>> filas = new ArrayList<>();
            for (var fila : reporte.filas()) {
                filas.add(Map.of("competencia", fila.competencia(), "evaluacion", fila.evaluacion() + "\n" + fila.fecha(),
                        "fecha", fila.fecha(), "resultado", fila.resultado(), "ponderacion", fila.ponderacion(),
                        "observacion", fila.observacion()));
            }
            for (var consolidado : reporte.consolidado()) {
                var nivel = consolidado.nivelConsolidado();
                filas.add(Map.of("competencia", consolidado.nombreCompetencia(),
                        "evaluacion", "Consolidado por regla", "fecha", "", "ponderacion", "—",
                        "resultado", nivel == null ? "Datos insuficientes" : nivel.name(),
                        "observacion", String.join(", ", consolidado.componentesFaltantes())));
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
