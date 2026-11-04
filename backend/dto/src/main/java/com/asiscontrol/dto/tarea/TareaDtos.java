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
            @Positive @Max(1000) int puntajeMaximo,
            @Min(0) @Max(20) int maxArchivosEntrega,
            @NotNull @Size(max = 20) Set<Long> archivoIds
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
            @NotNull Long version
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
            long version
    ) {
    }

    public record RegistrarEntregaRequest(
            @NotNull Long alumnoId,
            @NotNull @Size(max = 20) Set<Long> archivoIds,
            @Size(max = 2000) String comentario
    ) {
    }

    public record TareaAlumnoResponse(
            TareaResponse tarea,
            Long alumnoId,
            String curso,
            String docente,
            String seccion,
            EntregaTareaResponse entrega
    ) {
    }

    public record ActualizarEntregaRequest(
            @NotNull @Size(max = 20) Set<Long> archivoIds,
            @Size(max = 2000) String comentario,
            @NotNull Long version
    ) {
    }

    public record CalificarEntregaRequest(
            @NotNull @PositiveOrZero Integer nota,
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
            Integer nota,
            String comentario,
            EstadoTarea estado,
            long version
    ) {
    }
}
