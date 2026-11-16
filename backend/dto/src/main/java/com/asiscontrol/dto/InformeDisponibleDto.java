package com.asiscontrol.dto;

public record InformeDisponibleDto(Long idAlumno, String alumno, Long idAsignacionCurso,
        String curso, int anio, Long idPeriodoAcademico, String periodo) { }
