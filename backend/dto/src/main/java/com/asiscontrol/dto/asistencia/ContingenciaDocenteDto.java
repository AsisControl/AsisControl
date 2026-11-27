package com.asiscontrol.dto.asistencia;

import com.asiscontrol.entity.asistencia.SolicitudContingenciaDocente.Estado;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ContingenciaDocenteDto {
    private ContingenciaDocenteDto() {}

    public record Solicitar(@NotNull LocalDate fechaJornada, @NotNull LocalTime horaReportada,
            @NotBlank @Size(max = 1000) String motivo) {}
    public enum Decision { APROBAR, RECHAZAR }

    public record Resolucion(
            @NotNull Decision decision,
            @Size(max = 1000) String motivo,
            EstadoAsistencia estadoSinVentana
    ) {
        @AssertTrue(message = "Indica el motivo del rechazo.")
        public boolean isMotivoValido() {
            return decision != Decision.RECHAZAR || (motivo != null && !motivo.isBlank());
        }
    }
    public record Response(Long id, Long docenteId, String docente, LocalDate fechaJornada,
            LocalTime horaReportada, String motivo, Estado estado, Instant fechaEnvio,
            Instant fechaResolucion, String motivoRechazo, EstadoAsistencia estadoSugerido,
            EstadoAsistencia estadoRegistrado) {}
}
