package com.asiscontrol.controller;

import com.asiscontrol.dto.tarea.TareaDtos.ActualizarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CrearTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.EliminarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.TareaResponse;
import com.asiscontrol.service.TareaService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tareas")
@PreAuthorize("hasAuthority('TAREAS_LEER')")
public class TareaController {

    private final TareaService tareaService;
    private final ModuloAccessGuard accessGuard;

    public TareaController(TareaService tareaService, ModuloAccessGuard accessGuard) {
        this.tareaService = tareaService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public ResponseEntity<TareaResponse> crear(@Valid @RequestBody CrearTareaRequest request) {
        return ResponseEntity.status(201).body(tareaService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public TareaResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarTareaRequest request) {
        return tareaService.actualizar(id, request);
    }

    @PostMapping("/{id}/publicar")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public TareaResponse publicar(@PathVariable Long id) {
        return tareaService.publicar(id);
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public TareaResponse cerrar(@PathVariable Long id) {
        return tareaService.cerrar(id);
    }

    @PostMapping("/{id}/archivar")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public TareaResponse archivar(@PathVariable Long id) {
        return tareaService.archivar(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederTarea(#id)")
    public TareaResponse obtener(@PathVariable Long id) {
        return tareaService.obtener(id);
    }

    @GetMapping
    public Page<TareaResponse> listar(
            @RequestParam(required = false) Long asignacionCursoId,
            @PageableDefault(size = 20, sort = "fechaCreacion") Pageable pageable) {
        return tareaService.listar(
                asignacionCursoId,
                accessGuard.restriccionDocentePersonaId(),
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) EliminarTareaRequest request) {
        tareaService.eliminar(id, request == null ? null : request.motivo());
        return ResponseEntity.noContent().build();
    }
}
