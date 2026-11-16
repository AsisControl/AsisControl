package com.asiscontrol.controller;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.entity.enums.EstadoAlerta;
import com.asiscontrol.entity.enums.NivelRiesgo;
import com.asiscontrol.service.RiesgoService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas-riesgo")
@PreAuthorize("hasAuthority('RIESGOS_LEER')")
public class AlertaRiesgoController {

    private final RiesgoService riesgoService;
    private final ModuloAccessGuard accessGuard;
    private final com.asiscontrol.service.CalculoRiesgoService calculo;

    public AlertaRiesgoController(
            RiesgoService riesgoService,
            ModuloAccessGuard accessGuard,
            com.asiscontrol.service.CalculoRiesgoService calculo
    ) {
        this.riesgoService = riesgoService;
        this.accessGuard = accessGuard;
        this.calculo = calculo;
    }

    @GetMapping("/resumen")
    public java.util.List<RiesgoDto.ResumenResponse> resumen(@RequestParam Long idPeriodo) {
        return calculo.resumen(idPeriodo, accessGuard.restriccionDocentePersonaId());
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('RIESGOS_LEER') and @moduloAccessGuard.puedeAccederAlertaRiesgo(#id)")
    public java.util.List<RiesgoDto.HistorialResponse> historial(@PathVariable Long id) {
        riesgoService.obtenerAlerta(id);
        return calculo.historial(id);
    }

    @GetMapping
    public Page<RiesgoDto.AlertaResponse> listar(
            @RequestParam(required = false) Long idAlumno,
            @RequestParam(required = false) EstadoAlerta estado,
            @RequestParam(required = false) NivelRiesgo nivel,
            @PageableDefault(size = 20, sort = "fechaDeteccion", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return riesgoService.listarAlertas(
                idAlumno,
                estado,
                nivel,
                accessGuard.restriccionDocentePersonaId(),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RIESGOS_LEER') and @moduloAccessGuard.puedeAccederAlertaRiesgo(#id)")
    public RiesgoDto.AlertaResponse obtener(@PathVariable Long id) {
        return riesgoService.obtenerAlerta(id);
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('RIESGOS_ESCRIBIR')")
    public RiesgoDto.AlertaResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody RiesgoDto.EstadoAlertaRequest request
    ) {
        return riesgoService.cambiarEstadoAlerta(id, request);
    }
}
