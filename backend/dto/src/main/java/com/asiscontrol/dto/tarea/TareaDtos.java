package com.asiscontrol.dto.tarea;

import com.asiscontrol.dto.archivo.ArchivoDtos.ArchivoResponse;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoTarea;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class TareaDtos {

    private TareaDtos() {
    }

    public record CrearTareaRequest(
            @NotNull Long asignacionCursoId,
            @NotBlank @Size(max = 150) String nombre,
            @NotBlank @Size(max = 3000) String descripcion,
            LocalDateTime fechaPublicacion,
            @NotNull LocalDateTime fechaLimite,
            @Positive @Max(1000) Integer puntajeMaximo,
            @Min(0) @Max(20) int maxArchivosEntrega,
            @NotNull @Size(max = 20) Set<Long> archivoIds,
            Boolean cuentaParaResultadoAcademico,
            @Positive Long idEvaluacionAcademica,
            @jakarta.validation.Valid CompetenciasSinPeso competenciasSinPeso,
            @jakarta.validation.Valid EvaluacionParaTarea nuevaEvaluacion
    ) {
    }

    public record ActualizarTareaRequest(
            @NotBlank @Size(max = 150) String nombre,
            @NotBlank @Size(max = 3000) String descripcion,
            LocalDateTime fechaPublicacion,
            @NotNull LocalDateTime fechaLimite,
            @Positive @Max(1000) int puntajeMaximo,
            @Min(0) @Max(20) int maxArchivosEntrega,
            @NotNull @Size(max = 20) Set<Long> archivoIds,
            @NotNull Long version,
            /** US-22: obligatorio si la tarea ya tiene entregas y cambia la fecha límite. */
            @Size(max = 500) String motivo,
            Boolean cuentaParaResultadoAcademico,
            @Positive Long idEvaluacionAcademica,
            @jakarta.validation.Valid CompetenciasSinPeso competenciasSinPeso,
            @jakarta.validation.Valid EvaluacionParaTarea nuevaEvaluacion
    ) {
    }

    /** US-22: el motivo es obligatorio si la tarea ya tiene entregas. */
    public record EliminarTareaRequest(@Size(max = 500) String motivo) {
    }

    public record ConfirmarCeros(@NotNull @Positive Integer estudiantesEsperados) {}
    public record CerosFaltantes(int estudiantes, int calificaciones) {}
    public record SinEntregaAlumno(Long id, String nombre, String codigo, List<Long> competencias) {}
    public record SinEntregaCompetencia(Long id, String nombre) {}
    public record SinEntregaDisponible(List<SinEntregaAlumno> alumnos, List<SinEntregaCompetencia> competencias, int maximo) {}
    public record SinEntregaRequest(
            @NotNull @Size(min = 1) Set<@NotNull @Positive Long> alumnos,
            @NotNull @Size(min = 1) Set<@NotNull @Positive Long> competencias,
            @NotNull @PositiveOrZero @Max(20) @jakarta.validation.constraints.Digits(integer = 2, fraction = 10) BigDecimal nota) {}
    public record EvaluacionParaTarea(
            @NotNull com.asiscontrol.entity.enums.TipoEvaluacion tipoEvaluacion,
            @NotNull LocalDate fechaEvaluacion,
            @jakarta.validation.constraints.NotEmpty Set<@NotNull @Positive Long> idsCompetencia) {}
    public record CompetenciasSinPeso(@NotNull LocalDate fechaEvaluacion,
                                      @jakarta.validation.constraints.NotEmpty Set<@NotNull @Positive Long> idsCompetencia) {}

    /**
     * US-22: padrón de la sección con el estado de entrega de cada alumno.
     * Los alumnos sin entrega figuran con estado PENDIENTE y entregaId nulo.
     */
    public record SeguimientoTareaResponse(
            Long tareaId,
            int totalAlumnos,
            int entregados,
            int calificados,
            int pendientes,
            List<SeguimientoAlumno> alumnos
    ) {
    }

    public record SeguimientoAlumno(
            Long alumnoId,
            String codigoAlumno,
            String nombreCompleto,
            /** Falso si entregó pero ya no tiene matrícula activa en la sección (no suma al total). */
            boolean matriculaActiva,
            Long entregaId,
            EstadoTarea estado,
            LocalDateTime fechaHoraEntrega,
            java.math.BigDecimal nota
    ) {
    }

    public record TareaResponse(
            Long id,
            Long asignacionCursoId,
            String nombre,
            String descripcion,
            LocalDateTime fechaPublicacion,
            LocalDateTime fechaLimite,
            int puntajeMaximo,
            int maxArchivosEntrega,
            EstadoPublicacionTarea estado,
            List<ArchivoResponse> archivos,
            long version,
            boolean puntajeMaximoFijo,
            EvaluacionAcademica evaluacionAcademica
    ) {
    }

    public record EvaluacionAcademica(Long id, String titulo, com.asiscontrol.entity.enums.EstadoEvaluacion estado,
                                      boolean cuentaParaResultadoAcademico, String componente, BigDecimal pesoComponente) {}

    public record ResultadoCompetencia(String competencia, com.asiscontrol.entity.enums.NivelLogro nivel,
                                       BigDecimal pesoEfectivo) {}

    public record RegistrarEntregaRequest(
            @NotNull Long alumnoId,
            @NotNull @Size(max = 20) Set<Long> archivoIds,
            @Size(max = 2000) String comentario,
            @Size(max = 2000) String respuesta
    ) {
    }

    public record TareaAlumnoResponse(
            TareaResponse tarea,
            Long alumnoId,
            String curso,
            String docente,
            String seccion,
            EntregaTareaResponse entrega,
            List<ResultadoCompetencia> resultados
    ) {
    }

    public record ActualizarEntregaRequest(
            @NotNull @Size(max = 20) Set<Long> archivoIds,
            @Size(max = 2000) String comentario,
            @Size(max = 2000) String respuesta,
            @NotNull Long version
    ) {
    }

    public record CalificarEntregaRequest(
            @NotNull @PositiveOrZero @jakarta.validation.constraints.Digits(integer = 4, fraction = 10) java.math.BigDecimal nota,
            @Size(max = 2000) String comentario,
            @NotNull Long version
    ) {
    }

    public record EntregaTareaResponse(
            Long id,
            Long tareaId,
            Long alumnoId,
            LocalDateTime fechaHoraEntrega,
            List<ArchivoResponse> archivos,
            java.math.BigDecimal nota,
            String comentario,
            String respuesta,
            EstadoTarea estado,
            long version
    ) {
        public EntregaTareaResponse sinNotaNumerica() {
            return new EntregaTareaResponse(id, tareaId, alumnoId, fechaHoraEntrega, archivos, null, comentario, respuesta, estado, version);
        }
    }
}
