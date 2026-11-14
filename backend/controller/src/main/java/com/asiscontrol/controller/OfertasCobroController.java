package com.asiscontrol.controller;

import com.asiscontrol.dto.OfertaCobroDto;
import com.asiscontrol.service.OfertasCobroService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracion-pagos/ofertas")
@PreAuthorize("hasAuthority('PAGOS_LEER') and hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','TESORERIA')")
public class OfertasCobroController {
    private final OfertasCobroService service;

    public OfertasCobroController(OfertasCobroService service) { this.service = service; }

    @GetMapping
    public Page<OfertaCobroDto.OfertaResponse> listar(
            @PageableDefault(size = 10, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','TESORERIA')")
    public List<OfertaCobroDto.OfertaResponse> crear(@Valid @RequestBody OfertaCobroDto.CrearRequest request) {
        return service.crear(request);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','TESORERIA')")
    public OfertaCobroDto.OfertaResponse cancelar(@PathVariable Long id,
            @Valid @RequestBody(required = false) OfertaCobroDto.CambioEstadoRequest request) {
        return service.cancelar(id, request);
    }
}
