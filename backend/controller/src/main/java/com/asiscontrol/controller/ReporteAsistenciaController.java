package com.asiscontrol.controller;

import com.asiscontrol.dto.asistencia.ReporteAsistenciaDtos.*;
import com.asiscontrol.service.ReporteAsistenciaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes/asistencia")
@PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ASISTENCIA_LEER')")
public class ReporteAsistenciaController {
    private final ReporteAsistenciaService service;
    public ReporteAsistenciaController(ReporteAsistenciaService service) { this.service = service; }
    @GetMapping("/filtros") public Filtros filtros() { return service.filtros(); }
    @GetMapping public Informe consultar(@RequestParam Tipo tipo,
            @RequestParam(required = false) Long periodoId, @RequestParam(required = false) Long seccionId,
            @RequestParam(required = false) Long personaId, @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) Estado estado, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.consultar(tipo, periodoId, seccionId, personaId, busqueda, desde, hasta, estado, pagina, tamano);
    }
}
