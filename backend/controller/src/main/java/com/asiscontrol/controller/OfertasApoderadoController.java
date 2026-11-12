package com.asiscontrol.controller;

import com.asiscontrol.dto.OfertaCobroDto;
import com.asiscontrol.service.OfertasCobroService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apoderado/ofertas")
@PreAuthorize("hasAuthority('PAGOS_LEER') and hasRole('APODERADO')")
public class OfertasApoderadoController {
    private final OfertasCobroService service;

    public OfertasApoderadoController(OfertasCobroService service) { this.service = service; }

    @GetMapping
    public Page<OfertaCobroDto.OfertaResponse> listar(@RequestParam(required = false) Long idAlumno,
            @PageableDefault(size = 10, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listarFamilia(idAlumno, pageable);
    }

    @GetMapping("/{id}")
    public OfertaCobroDto.OfertaResponse obtener(@PathVariable Long id) {
        return service.obtenerFamilia(id);
    }

    @PostMapping("/{id}/aceptar")
    public OfertaCobroDto.OfertaResponse aceptar(@PathVariable Long id,
            @Valid @RequestBody OfertaCobroDto.AceptarRequest request) {
        return service.aceptar(id, request);
    }

    @PostMapping("/{id}/rechazar")
    public OfertaCobroDto.OfertaResponse rechazar(@PathVariable Long id,
            @Valid @RequestBody(required = false) OfertaCobroDto.CambioEstadoRequest request) {
        return service.rechazar(id, request);
    }
}
