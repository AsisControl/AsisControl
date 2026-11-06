package com.asiscontrol.controller;

import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.service.EvaluacionService;
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

@RestController
@RequestMapping("/api/evaluaciones")
@PreAuthorize("hasAuthority('CALIFICACIONES_LEER')")
public class EvaluacionController {

    private final EvaluacionService evaluacionService;
    private final ModuloAccessGuard accessGuard;

    public EvaluacionController(
            EvaluacionService evaluacionService,
            ModuloAccessGuard accessGuard
    ) {
        this.evaluacionService = evaluacionService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public Page<EvaluacionDto.Response> listar(
            @RequestParam(required = false) EstadoEvaluacion estado,
            @RequestParam(required = false) Long idAsignacionCurso,
            @RequestParam(required = false) com.asiscontrol.entity.enums.PeriodoEvaluacion periodo,
            @RequestParam(required = false) Long idCurso,
            @RequestParam(required = false) Long idSeccion,
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "fechaEvaluacion", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return evaluacionService.listar(
                estado,
                idAsignacionCurso,
                periodo,
                idCurso,
                idSeccion,
                busqueda,
                accessGuard.restriccionDocentePersonaId(),
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    /** US-18: alumnos de la seccion y sus niveles; nunca para alumnos ni apoderados. */
    @GetMapping("/{id}/planilla")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and @moduloAccessGuard.puedeGestionarEvaluacion(#id)")
    public EvaluacionDto.PlanillaResponse planilla(@PathVariable Long id) {
        return evaluacionService.planilla(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederEvaluacion(#id)")
    public EvaluacionDto.Response obtener(@PathVariable Long id) {
        return evaluacionService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public EvaluacionDto.Response crear(@Valid @RequestBody EvaluacionDto.Request request) {
        return evaluacionService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public EvaluacionDto.Response actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EvaluacionDto.Request request
    ) {
        return evaluacionService.actualizar(id, request);
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public EvaluacionDto.Response cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody EvaluacionDto.EstadoRequest request
    ) {
        return evaluacionService.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        evaluacionService.eliminar(id);
    }
}
