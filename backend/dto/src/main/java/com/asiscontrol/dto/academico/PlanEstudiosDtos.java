package com.asiscontrol.dto.academico;

import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public final class PlanEstudiosDtos {
    private PlanEstudiosDtos() {}

    public record GuardarPlan(@NotNull Long version, @NotNull List<@NotNull @Positive Long> cursosIds) {}

    public record CursoPlan(Long id, String codigo, String nombre) {}

    public record PlanEstudios(int anio, int grado, EstadoAnioAcademico estadoAnio, boolean editable,
                              boolean inicializacion, long version, List<CursoPlan> cursos) {}

    public record PlanAnual(int anio, EstadoAnioAcademico estado, LocalDate inicio, LocalDate fin,
                           List<GradoDelPlan> grados) {
        public boolean borrador() {
            return estado == EstadoAnioAcademico.BORRADOR
                    || grados.stream().anyMatch(grado -> !grado.definido() || grado.cursos().isEmpty());
        }
    }

    public record GradoDelPlan(int numero, boolean definido, List<CursoDelPlan> cursos) {
        public long horasSemanales() {
            return cursos.stream().mapToLong(CursoDelPlan::horasSemanales).sum();
        }
    }

    public record CursoDelPlan(String codigo, String nombre, int horasSemanales) {}
}
