package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.EstadoAlerta;
import com.asiscontrol.entity.enums.IndicadorRiesgo;
import com.asiscontrol.entity.enums.NivelRiesgo;
import com.asiscontrol.entity.enums.OperadorComparacion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RiesgoDto {

    private RiesgoDto() {
    }

    public record CriterioRequest(
            @NotBlank @Size(max = 160) String nombre,
            @Size(max = 600) String descripcion,
            @NotNull IndicadorRiesgo indicador,
            @NotNull OperadorComparacion operador,
            @NotNull @DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 8, fraction = 4) BigDecimal umbral,
            @NotNull NivelRiesgo nivelRiesgo,
            @jakarta.validation.constraints.Min(1) Integer minimoDatos
    ) {
        public CriterioRequest(String nombre, String descripcion, IndicadorRiesgo indicador,
                OperadorComparacion operador, BigDecimal umbral, NivelRiesgo nivelRiesgo) {
            this(nombre, descripcion, indicador, operador, umbral, nivelRiesgo, 1);
        }
    }

    public record CriterioResponse(
            Long id,
            String nombre,
            String descripcion,
            IndicadorRiesgo indicador,
            OperadorComparacion operador,
            BigDecimal umbral,
            NivelRiesgo nivelRiesgo,
            Instant fechaCreacion,
            Instant fechaActualizacion,
            int minimoDatos
    ) {
    }

    public record RecalculoRequest(@NotNull Long idAlumno, @NotNull Long idPeriodo) {}

    public enum Resultado { EN_RIESGO, SIN_RIESGO, DATOS_INSUFICIENTES }

    public record ResultadoRegla(Long idCriterio, String nombreCriterio, NivelRiesgo nivel,
            IndicadorRiesgo indicador, Resultado resultado,
            BigDecimal valor, BigDecimal umbral, OperadorComparacion operador,
            int cantidadDatos, int minimoDatos, String motivo, List<String> evidencias, Long idAlerta) {}

    public record EvaluacionResponse(Long idAlumno, Long idPeriodo, Resultado resultado,
            List<ResultadoRegla> reglas) {}

    public record HistorialResponse(Long id, Instant fecha, ResultadoRegla evaluacion) {}

    public record ResumenResponse(Long idPeriodo, Integer grado, Long idSeccion, String seccion,
            IndicadorRiesgo causa, NivelRiesgo nivel, long cantidadAlertas, long cantidadEstudiantes) {}

    public record EstadoAlertaRequest(
            @NotNull EstadoAlerta estado,
            @Size(max = 1000) String observacion
    ) {
    }

    public record AlertaResponse(
            Long id,
            Long idAlumno,
            Long idCriterio,
            String nombreCriterio,
            IndicadorRiesgo indicador,
            BigDecimal valorDetectado,
            BigDecimal umbral,
            NivelRiesgo nivelRiesgo,
            EstadoAlerta estado,
            String mensaje,
            String observacion,
            Instant fechaDeteccion,
            Instant fechaAtencion,
            Instant fechaActualizacion,
            Long idPeriodo,
            String periodo,
            OperadorComparacion operador,
            boolean determinable
    ) {
    }
}
