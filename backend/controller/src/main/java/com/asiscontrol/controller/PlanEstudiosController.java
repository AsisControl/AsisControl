package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.PlanEstudiosDtos.GuardarPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanEstudios;
import com.asiscontrol.service.PlanEstudiosService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planes-estudios/{anio}/{grado}")
public class PlanEstudiosController {
    private final PlanEstudiosService service;

    public PlanEstudiosController(PlanEstudiosService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACADEMICO_LEER')")
    public PlanEstudios consultar(@PathVariable int anio, @PathVariable int grado) {
        return service.consultar(anio, grado);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public PlanEstudios guardar(@PathVariable int anio, @PathVariable int grado, @Valid @RequestBody GuardarPlan request) {
        return service.guardar(anio, grado, request);
    }
}
