package com.asiscontrol.dto.dashboard;

import com.asiscontrol.entity.asistencia.EstadoSesionClase;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Inicio del docente: clases de hoy, indicadores y pendientes académicos, en un solo pedido. */
public final class DashboardDocenteDtos {

    private DashboardDocenteDtos() {
    }

    /** Dónde está la clase respecto de la hora actual del colegio. */
    public enum MomentoClase {
        PROXIMA,
        EN_CURSO,
        FINALIZADA
    }

    public enum TipoPendiente {
        EVALUACION,
        TAREA,
        JUSTIFICACION
    }

    public enum Prioridad {
        ALTA,
        MEDIA
    }

    public record ClaseHoyResponse(
            Long sesionId,
            Long asignacionCursoId,
            String curso,
            String seccion,
            String aula,
            LocalTime horaInicio,
            LocalTime horaFin,
            EstadoSesionClase estado,
            MomentoClase momento,
            boolean puedeTomar
    ) {
    }

    /**
     * Algo que el docente debe atender. {@code id} es la tarea o la evaluación (null para las
     * justificaciones, que se agrupan en un solo pendiente) y {@code cantidad} cuántos elementos
     * resume: entregas, niveles por registrar o justificaciones.
     */
    public record PendienteResponse(
            TipoPendiente tipo,
            Long id,
            Long asignacionCursoId,
            String titulo,
            String detalle,
            long cantidad,
            Prioridad prioridad
    ) {
    }

    /** {@code anioAcademico} y {@code periodo} son null si el año de la fecha no está ACTIVO. */
    public record DashboardDocenteResponse(
            LocalDate fecha,
            Integer anioAcademico,
            String periodo,
            List<ClaseHoyResponse> clasesHoy,
            long asistenciasPendientes,
            long porRevisar,
            long bloquesSemanales,
            List<PendienteResponse> pendientes
    ) {
    }
}
