package com.asiscontrol.controller;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.service.ComprobantesFamiliaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/apoderado/comprobantes")
@PreAuthorize("hasRole('APODERADO') and hasAuthority('PAGOS_LEER')")
public class ComprobantesApoderadoController {
    private final ComprobantesFamiliaService service;

    public ComprobantesApoderadoController(ComprobantesFamiliaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PagosFamiliaDto.HistorialComprobanteResponse> listar(
            @PageableDefault(size = 10, sort = "fechaEmision", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public PagosFamiliaDto.HistorialComprobanteResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"constancia-" + id + ".html\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(service.descargar(id).getBytes(StandardCharsets.UTF_8));
    }
}
