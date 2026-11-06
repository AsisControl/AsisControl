package com.asiscontrol.controller;

import com.asiscontrol.dto.tarea.TareaDtos.TareaAlumnoResponse;
import com.asiscontrol.service.TareasAlumnoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alumnos/me/tareas")
@PreAuthorize("hasRole('ALUMNO') and hasAuthority('TAREAS_LEER')")
public class TareasAlumnoController {
    private final TareasAlumnoService service;

    public TareasAlumnoController(TareasAlumnoService service) { this.service = service; }

    @GetMapping
    public Page<TareaAlumnoResponse> listar(@RequestParam(defaultValue = "") String busqueda,
            @RequestParam(required = false) Boolean entregada,
            @PageableDefault(size = 20, sort = "fechaLimite") Pageable pageable) {
        return service.listar(busqueda, entregada, pageable);
    }
}
