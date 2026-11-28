package com.asiscontrol.dto.academico;

import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public final class PlanEstudiosDtos {
    private PlanEstudiosDtos() {}

    public record GuardarPlan(@NotNull Long version, List<@NotNull @Positive Long> cursosIds,
                              @Size(max = 500) String motivo, List<@NotNull @Valid CursoEnPlanRequest> cursos) {}
    public record CursoEnPlanRequest(@NotNull @Positive Long id, @NotBlank @Size(max = 120) String nombre,
                                    @NotNull @Min(1) @Max(40) Integer horasSemanales,
                                    List<@NotNull @Positive Long> competenciasIds) {}
    public record RenombrarPlan(@NotNull Long version, @NotBlank @Size(max = 100) String nombre) {}
    public record IdentidadPlan(String nombre, long version) {}

    public record CompetenciaPlan(Long id, String codigo, String nombre, boolean activa) {}
    public record CursoPlan(Long id, String codigo, String nombre, int horasSemanales,
                            List<CompetenciaPlan> competencias) {}
    public record PlanAnterior(int anioOrigen, List<CursoPlan> cursos) {}

    /** OBS-01: secciones cuyo horario supera las horas del plan; se informan sin bloquear el guardado. */
    public record PlanEstudios(int anio, int grado, EstadoAnioAcademico estadoAnio, boolean editable,
                               boolean inicializacion, boolean definido, long version, List<CursoPlan> cursos,
                               List<SeccionExcedida> seccionesExcedidas) {}

    /** OBS-01: un curso de una sección con más minutos programados que las horas del plan (1 h = 60 min). */
    public record SeccionExcedida(Long seccionId, String seccion, Long cursoId, String curso,
                                  int horasPlan, long minutosProgramados) {}

    public record PlanAnual(int anio, EstadoAnioAcademico estado, LocalDate inicio, LocalDate fin,
                           List<GradoDelPlan> grados, String nombre) {
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
