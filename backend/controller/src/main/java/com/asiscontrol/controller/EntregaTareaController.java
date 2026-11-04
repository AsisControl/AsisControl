package com.asiscontrol.controller;

import com.asiscontrol.dto.tarea.TareaDtos.ActualizarEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CalificarEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.EntregaTareaResponse;
import com.asiscontrol.dto.tarea.TareaDtos.RegistrarEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.SeguimientoTareaResponse;
import com.asiscontrol.service.EntregaTareaService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('TAREAS_LEER')")
public class EntregaTareaController {

    private final EntregaTareaService entregaService;
    private final ModuloAccessGuard accessGuard;

    public EntregaTareaController(
            EntregaTareaService entregaService,
            ModuloAccessGuard accessGuard
    ) {
        this.entregaService = entregaService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/tareas/{tareaId}/entregas")
    @PreAuthorize("hasAuthority('TAREAS_ENTREGAR')")
    public ResponseEntity<EntregaTareaResponse> registrar(
            @PathVariable Long tareaId,
            @Valid @RequestBody RegistrarEntregaRequest request) {
        return ResponseEntity.status(201).body(entregaService.registrar(tareaId, request));
    }

    @GetMapping("/tareas/{tareaId}/entregas")
    public Page<EntregaTareaResponse> listarPorTarea(
            @PathVariable Long tareaId,
            @PageableDefault(size = 20, sort = "fechaHoraEntrega") Pageable pageable) {
        return entregaService.listarPorTarea(
                tareaId,
                accessGuard.restriccionDocentePersonaId(),
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    /** US-22: padrón de la sección con quién entregó y quién no ("3 de 30 entregaron"). */
    @GetMapping("/tareas/{tareaId}/seguimiento")
    @PreAuthorize("hasAuthority('TAREAS_LEER') and @moduloAccessGuard.puedeConsultarSeguimientoTarea(#tareaId)")
    public SeguimientoTareaResponse seguimiento(@PathVariable Long tareaId) {
        return entregaService.seguimiento(tareaId);
    }

    @GetMapping("/entregas/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederEntrega(#id)")
    public EntregaTareaResponse obtener(@PathVariable Long id) {
        return entregaService.obtener(id);
    }

    @PutMapping("/entregas/{id}")
    @PreAuthorize("hasAuthority('TAREAS_ENTREGAR')")
    public EntregaTareaResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEntregaRequest request) {
        return entregaService.actualizar(id, request);
    }

    @PostMapping("/entregas/{id}/calificar")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR')")
    public EntregaTareaResponse calificar(
            @PathVariable Long id,
            @Valid @RequestBody CalificarEntregaRequest request) {
        return entregaService.calificar(id, request);
    }
}
