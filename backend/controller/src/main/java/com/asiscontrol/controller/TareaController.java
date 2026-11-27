package com.asiscontrol.controller;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.dto.tarea.TareaDtos.ActualizarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CrearTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.EliminarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.TareaResponse;
import com.asiscontrol.dto.tarea.TareaDtos.CerosFaltantes;
import com.asiscontrol.dto.tarea.TareaDtos.ConfirmarCeros;
import com.asiscontrol.dto.tarea.TareaDtos.RevisionSinEntrega;
import com.asiscontrol.dto.tarea.TareaDtos.NivelSinEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.DevolucionSinEntregaRequest;
import com.asiscontrol.service.TareaService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    @PreAuthorize("hasAuthority('TAREAS_LEER') and !hasRole('APODERADO') and @moduloAccessGuard.puedeAccederTarea(#id)")
    public TareaResponse obtener(@PathVariable Long id) {
        return tareaService.obtener(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TAREAS_LEER') and !hasRole('APODERADO')")
    public Page<TareaResponse> listar(
            @RequestParam(required = false) Long asignacionCursoId,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(required = false) EstadoPublicacionTarea estado,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String orden,
            @PageableDefault(size = 20, sort = "fechaCreacion") Pageable pageable) {
        var sort = switch (orden == null ? "" : orden) {
            case "antiguas" -> Sort.by(Sort.Order.asc("fechaCreacion"), Sort.Order.asc("id"));
            case "titulo" -> Sort.by(Sort.Order.asc("nombre").ignoreCase(), Sort.Order.asc("id"));
            case "titulo-desc" -> Sort.by(Sort.Order.desc("nombre").ignoreCase(), Sort.Order.asc("id"));
            case "recientes" -> Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("id"));
            default -> pageable.getSort().and(Sort.by("id"));
        };
        return tareaService.listar(
                asignacionCursoId,
                cursoId,
                estado,
                search,
                accessGuard.restriccionDocentePersonaId(),
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
    }

    @GetMapping("/{id}/calificaciones-sin-entrega")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public com.asiscontrol.dto.tarea.TareaDtos.SinEntregaDisponible sinEntrega(@PathVariable Long id) {
        return tareaService.sinEntrega(id);
    }

    @PutMapping("/{id}/calificaciones")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public List<CalificacionDto.Response> calificarEntregas(
            @PathVariable Long id, @Valid @RequestBody CalificacionDto.CargaLoteRequest request) {
        return tareaService.calificarEntregas(id, request);
    }

    @PostMapping("/{id}/correcciones")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public List<CalificacionDto.Response> corregirResultados(
            @PathVariable Long id, @Valid @RequestBody CalificacionDto.CorreccionRequest request) {
        return tareaService.corregirResultados(id, request);
    }

    @DeleteMapping("/{id}/calificaciones/{calificacion}")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public ResponseEntity<Void> quitarCalificacion(
            @PathVariable Long id, @PathVariable Long calificacion, @RequestParam Long version) {
        tareaService.quitarCalificacion(id, calificacion, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publicar-calificaciones")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public EvaluacionDto.Response publicarResultados(@PathVariable Long id) {
        return tareaService.publicarResultados(id);
    }

    @PostMapping("/{id}/calificaciones-sin-entrega")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public CerosFaltantes calificarSinEntrega(@PathVariable Long id,
            @Valid @RequestBody com.asiscontrol.dto.tarea.TareaDtos.SinEntregaRequest request) {
        return tareaService.calificarSinEntrega(id, request);
    }

    @GetMapping("/{id}/calificaciones-sin-entrega/{alumno}")
    @PreAuthorize("hasAuthority('TAREAS_LEER') and !hasAnyRole('ALUMNO','APODERADO') and @moduloAccessGuard.puedeAccederTarea(#id)")
    public RevisionSinEntrega revisionSinEntrega(@PathVariable Long id, @PathVariable Long alumno) {
        return tareaService.revisionSinEntrega(id, alumno);
    }

    @PutMapping("/{id}/calificaciones-sin-entrega/{alumno}")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public ResponseEntity<Void> guardarNivelSinEntrega(@PathVariable Long id, @PathVariable Long alumno,
            @Valid @RequestBody NivelSinEntregaRequest request) {
        tareaService.guardarNivelSinEntrega(id, alumno, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/calificaciones-sin-entrega/{alumno}/devolucion")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public ResponseEntity<Void> guardarDevolucionSinEntrega(@PathVariable Long id, @PathVariable Long alumno,
            @Valid @RequestBody DevolucionSinEntregaRequest request) {
        tareaService.guardarDevolucionSinEntrega(id, alumno, request.comentario());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/ceros-faltantes")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public CerosFaltantes previsualizarCeros(@PathVariable Long id) {
        return tareaService.previsualizarCeros(id);
    }

    @PostMapping("/{id}/ceros-faltantes")
    @PreAuthorize("hasAuthority('TAREAS_ESCRIBIR') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public CerosFaltantes confirmarCeros(@PathVariable Long id, @Valid @RequestBody ConfirmarCeros request) {
        return tareaService.confirmarCeros(id, request.estudiantesEsperados());
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
