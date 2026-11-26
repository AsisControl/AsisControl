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
        return generar(reporte, seleccionadas, null);
    }

    public byte[] generar(ReporteAvanceDto reporte, Set<Long> seleccionadas, Set<Long> competencias) {
        return generar(reporte, seleccionadas, competencias, false);
    }

    public byte[] generarParcialVigente(ReporteAvanceDto reporte) {
        return generar(reporte, reporte.filas().stream().map(ReporteAvanceDto.Fila::idEvaluacion)
                .collect(java.util.stream.Collectors.toSet()), null, true);
    }

    private byte[] generar(ReporteAvanceDto reporte, Set<Long> seleccionadas, Set<Long> competencias, boolean parcialVigente) {
        try {
            // La plantilla solo referencia campos/parametros/variables directamente.
            // Formatear aqui evita depender de javac y su classpath al ejecutar el JAR.
            Map<String, Object> parametros = new HashMap<>();
            boolean parcial = seleccionadas != null;
            parametros.put("titulo", parcial ? "Informe parcial de progreso" : "Informe de progreso por competencias");
            String[] periodo = reporte.periodo().split(" \\| ", 3);
            parametros.put("alumno", reporte.alumno() + " | Código: " + Objects.toString(reporte.codigoAlumno(), "Sin código"));
            parametros.put("curso", reporte.curso() + " | " + reporte.grado() + "° " + reporte.seccion()
                    + " | " + periodo[0] + " | " + reporte.anio());
            parametros.put("docente", "Docente: " + reporte.docente());
            var regla = reporte.reglaCalificacion();
            String estadoPeriodo = periodo.length == 3
                    ? periodo[1].replace("Version ", "Versión ") + " | " + periodo[2] + " | " + reporte.fechas()
                    : reporte.fechas();
            var escalas = (parcialVigente ? reporte.filas().stream().map(ReporteAvanceDto.Fila::numeroVersionEscala)
                    : reporte.consolidado().stream().map(item -> item.numeroVersionEscala()))
                    .filter(Objects::nonNull).distinct().sorted().map(version -> "v" + version).toList();
            parametros.put("periodo", estadoPeriodo + (regla == null ? "" : " | Regla v" + regla.numeroVersion())
                    + (escalas.isEmpty() ? "" : " | Escala " + String.join(", ", escalas)));
            parametros.put("pesoTitulo", regla == null ? "Peso" : "Peso efectivo");
            parametros.put("notaPonderacion", parcialVigente
                    ? "Pesos por estudiante y competencia calculados sobre el período completo."
                    : "Ponderación por estudiante y competencia según la versión de la libreta.");
            parametros.put("generado", "Generado: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.of("America/Lima")).format(reporte.generadoEn()) + " (Lima)");
            Collection<Map<String, ?>> filas = new ArrayList<>();
            var ordenadas = reporte.filas().stream()
                    .filter(fila -> seleccionadas == null || seleccionadas.contains(fila.idEvaluacion()))
                    .filter(fila -> competencias == null || competencias.contains(fila.idCompetencia()))
                    .sorted(Comparator
                    .comparing((ReporteAvanceDto.Fila fila) -> java.time.LocalDate.parse(fila.fecha(), FECHA))
                    .thenComparing(ReporteAvanceDto.Fila::idEvaluacion)).toList();
            var porCompetencia = new LinkedHashMap<Long, List<ReporteAvanceDto.Fila>>();
            if (!parcial) reporte.consolidado().forEach(item -> porCompetencia.put(item.idCompetencia(), new ArrayList<>()));
            for (var fila : ordenadas) {
                porCompetencia.computeIfAbsent(fila.idCompetencia(), id -> new ArrayList<>()).add(fila);
            }
            var resumenes = parcial ? Map.<Long, com.asiscontrol.dto.CalificacionDto.ConsolidadoResponse>of()
                    : reporte.consolidado().stream().collect(java.util.stream.Collectors.toMap(item -> item.idCompetencia(), item -> item));
            porCompetencia.forEach((id, resultados) -> {
                var resumen = resumenes.get(id);
                String nombre = resumen == null ? resultados.getFirst().competencia() : resumen.nombreCompetencia();
                String estado = resumen == null ? "" : resumen.nivelConsolidado() != null ? "Nivel consolidado: " + resumen.nivelConsolidado()
                        : "NO_EVALUADA".equals(resumen.estadoAvance()) ? "Aún no evaluada" : "Pendiente";
                var notas = new ArrayList<String>();
                if (!estado.isBlank()) notas.add(estado);
                if (resumen != null && !resumen.componentesFaltantes().isEmpty()) notas.add(String.join(" · ", resumen.componentesFaltantes()));
                if (!parcial && reporte.conclusionesDescriptivas().containsKey(id)) {
                    notas.add("Conclusión descriptiva histórica: " + reporte.conclusionesDescriptivas().get(id));
                }
                String detalle = String.join("\n", notas);
                if (resultados.isEmpty()) filas.add(Map.of("idCompetencia", id, "competencia", nombre,
                        "detalleCompetencia", detalle, "evaluacion", "Sin evaluaciones registradas",
                        "resultado", "—", "ponderacion", "—", "observacion", "—"));
                for (var fila : resultados) {
                    filas.add(Map.of("idCompetencia", id, "competencia", nombre, "detalleCompetencia", detalle,
                            "evaluacion", fila.evaluacion() + "\nFecha: " + fila.fecha(), "resultado", fila.resultado(),
                            "ponderacion", fila.ponderacion(), "observacion", Objects.toString(fila.observacion(), "Sin observaciones")));
                }
            });
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
