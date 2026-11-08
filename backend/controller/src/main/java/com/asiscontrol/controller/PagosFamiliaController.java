package com.asiscontrol.controller;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.service.PagosFamiliaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/pagos")
@PreAuthorize("hasRole('APODERADO') and hasAuthority('PAGOS_LEER')")
public class PagosFamiliaController {
    private final PagosFamiliaService service;

    public PagosFamiliaController(PagosFamiliaService service) {
        this.service = service;
    }

    @PostMapping("/iniciar")
    @ResponseStatus(HttpStatus.CREATED)
    public PagosFamiliaDto.OrdenResponse iniciar(@Valid @RequestBody PagosFamiliaDto.InicioRequest request,
            @RequestHeader("Idempotency-Key") String clave) {
        return service.iniciar(request, clave);
    }

    @GetMapping("/ordenes")
    public Page<PagosFamiliaDto.OrdenResponse> listar(
            @PageableDefault(size = 10, sort = "creadaEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/ordenes/{id}")
    public PagosFamiliaDto.OrdenResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping("/ordenes/{id}/confirmar-tarjeta")
    public PagosFamiliaDto.OrdenResponse confirmarTarjeta(@PathVariable Long id,
            @Valid @RequestBody PagosFamiliaDto.TarjetaRequest request) {
        return service.confirmarTarjeta(id, request);
    }

    @PostMapping("/ordenes/{id}/autenticar")
    public PagosFamiliaDto.OrdenResponse autenticar(@PathVariable Long id,
            @Valid @RequestBody PagosFamiliaDto.AutenticacionRequest request) {
        return service.autenticar(id, request);
    }

    @GetMapping("/ordenes/{id}/comprobante")
    public PagosFamiliaDto.ReciboResponse recibo(@PathVariable Long id) {
        return service.recibo(id);
    }

    @GetMapping("/ordenes/{id}/comprobante/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"constancia-orden-" + id + ".html\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(service.comprobanteHtml(id).getBytes(StandardCharsets.UTF_8));
    }
}
