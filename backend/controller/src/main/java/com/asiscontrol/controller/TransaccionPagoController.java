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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transacciones-pago")
@PreAuthorize("hasAuthority('PAGOS_LEER')")
public class TransaccionPagoController {

    private final PagoService pagoService;
    private final ModuloAccessGuard accessGuard;

    public TransaccionPagoController(PagoService pagoService, ModuloAccessGuard accessGuard) {
        this.pagoService = pagoService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public Page<PagoDto.TransaccionResponse> listar(
            @RequestParam(required = false) Long idCompromisoPago,
            @PageableDefault(size = 20, sort = "fechaTransaccion", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return pagoService.listarTransacciones(
                idCompromisoPago,
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_LEER') and @moduloAccessGuard.puedeAccederTransaccionPago(#id)")
    public PagoDto.TransaccionResponse obtener(@PathVariable Long id) {
        return pagoService.obtenerTransaccion(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public PagoDto.TransaccionResponse registrar(
            @Valid @RequestBody PagoDto.TransaccionRequest request
    ) {
        return pagoService.registrarTransaccion(request);
    }
}
