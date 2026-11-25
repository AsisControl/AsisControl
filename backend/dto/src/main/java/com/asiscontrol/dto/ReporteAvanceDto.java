package com.asiscontrol.dto;

import java.time.Instant;
import java.util.List;

/** Datos de consulta/PDF con los identificadores persistidos de cada fila. */
public record ReporteAvanceDto(
        String alumno, String codigoAlumno, String curso, String docente,
        int grado, String seccion, int anio, String periodo, String fechas,
        Instant generadoEn, List<Fila> filas,
        ReglaCalificacionDto.Aplicada reglaCalificacion, List<CalificacionDto.ConsolidadoResponse> consolidado) {
    public ReporteAvanceDto(String alumno, String codigoAlumno, String curso, String docente,
                            int grado, String seccion, int anio, String periodo, String fechas,
                            Instant generadoEn, List<Fila> filas) {
        this(alumno, codigoAlumno, curso, docente, grado, seccion, anio, periodo, fechas, generadoEn,
                filas, null, List.of());
    }

    public record Fila(Long idInforme, Long idDetalleInforme, Long idEvaluacion,
                       Long idCompetencia, Long idCalificacion,
                       String competencia, String evaluacion, String fecha,
                       String resultado, String ponderacion, String observacion) { }
}
