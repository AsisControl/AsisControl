package com.asiscontrol.dto.academico;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** US-27: cambios de bloques de horario y asignaciones docentes, leidos de la auditoria. */
public final class HistorialHorarioDtos {

    private HistorialHorarioDtos() {}

    /** Filtros opcionales; se combinan con AND. */
    public record FiltroHistorialHorario(
            Integer anioAcademico,
            Long seccionId,
            Long docenteId,
            Long aulaId,
            LocalDate desde,
            LocalDate hasta) {}

    /** Valor de un campo antes y despues del cambio; null si no existia en ese momento. */
    public record CampoHistorial(String campo, String anterior, String nuevo) {}

    public record HistorialHorarioResponse(
            Long id,
            Instant fecha,
            String actorIdentificador,
            /** Nombre de la persona del acceso; null para "SISTEMA" o accesos ya inactivos. */
            String actorNombre,
            String rolActor,
            String accion,
            String entidad,
            String registroId,
            String motivo,
            List<CampoHistorial> campos) {}
}
