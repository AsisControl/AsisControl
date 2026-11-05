// asistencia alumnos
package com.asiscontrol.service.asistencia;

import java.util.Set;

/**
 * Cambió el horario o la asignación de estas asignaciones de curso. Lo publica Planificación
 * y Asistencia lo escucha para sincronizar las sesiones, sin que Planificación dependa de ella.
 */
public record HorarioAsignacionCambiadoEvent(Set<Long> asignacionCursoIds) {
}
