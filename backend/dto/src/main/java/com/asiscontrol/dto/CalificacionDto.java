package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.NivelLogro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;

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
            NivelLogro nivelLogro,
            @Size(max = 1000) String observacion,
            @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 10) BigDecimal notaNumerica
    ) {
        public Request(Long idEvaluacion, Long idAlumno, Long idCompetencia, NivelLogro nivelLogro, String observacion) {
            this(idEvaluacion, idAlumno, idCompetencia, nivelLogro, observacion, null);
        }

        @AssertTrue(message = "Envía una nota numérica o un nivel histórico.")
        public boolean isCalificacionDefinida() { return (nivelLogro == null) != (notaNumerica == null); }
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
            NivelLogro nivelLogro,
            @NotNull Long version,
            @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 10) BigDecimal notaNumerica
    ) {
        @AssertTrue(message = "Envía una nota numérica o un nivel histórico.")
        public boolean isCalificacionDefinida() { return (nivelLogro == null) != (notaNumerica == null); }
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
            Instant fecha,
            BigDecimal notaAnterior,
            BigDecimal notaNueva
    ) {
    }

    public record ConsolidadoResponse(
            Long idCompetencia,
            String codigoCompetencia,
            String nombreCompetencia,
            BigDecimal promedioPonderado,
            NivelLogro nivelConsolidado,
            int evaluacionesConsideradas,
            Long idAsignacionCurso,
            Integer numeroVersionRegla,
            List<String> componentesFaltantes,
            Integer numeroVersionEscala,
            String estadoAvance
    ) {
        public ConsolidadoResponse(Long idCompetencia, String codigoCompetencia, String nombreCompetencia,
                BigDecimal promedioPonderado, NivelLogro nivelConsolidado, int evaluacionesConsideradas,
                Long idAsignacionCurso, Integer numeroVersionRegla, List<String> componentesFaltantes, Integer numeroVersionEscala) {
            this(idCompetencia, codigoCompetencia, nombreCompetencia, promedioPonderado, nivelConsolidado,
                    evaluacionesConsideradas, idAsignacionCurso, numeroVersionRegla, componentesFaltantes, numeroVersionEscala, null);
        }

        public ConsolidadoResponse sinPromedioNumerico() {
            return new ConsolidadoResponse(idCompetencia, codigoCompetencia, nombreCompetencia, null, nivelConsolidado,
                    evaluacionesConsideradas, idAsignacionCurso, numeroVersionRegla, componentesFaltantes, numeroVersionEscala, estadoAvance);
        }

        public ConsolidadoResponse(Long idCompetencia, String codigoCompetencia, String nombreCompetencia,
                                   BigDecimal promedioPonderado, NivelLogro nivelConsolidado, int evaluacionesConsideradas) {
            this(idCompetencia, codigoCompetencia, nombreCompetencia, promedioPonderado,
                    nivelConsolidado, evaluacionesConsideradas, null, null, List.of(), null);
        }
    }
}
