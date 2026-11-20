package com.asiscontrol.controller;

import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.service.HistorialPagosFamiliaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apoderado/pagos/historial")
@PreAuthorize("hasRole('APODERADO') and hasAuthority('PAGOS_LEER')")
public class HistorialPagosApoderadoController {
    private final HistorialPagosFamiliaService service;
    public HistorialPagosApoderadoController(HistorialPagosFamiliaService service) { this.service = service; }

    @GetMapping
    public Page<PagosFamiliaDto.HistorialPagoResponse> listar(@RequestParam(required = false) Long idAlumno,
            @RequestParam(required = false) PagosFamiliaDto.ResultadoHistorial estado,
            @PageableDefault(size = 10) Pageable pageable) {
        return service.listar(idAlumno, estado, pageable);
    }
}
