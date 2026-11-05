// asistencia alumnos
package com.asiscontrol.controller.asistencia;

import com.asiscontrol.dto.asistencia.SesionClaseDtos.SesionClaseResponse;
import com.asiscontrol.service.asistencia.CalendarioSesionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/asistencias")
@PreAuthorize("hasAuthority('ASISTENCIA_LEER')")
public class SesionClaseController {

    private final CalendarioSesionService calendarioService;

    public SesionClaseController(CalendarioSesionService calendarioService) {
        this.calendarioService = calendarioService;
    }

    /** Sesiones de clase de la asignación con su estado (CA-01). Solo para el docente asignado. */
    @GetMapping("/asignaciones/{asignacionCursoId}/sesiones")
    public List<SesionClaseResponse> listarSesiones(@PathVariable Long asignacionCursoId) {
        return calendarioService.listar(asignacionCursoId);
    }
}
