package com.asiscontrol.dto.auditoria;

import com.asiscontrol.dto.academico.HistorialHorarioDtos.CampoHistorial;
import com.asiscontrol.entity.enums.EstadoError;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.SeveridadError;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuditoriaDtos {

    private AuditoriaDtos() {}

    public record AuditResponse(
            Long id,
            String actorIdentificador,
            /** Nombre de la persona del acceso; null para "SISTEMA" o accesos ya inactivos. */
            String actorNombre,
            String rolActor,
            String accion,
            String modulo,
            String entidad,
            String registroId,
            Instant fechaHora,
            ResultadoAuditoria resultado,
            String motivo,
            String correlationId,
            String direccionIp,
            String valorAnterior,
            String nuevoValor) {}

    public record ErrorResponse(
            Long id,
            Instant fechaHora,
            SeveridadError severidad,
            String componente,
            String mensajeSeguro,
            String correlationId,
            EstadoError estado,
            String observacionSeguimiento,
            long version) {}

    public record SeguimientoResponse(
            Long id,
            String actor,
            Instant fecha,
            EstadoError anterior,
            EstadoError nuevo,
            String observacion) {}

    public record HistorialResponse(
            Long id,
            String actor,
            /** Nombre de la persona del acceso; null para "SISTEMA" o accesos ya inactivos. */
            String actorNombre,
            String rol,
            Instant fecha,
            String accion,
            String motivo,
            String anterior,
            String nuevo,
            /** Valores legibles por campo: sin identificadores internos. */
            List<CampoHistorial> campos) {}

    public record UpdateErrorRequest(
            @NotNull EstadoError estado,
            @jakarta.validation.constraints.NotBlank @Size(max = 1000)
                    String observacionSeguimiento,
            @NotNull Long version) {}
}
