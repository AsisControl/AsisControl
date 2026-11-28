package com.asiscontrol.controller;

import com.asiscontrol.dto.EvidenciaPedagogicaDto.Crear;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Editar;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Estudiante;
import com.asiscontrol.dto.EvidenciaPedagogicaDto.Respuesta;
import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.service.EvidenciaPedagogicaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evidencias-pedagogicas")
@PreAuthorize("hasRole('DOCENTE') and hasAuthority('CALIFICACIONES_LEER')")
public class EvidenciaPedagogicaController {

    private final EvidenciaPedagogicaService servicio;

    public EvidenciaPedagogicaController(EvidenciaPedagogicaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public Respuesta crear(@Valid @RequestBody Crear request) {
        return servicio.crear(request);
    }

    @GetMapping
    public PageResponse<Respuesta> listar(@RequestParam Long asignacionId, @RequestParam Long alumnoId,
                                  @RequestParam(required = false) Long competenciaId,
                                  @RequestParam(required = false) Long periodoId, Pageable pagina) {
        var orden = Sort.by(Sort.Direction.DESC, "fechaCreacion", "id");
        var paginaOrdenada = PageRequest.of(pagina.getPageNumber(), Math.min(pagina.getPageSize(), 100), orden);
        return servicio.listar(asignacionId, alumnoId, competenciaId, periodoId, paginaOrdenada);
    }

    @GetMapping("/{id}")
    public Respuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @GetMapping("/asignaciones/{asignacionId}/estudiantes")
    public List<Estudiante> listarEstudiantes(@PathVariable Long asignacionId) {
        return servicio.listarEstudiantes(asignacionId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCENTE') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public Respuesta editar(@PathVariable Long id, @Valid @RequestBody Editar request) {
        return servicio.editar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DOCENTE') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public void inactivar(@PathVariable Long id) {
        servicio.inactivar(id);
    }

    @PutMapping("/{id}/evaluaciones/{evaluacionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DOCENTE') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public void vincularEvaluacion(@PathVariable Long id, @PathVariable Long evaluacionId) {
        servicio.vincularEvaluacion(id, evaluacionId);
    }
}
