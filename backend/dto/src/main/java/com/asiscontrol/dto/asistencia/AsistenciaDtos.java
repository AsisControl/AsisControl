package com.asiscontrol.dto.asistencia;

import com.asiscontrol.dto.archivo.ArchivoDtos.ArchivoResponse;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public final class AsistenciaDtos {

    private AsistenciaDtos() {
    }

    public record AsistenciaAlumnoResponse(
            Long id,
            Long alumnoId,
            Long asignacionCursoId,
            Long docenteRegistradorId,
            LocalDate fechaRegistro,
            LocalTime horaRegistro,
            EstadoAsistencia estado,
            String comentario,
            long version,
            String curso,
            Long justificacionId,
            EstadoJustificacion estadoJustificacion,
            String motivoJustificacion,
            String observacionRevision
    ) {
    }

    public record EmitirCodigoQRRequest(
            @NotNull Long docenteId,
            @Min(30) @Max(900) Integer vigenciaSegundos
    ) {
    }

    public record CodigoQRResponse(
            Long id,
            Long docenteId,
            String token,
            LocalDateTime fechaHoraEmision,
            LocalDateTime fechaHoraExpiracion,
            EstadoCodigoQR estado
    ) {
    }

    public record EscanearCodigoQRRequest(
            @NotBlank @Size(max = 500) String token
    ) {
    }

    public record RegistrarContingenciaRequest(
            @NotNull Long docenteId,
            @NotNull LocalDate fechaJornada,
            LocalDateTime fechaHoraMarcada,
            @NotNull EstadoAsistencia estado,
            @NotBlank @Size(max = 1000) String motivo
    ) {
    }

    public record AsistenciaDocenteResponse(
            Long id,
            Long docenteId,
            Long codigoQRId,
            LocalDate fechaJornada,
            LocalDateTime fechaHoraMarcada,
            EstadoAsistencia estado,
            boolean contingencia,
            String motivoContingencia,
            long version
    ) {
    }

    public record CrearJustificacionRequest(
            @NotNull Long asistenciaAlumnoId,
            @NotBlank @Size(max = 2000) String motivo,
            @NotNull @Size(max = 10) Set<Long> archivoIds
    ) {
    }

    public record RevisarJustificacionRequest(
            @Size(max = 2000) String observacion,
            @NotNull Long version
    ) {
    }

    public record SubsanarJustificacionRequest(
            @NotBlank @Size(max = 2000) String motivo,
            @NotNull @Size(max = 10) Set<Long> archivoIds,
            @NotNull Long version
    ) {
    }

    public record JustificacionAsistenciaResponse(
            Long id,
            Long asistenciaAlumnoId,
            String motivo,
            LocalDateTime fechaHoraEnvio,
            LocalDateTime fechaHoraRevision,
            Long docenteRevisorId,
            String observacionRevision,
            EstadoJustificacion estado,
            List<ArchivoResponse> archivos,
            long version,
            String estudiante,
            String curso,
            String seccion,
            LocalDate fechaSesion,
            LocalTime horaSesion,
            EstadoAsistencia estadoAsistencia
    ) {
    }
}
