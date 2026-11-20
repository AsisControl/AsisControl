package com.asiscontrol.dto;

import java.util.List;

/** Contexto persistido de la fila y sus evidencias actualmente vinculadas y autorizadas. */
public record SustentoInformeDto(Long idInforme, Long idDetalleInforme, int numeroVersion,
        Long idAlumno, Long idAsignacionCurso, Long idPeriodoAcademico,
        Long idEvaluacion, Long idCompetencia, Long idCalificacion, List<Evidencia> evidencias) {
    public record Evidencia(Long idEvidencia, String descripcion, Adjunto archivo) { }
    public record Adjunto(Long idArchivo, String nombreOriginal, String tipoMime,
                          long tamanioBytes, String urlDescarga) { }
}
