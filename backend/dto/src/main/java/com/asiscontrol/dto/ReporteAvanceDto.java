package com.asiscontrol.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Datos de consulta/PDF con los identificadores persistidos de cada fila. */
public record ReporteAvanceDto(
        String alumno, String codigoAlumno, String curso, String docente,
        int grado, String seccion, int anio, String periodo, String fechas,
        Instant generadoEn, List<Fila> filas,
        ReglaCalificacionDto.Aplicada reglaCalificacion, List<CalificacionDto.ConsolidadoResponse> consolidado,
        Long versionBorrador, Map<Long, String> conclusionesDescriptivas,
        List<Long> competenciasCConsecutivas) {
    public ReporteAvanceDto(String alumno, String codigoAlumno, String curso, String docente,
                            int grado, String seccion, int anio, String periodo, String fechas,
                            Instant generadoEn, List<Fila> filas) {
        this(alumno, codigoAlumno, curso, docente, grado, seccion, anio, periodo, fechas, generadoEn,
                filas, null, List.of(), null, Map.of(), List.of());
    }

    public record Fila(Long idInforme, Long idDetalleInforme, Long idEvaluacion,
                       Long idCompetencia, Long idCalificacion,
                       String competencia, String evaluacion, String fecha,
                       String resultado, String ponderacion, String observacion,
                       String tipoEvaluacion, String estadoEvaluacion, boolean tieneSustento, Integer numeroVersionEscala,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                       java.math.BigDecimal notaNumerica, java.math.BigDecimal pesoInstitucional) {
        public Fila(Long idInforme, Long idDetalleInforme, Long idEvaluacion, Long idCompetencia, Long idCalificacion,
                    String competencia, String evaluacion, String fecha, String resultado, String ponderacion,
                    String observacion, String tipoEvaluacion, String estadoEvaluacion, boolean tieneSustento) {
            this(idInforme, idDetalleInforme, idEvaluacion, idCompetencia, idCalificacion, competencia, evaluacion,
                    fecha, resultado, ponderacion, observacion, tipoEvaluacion, estadoEvaluacion, tieneSustento, null, null, null);
        }
        Fila sinNotaNumerica() {
            return new Fila(idInforme, idDetalleInforme, idEvaluacion, idCompetencia, idCalificacion,
                    competencia, evaluacion, fecha, resultado, ponderacion, observacion, tipoEvaluacion,
                    estadoEvaluacion, tieneSustento, numeroVersionEscala, null, pesoInstitucional);
        }
        public Fila(Long idInforme, Long idDetalleInforme, Long idEvaluacion, Long idCompetencia, Long idCalificacion,
                    String competencia, String evaluacion, String fecha, String resultado, String ponderacion,
                    String observacion, String tipoEvaluacion, String estadoEvaluacion) {
            this(idInforme, idDetalleInforme, idEvaluacion, idCompetencia, idCalificacion, competencia,
                    evaluacion, fecha, resultado, ponderacion, observacion, tipoEvaluacion, estadoEvaluacion, false);
        }
    }

    public ReporteAvanceDto sinPromediosNumericos() {
        return new ReporteAvanceDto(alumno, codigoAlumno, curso, docente, grado, seccion, anio, periodo, fechas,
                generadoEn, filas.stream().filter(fila -> !"BORRADOR".equals(fila.estadoEvaluacion())).map(Fila::sinNotaNumerica).toList(), reglaCalificacion,
                consolidado.stream().map(CalificacionDto.ConsolidadoResponse::sinPromedioNumerico).toList(), null, conclusionesDescriptivas, List.of());
    }
}
