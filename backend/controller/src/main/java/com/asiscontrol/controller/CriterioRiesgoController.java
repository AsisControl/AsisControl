package com.asiscontrol.controller;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.service.RiesgoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/criterios-riesgo")
@PreAuthorize("hasAuthority('RIESGOS_LEER')")
public class CriterioRiesgoController {

    private final RiesgoService riesgoService;

    public CriterioRiesgoController(RiesgoService riesgoService) {
        this.riesgoService = riesgoService;
    }

    @GetMapping
    public Page<RiesgoDto.CriterioResponse> listar(
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return riesgoService.listarCriterios(busqueda, pageable);
    }

    @GetMapping("/{id}")
    public RiesgoDto.CriterioResponse obtener(@PathVariable Long id) {
        return riesgoService.obtenerCriterio(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RIESGOS_ESCRIBIR')")
    public RiesgoDto.CriterioResponse crear(
            @Valid @RequestBody RiesgoDto.CriterioRequest request
    ) {
        return riesgoService.crearCriterio(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RIESGOS_ESCRIBIR')")
    public RiesgoDto.CriterioResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody RiesgoDto.CriterioRequest request
    ) {
        return riesgoService.actualizarCriterio(id, request);
    }

    @PutMapping("/{id}/activacion")
    @PreAuthorize("hasAuthority('RIESGOS_ESCRIBIR')")
    public RiesgoDto.CriterioResponse reactivar(@PathVariable Long id) {
        return riesgoService.reactivarCriterio(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RIESGOS_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        riesgoService.eliminarCriterio(id);
    }
}
