package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.NivelLogro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class EscalaInstitucionalDto {
    private EscalaInstitucionalDto() {}

    public record Rango(@NotNull NivelLogro nivel, @NotNull @Min(0) @Max(20) Integer minimo,
                        @NotNull @Min(0) @Max(20) Integer maximo) {}
    public record Borrador(@NotNull @Size(max = 4) List<@NotNull @Valid Rango> rangos,
                           @PositiveOrZero Long version) {}
    public record Activacion(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 300) String motivo) {}
    public record Escala(Long id, int numeroVersion, String estado, long version, List<Rango> rangos,
                         Instant fechaAplicacion, String actorAplicacion, String motivoAplicacion) {}
}
