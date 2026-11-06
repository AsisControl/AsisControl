package com.asiscontrol.dto;


import com.asiscontrol.dto.CompetenciaDto;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class EvaluacionDto {

    private EvaluacionDto() {
    }

    public record Request(
            @NotNull Long idAsignacionCurso,
            @NotBlank @Size(max = 160) String titulo,
            @Size(max = 1000) String descripcion,
            @NotNull TipoEvaluacion tipoEvaluacion,
            @NotNull PeriodoEvaluacion periodoEvaluacion,
            @NotNull LocalDate fechaEvaluacion,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal ponderacionPorcentaje,
            @NotEmpty Set<@NotNull Long> idsCompetencia
    ) {
    }

    public record EstadoRequest(@NotNull EstadoEvaluacion estado) {
    }

    public record Response(
            Long id,
            Long idAsignacionCurso,
            String titulo,
            String descripcion,
            TipoEvaluacion tipoEvaluacion,
            PeriodoEvaluacion periodoEvaluacion,
            EstadoEvaluacion estado,
            LocalDate fechaEvaluacion,
            BigDecimal ponderacionPorcentaje,
            Set<CompetenciaDto.Response> competencias,
            Instant fechaPublicacion,
            Instant fechaCierre,
            Instant fechaCreacion,
            Instant fechaActualizacion,
            Long idCurso,
            String curso,
            Long idSeccion,
            String seccion,
            int anioAcademico
    ) {
    }

    /** US-18: planilla de una evaluacion con los alumnos matriculados en la seccion. */
    public record PlanillaResponse(
            Response evaluacion,
            List<PlanillaAlumno> alumnos
    ) {
    }

    public record PlanillaAlumno(
            Long idAlumno,
            String codigoAlumno,
            String nombreCompleto,
            List<PlanillaNota> notas
    ) {
    }

    public record PlanillaNota(
            Long idCalificacion,
            Long idCompetencia,
            NivelLogro nivelLogro,
            long version,
            boolean corregida
    ) {
    }
}
