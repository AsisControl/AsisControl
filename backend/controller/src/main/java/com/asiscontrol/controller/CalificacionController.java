package com.asiscontrol.controller;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.service.CalificacionService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calificaciones")
@PreAuthorize("hasAuthority('CALIFICACIONES_LEER')")
public class CalificacionController {

    private final CalificacionService calificacionService;
    private final ModuloAccessGuard accessGuard;

    public CalificacionController(
            CalificacionService calificacionService,
            ModuloAccessGuard accessGuard
    ) {
        this.calificacionService = calificacionService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DIRECTOR','DOCENTE')")
    public Page<CalificacionDto.Response> listar(
            @RequestParam(required = false) Long idEvaluacion,
            @RequestParam(required = false) Long idAlumno,
            @RequestParam(required = false) Long idCompetencia,
            @PageableDefault(size = 20, sort = "fechaRegistro", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return calificacionService.listar(
                idEvaluacion,
                idAlumno,
                idCompetencia,
                accessGuard.restriccionDocentePersonaId(),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederCalificacion(#id)")
    public CalificacionDto.Response obtener(@PathVariable Long id) {
        return calificacionService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public CalificacionDto.Response registrar(@Valid @RequestBody CalificacionDto.Request request) {
        return calificacionService.registrar(request);
    }

    @PutMapping("/lote")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public List<CalificacionDto.Response> registrarLote(
            @Valid @RequestBody CalificacionDto.CargaLoteRequest request
    ) {
        return calificacionService.registrarLote(request);
    }

    /** US-50: corrige niveles publicados con un motivo comun. */
    @PostMapping("/correcciones")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public java.util.List<CalificacionDto.Response> corregir(
            @Valid @RequestBody CalificacionDto.CorreccionRequest request) {
        return calificacionService.corregir(request);
    }

    /** US-50: trazabilidad de una calificacion; alumnos y apoderados no la consultan. */
    @GetMapping("/{id}/correcciones")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DIRECTOR','DOCENTE')"
            + " and @moduloAccessGuard.puedeAccederCalificacion(#id)")
    public java.util.List<CalificacionDto.CorreccionResponse> correcciones(@PathVariable Long id) {
        return calificacionService.correcciones(id);
    }

    @GetMapping("/consolidados")
    @PreAuthorize("@moduloAccessGuard.puedeAccederAlumno(#idAlumno)")
    public List<CalificacionDto.ConsolidadoResponse> obtenerConsolidado(
            @RequestParam Long idAlumno,
            @RequestParam(required = false) Long idAsignacionCurso
    ) {
        return calificacionService.obtenerConsolidado(idAlumno, idAsignacionCurso);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        calificacionService.eliminar(id);
    }
}
