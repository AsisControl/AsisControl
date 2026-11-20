package com.asiscontrol.controller;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.service.PagoService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comprobantes")
@PreAuthorize("hasAuthority('PAGOS_LEER')")
public class ComprobanteController {

    private final PagoService pagoService;
    private final ModuloAccessGuard accessGuard;

    public ComprobanteController(PagoService pagoService, ModuloAccessGuard accessGuard) {
        this.pagoService = pagoService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public Page<PagoDto.ComprobanteResponse> listar(
            @PageableDefault(size = 20, sort = "fechaEmision", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return pagoService.listarComprobantes(
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_LEER') and @moduloAccessGuard.puedeAccederComprobante(#id)")
    public PagoDto.ComprobanteResponse obtener(@PathVariable Long id) {
        return pagoService.obtenerComprobante(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public PagoDto.ComprobanteResponse emitir(
            @Valid @RequestBody PagoDto.ComprobanteRequest request
    ) {
        return pagoService.emitirComprobante(request);
    }
}
