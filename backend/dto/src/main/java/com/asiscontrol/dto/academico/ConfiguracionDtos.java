package com.asiscontrol.dto.academico;

import com.asiscontrol.entity.enums.*;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ConfiguracionDtos {
    private ConfiguracionDtos() {}

    public record CrearAnio(
            @Min(2000) @Max(2100) int anio,
            @NotNull LocalDate fechaInicio,
            @NotNull LocalDate fechaFin) {}

    public record EstadoAnio(
            @NotNull EstadoAnioAcademico estado,
            @NotNull Long version,
            @NotBlank @Size(max = 700) String motivo) {}

    public record AdmisionAnio(
            boolean abierta, @NotNull Long version, @NotBlank @Size(max = 700) String motivo) {}

    public record CrearPeriodo(
            @NotBlank @Size(max = 100) String nombre,
            @Min(1) int orden,
            @NotNull LocalDate fechaInicio,
            @NotNull LocalDate fechaFin) {}

    public record CrearDiaNoLectivo(
            @NotNull LocalDate fecha,
            @Size(max = 255) String descripcion) {}

    public record ActualizarDiaNoLectivo(
            @Size(max = 255) String descripcion,
            @NotNull Long version) {}

    public record EstadoPeriodo(
            @NotNull EstadoPeriodoAcademico estado,
            @NotNull Long version,
            @NotBlank @Size(max = 700) String motivo) {}

    public record CrearGrado(@Min(1) @Max(5) int numero, @Min(1) @Max(100) int capacidadDefault) {}

    public record EstadoGrado(
            boolean activo,
            @Min(1) @Max(100) int capacidadDefault,
            @NotNull Long version,
            @NotBlank @Size(max = 700) String motivo) {}

    public record EstadoSeccion(
            boolean activo, @NotNull Long version, @NotBlank @Size(max = 700) String motivo) {}

    public record PeriodoResponse(
            Long id,
            String nombre,
            int orden,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            EstadoPeriodoAcademico estado,
            long version) {}

    public record DiaNoLectivoResponse(
            Long id,
            int anioAcademico,
            LocalDate fecha,
            String descripcion,
            Instant fechaCreacion,
            Instant fechaActualizacion,
            long version) {}

    public record GradoResponse(
            Long id, int numero, int capacidadDefault, boolean activo, long version) {}

    public record VacantesResponse(
            Long id,
            String nombre,
            int grado,
            int anioAcademico,
            int capacidadMaxima,
            long ocupacion,
            long vacantes,
            boolean activo,
            long version) {}

    public record AnioResponse(
            Long id,
            int anio,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            EstadoAnioAcademico estado,
            long version,
            List<PeriodoResponse> periodos,
            List<GradoResponse> grados,
            List<VacantesResponse> secciones,
            boolean admisionAbierta) {}
}
