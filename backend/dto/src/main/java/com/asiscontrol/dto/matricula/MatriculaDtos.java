package com.asiscontrol.dto.matricula;

import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class MatriculaDtos {

    private MatriculaDtos() {}

    public record CrearSolicitudMatriculaRequest(
            @NotNull Long alumnoId,
            Long seccionId,
            @Size(max = 1000) String observaciones,
            @jakarta.validation.constraints.Min(2000) @jakarta.validation.constraints.Max(2100)
                    Integer anioAcademico,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5)
                    Integer grado) {
        public CrearSolicitudMatriculaRequest(Long a, Long s, String o) {
            this(a, s, o, null, null);
        }
    }

    public record ActualizarSolicitudMatriculaRequest(
            Long seccionId,
            EstadoSolicitudMatricula estado,
            @Size(max = 1000) String observaciones,
            @Size(max = 700) String motivoRechazo,
            @NotNull Long version) {}

    public record SolicitudMatriculaResponse(
            Long id,
            Long alumnoId,
            String codigoAlumno,
            String alumno,
            Long seccionId,
            String seccion,
            int grado,
            int anioAcademico,
            EstadoSolicitudMatricula estado,
            Instant fechaEnvio,
            Instant fechaRevision,
            String observaciones,
            String motivoRechazo,
            boolean activo,
            long version) {}

    public record CrearMatriculaRequest(
            @NotNull Long solicitudMatriculaId, @NotNull Long versionSolicitud) {}

    public record ActualizarMatriculaRequest(
            @NotNull EstadoMatricula estado,
            @NotNull Long version,
            @jakarta.validation.constraints.NotBlank @Size(max = 700) String motivo) {}

    public record CambiarSeccionMatriculaRequest(
            @NotNull Long seccionId,
            @NotNull Long version,
            @jakarta.validation.constraints.NotBlank @Size(max = 700) String motivo) {}

    public record MatriculaResponse(
            Long id,
            String codigoMatricula,
            Long solicitudMatriculaId,
            Long alumnoId,
            String codigoAlumno,
            String alumno,
            Long seccionId,
            String seccion,
            int grado,
            int anioAcademico,
            Instant fechaMatricula,
            EstadoMatricula estado,
            boolean activo,
            long version) {}
}
