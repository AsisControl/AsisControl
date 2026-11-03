package com.asiscontrol.controller;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaAlumnoResponse;
import com.asiscontrol.service.AsistenciaAlumnoService;
import com.asiscontrol.service.ModuloAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Consulta de asistencias de alumnos. El registro, la finalización y la corrección
 * se hacen por sesión de clase (HU14), no por fila de alumno.
 */
@RestController
@RequestMapping("/api/asistencias/alumnos")
@PreAuthorize("hasAuthority('ASISTENCIA_LEER')")
public class AsistenciaAlumnoController {

    private final AsistenciaAlumnoService asistenciaService;
    private final ModuloAccessGuard accessGuard;

    public AsistenciaAlumnoController(
            AsistenciaAlumnoService asistenciaService,
            ModuloAccessGuard accessGuard
    ) {
        this.asistenciaService = asistenciaService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederAsistenciaAlumno(#id)")
    public AsistenciaAlumnoResponse obtener(@PathVariable Long id) {
        return asistenciaService.obtener(id);
    }

    @GetMapping
    public Page<AsistenciaAlumnoResponse> listar(
            @RequestParam(required = false) Long alumnoId,
            @RequestParam(required = false) Long asignacionCursoId,
            @RequestParam(required = false) LocalDate fechaRegistro,
            @PageableDefault(size = 20, sort = "fechaRegistro") Pageable pageable) {
        return asistenciaService.listar(
                alumnoId,
                asignacionCursoId,
                fechaRegistro,
                accessGuard.restriccionDocentePersonaId(),
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }
}
