package com.asiscontrol.dto;

import com.asiscontrol.entity.ReglaCalificacion.Estado;
import com.asiscontrol.entity.ReglaCalificacion.Formula;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class ReglaCalificacionDto {
    private ReglaCalificacionDto() {}

    public record Componente(
            @NotNull TipoEvaluacion tipoEvaluacion,
            @NotBlank @Size(max = 90) String nombre,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal peso) {}

    public record Borrador(
            @NotNull @Positive Long idCurso,
            @NotNull @Positive Long idGrado,
            @NotNull Formula formula,
            @NotEmpty @Size(max = 5) List<@NotNull @Valid Componente> componentes,
            @PositiveOrZero Long version) {}

    public record Activacion(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 300) String motivo) {}

    public record Regla(Long id, Long idCurso, Long idGrado, int numeroVersion, Estado estado,
                        Formula formula, List<Componente> componentes, long version, java.time.Instant fechaCreacion) {}

    public record Aplicada(Long id, int numeroVersion, Formula formula) {}

    public record Muestra(@NotNull TipoEvaluacion tipoEvaluacion, @NotNull NivelLogro nivel) {}

    public record VistaPrevia(@NotNull Formula formula,
                             @NotEmpty @Size(max = 5) List<@NotNull @Valid Componente> componentes,
                             @NotEmpty @Size(max = 50) List<@NotNull @Valid Muestra> muestra) {}

    public record Resultado(BigDecimal promedioPonderado, NivelLogro nivelConsolidado,
                            int evaluacionesConsideradas, List<String> componentesFaltantes,
                            List<ComponenteSimulado> componentes) {
        public Resultado(BigDecimal promedioPonderado, NivelLogro nivelConsolidado,
                         int evaluacionesConsideradas, List<String> componentesFaltantes) {
            this(promedioPonderado, nivelConsolidado, evaluacionesConsideradas, componentesFaltantes, List.of());
        }
    }

    public record ComponenteSimulado(TipoEvaluacion tipo, int evaluaciones, BigDecimal promedio, BigDecimal pesoEfectivo) {}

    public record MuestraNumerica(@NotNull TipoEvaluacion tipoEvaluacion,
                                  @DecimalMin("0") @DecimalMax("20") BigDecimal nota) {}

    public record VistaPreviaNumerica(@NotNull Formula formula,
            @NotEmpty @Size(max = 5) List<@NotNull @Valid Componente> componentes,
            @NotNull @Size(max = 50) List<@NotNull @Valid MuestraNumerica> muestra) {}

    public record ResultadoNumerico(BigDecimal promedioPonderado, Integer notaRedondeada, NivelLogro nivelConsolidado,
                                    int numeroVersionEscala, int evaluacionesConsideradas,
                                    List<String> componentesFaltantes) {}
}
