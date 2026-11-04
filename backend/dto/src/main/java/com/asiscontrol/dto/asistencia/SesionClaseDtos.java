// asistencia alumnos
package com.asiscontrol.dto.asistencia;

import com.asiscontrol.entity.asistencia.EstadoSesionClase;

import java.time.LocalDate;
import java.time.LocalTime;

public final class SesionClaseDtos {

    private SesionClaseDtos() {
    }

    /**
     * Sesión de clase con su estado efectivo: un BORRADOR de un día anterior se informa como
     * PENDIENTE. Los indicadores dicen qué puede hacer el docente según la fecha del servidor.
     */
    public record SesionClaseResponse(
            Long id,
            Long asignacionCursoId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            EstadoSesionClase estado,
            boolean puedeTomar,
            boolean puedeVer,
            boolean puedeEditar,
            long version
    ) {
    }
}
