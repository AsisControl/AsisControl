package com.asiscontrol.controller;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.dto.PagosFamiliaDto;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.service.ConsultaObligacionesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apoderado/obligaciones")
@PreAuthorize("hasRole('APODERADO') and hasAuthority('PAGOS_LEER')")
public class ObligacionesApoderadoController {
    private final ConsultaObligacionesService service;

    public ObligacionesApoderadoController(ConsultaObligacionesService service) {
        this.service = service;
    }

    @GetMapping("/estudiantes")
    public List<PagosFamiliaDto.EstudianteResponse> estudiantes() {
        return service.estudiantes();
    }

    @GetMapping
    public Page<PagoDto.CompromisoResponse> listar(@RequestParam(required = false) Long idAlumno,
            @RequestParam(required = false) EstadoCompromisoPago estado,
            @PageableDefault(size = 10, sort = "fechaVencimiento", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.listar(idAlumno, estado, pageable);
    }

    @GetMapping("/{id}")
    public PagosFamiliaDto.ObligacionDetalle detalle(@PathVariable Long id) {
        return service.detalle(id);
    }
}
