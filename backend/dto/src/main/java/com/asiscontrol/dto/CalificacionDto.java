package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.NivelLogro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CalificacionDto {

    private CalificacionDto() {
    }

    public record Request(
            @NotNull Long idEvaluacion,
            @NotNull Long idAlumno,
            @NotNull Long idCompetencia,
            @NotNull NivelLogro nivelLogro,
            @Size(max = 1000) String observacion
    ) {
    }

    public record CargaLoteRequest(
            @NotEmpty List<@Valid Request> calificaciones
    ) {
    }

    public record Response(
            Long id,
            Long idEvaluacion,
            String tituloEvaluacion,
            Long idAlumno,
            Long idCompetencia,
            String nombreCompetencia,
            NivelLogro nivelLogro,
            String observacion,
            Instant fechaRegistro,
            Instant fechaActualizacion,
            long version
    ) {
    }

    /** US-50: una calificacion a corregir; la version evita pisar un cambio de otro usuario. */
    public record CorreccionItem(
            @NotNull Long idCalificacion,
            @NotNull NivelLogro nivelLogro,
            @NotNull Long version
    ) {
    }

    /** US-50: el motivo es comun a todos los cambios guardados a la vez. */
    public record CorreccionRequest(
            @NotBlank @Size(max = 500) String motivo,
            @NotEmpty List<@NotNull @Valid CorreccionItem> correcciones
    ) {
    }

    public record CorreccionResponse(
            int numeroVersion,
            NivelLogro nivelAnterior,
            NivelLogro nivelNuevo,
            String motivo,
            String autor,
            Instant fecha
    ) {
    }

    public record ConsolidadoResponse(
            Long idCompetencia,
            String codigoCompetencia,
            String nombreCompetencia,
            BigDecimal promedioPonderado,
            NivelLogro nivelConsolidado,
            int evaluacionesConsideradas
    ) {
    }
}
