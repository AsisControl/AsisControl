package com.asiscontrol.controller;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.service.PagosFamiliaService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** La capacidad HMAC solo autoriza esta orden QR durante su vigencia, nunca otra API. */
@RestController
@RequestMapping("/api/pagos/qr")
public class PagoQrController {
    private final PagosFamiliaService service;

    public PagoQrController(PagosFamiliaService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public ResponseEntity<PagosFamiliaDto.OrdenResponse> info(@PathVariable String token) {
        return respuesta(service.qr(token));
    }

    @PostMapping("/{token}/confirmar")
    public ResponseEntity<PagosFamiliaDto.OrdenResponse> confirmar(@PathVariable String token,
            @Valid @RequestBody PagosFamiliaDto.QrConfirmacionRequest request) {
        return respuesta(service.confirmarQr(token, request));
    }

    private ResponseEntity<PagosFamiliaDto.OrdenResponse> respuesta(PagosFamiliaDto.OrdenResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(body);
    }
}
