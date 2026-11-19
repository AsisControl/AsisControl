package com.asiscontrol.dto;

import java.time.Instant;
import java.util.List;

/** Datos de una descarga; no representa una entidad ni una version persistida. */
public record ReporteAvanceDto(
        String alumno, String codigoAlumno, String curso, String docente,
        int grado, String seccion, int anio, String periodo, String fechas,
        Instant generadoEn, List<Fila> filas) {
    public record Fila(String competencia, String evaluacion, String fecha,
                       String resultado, String ponderacion, String observacion) { }
}
