package com.asiscontrol.dto.academico;

import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.TipoAula;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public final class AcademicoDtos {

    private AcademicoDtos() {}

    public record CrearCursoRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 500) String descripcion,
            @Min(1) @Max(40) int horasSemanales) {}

    public record ActualizarCursoRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 500) String descripcion,
            @Min(1) @Max(40) int horasSemanales,
            @NotNull Long version) {}

    public record CursoResponse(
            Long id,
            String codigo,
            String nombre,
            String descripcion,
            int horasSemanales,
            boolean activo,
            long version) {}

    public record CrearCompetenciaRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 180) String nombre,
            @Size(max = 700) String descripcion) {}

    public record ActualizarCompetenciaRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 180) String nombre,
            @Size(max = 700) String descripcion,
            @NotNull Long version) {}

    public record CompetenciaResponse(
            Long id,
            String codigo,
            String nombre,
            String descripcion,
            boolean activo,
            long version) {}

    public record CrearCursoCompetenciaRequest(
            @NotNull Long cursoId, @NotNull Long competenciaId, @Positive int orden) {}

    public record ActualizarCursoCompetenciaRequest(
            @NotNull Long cursoId,
            @NotNull Long competenciaId,
            @Positive int orden,
            @NotNull Long version) {}

    public record CursoCompetenciaResponse(
            Long id,
            Long cursoId,
            String cursoCodigo,
            String cursoNombre,
            Long competenciaId,
            String competenciaCodigo,
            String competenciaNombre,
            int orden,
            boolean activo,
            long version) {}

    public record CrearSeccionRequest(
            @NotBlank @Size(max = 20) String nombre,
            @Min(1) @Max(5) int grado,
            @Min(2000) @Max(2100) int anioAcademico,
            @Min(1) @Max(100) int capacidadMaxima,
            Long docenteTutorId,
            @Size(max = 700) String motivo) {}

    public record ActualizarSeccionRequest(
            @NotBlank @Size(max = 20) String nombre,
            @Min(1) @Max(5) int grado,
            @Min(2000) @Max(2100) int anioAcademico,
            @Min(1) @Max(100) int capacidadMaxima,
            Long docenteTutorId,
            @NotNull Long version,
            @NotBlank @Size(max = 700) String motivo) {}

    public record SeccionResponse(
            Long id,
            String nombre,
            int grado,
            int anioAcademico,
            int capacidadMaxima,
            Long docenteTutorId,
            String docenteTutor,
            boolean activo,
            long version) {}

    public record GenerarPropuestasSeccionesRequest(
            @Min(2000) @Max(2100) int anioAcademico,
            @Min(1) @Max(5) int grado,
            @Min(1) int cantidad,
            @Min(1) @Max(100) int capacidadMaxima) {}

    public record SeccionPropuestaResponse(
            String nombre, int grado, int anioAcademico, int capacidadMaxima) {}

    public record ConfirmarPropuestasSeccionesRequest(
            @Min(2000) @Max(2100) int anioAcademico,
            @Min(1) @Max(5) int grado,
            @NotEmpty @Valid List<SeccionPropuestaRequest> secciones,
            @Size(max = 700) String motivo) {}

    public record SeccionPropuestaRequest(
            @NotBlank @Size(max = 20) String nombre,
            @Min(1) @Max(100) int capacidadMaxima) {}

    public record CrearAulaRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 100) String nombre,
            @NotNull TipoAula tipoAula,
            @Min(1) @Max(300) int capacidad,
            @NotBlank @Size(max = 180) String ubicacion) {}

    public record ActualizarAulaRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 100) String nombre,
            @NotNull TipoAula tipoAula,
            @Min(1) @Max(300) int capacidad,
            @NotBlank @Size(max = 180) String ubicacion,
            @NotNull Long version) {}

    public record AulaResponse(
            Long id,
            String codigo,
            String nombre,
            TipoAula tipoAula,
            int capacidad,
            String ubicacion,
            boolean activo,
            long version) {}

    public record CrearAsignacionCursoRequest(
            @NotNull Long cursoId, @NotNull Long seccionId, @NotNull Long docenteId) {}

    public record ActualizarAsignacionCursoRequest(
            @NotNull Long cursoId,
            @NotNull Long seccionId,
            @NotNull Long docenteId,
            @NotNull Long version) {}

    public record AsignacionCursoResponse(
            Long id,
            Long cursoId,
            String curso,
            Long seccionId,
            String seccion,
            int anioAcademico,
            Long docenteId,
            String docente,
            boolean activo,
            long version) {}

    public record CrearHorarioRequest(
            @NotNull Long asignacionCursoId,
            @NotNull Long aulaId,
            @NotNull DiaSemana diaSemana,
            @NotNull LocalTime horaInicio,
            @NotNull LocalTime horaFin) {}

    public record ActualizarHorarioRequest(
            @NotNull Long asignacionCursoId,
            @NotNull Long aulaId,
            @NotNull DiaSemana diaSemana,
            @NotNull LocalTime horaInicio,
            @NotNull LocalTime horaFin,
            @NotNull Long version) {}

    public record HorarioResponse(
            Long id,
            Long asignacionCursoId,
            Long cursoId,
            String curso,
            Long seccionId,
            String seccion,
            Long docenteId,
            String docente,
            Long aulaId,
            String aula,
            DiaSemana diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            boolean activo,
            long version) {}
}
