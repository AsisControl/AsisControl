package com.asiscontrol.dto.academico;

import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class PlanEstudiosDtos {
    private PlanEstudiosDtos() {}

    public record GuardarPlan(@NotNull Long version, @NotNull List<@NotNull @Positive Long> cursosIds) {}

    public record CursoPlan(Long id, String codigo, String nombre) {}

    public record PlanEstudios(int anio, int grado, EstadoAnioAcademico estadoAnio, boolean editable,
                              boolean inicializacion, long version, List<CursoPlan> cursos) {}
}
