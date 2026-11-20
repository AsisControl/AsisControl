package com.asiscontrol.controller;

import com.asiscontrol.service.FamiliaService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/familia")
@PreAuthorize("hasRole('APODERADO') and hasAuthority('MATRICULA_LEER')")
public class FamiliaController {
    private final FamiliaService service;

    public FamiliaController(FamiliaService s) {
        service = s;
    }

    @GetMapping("/estudiantes")
    @PreAuthorize("hasRole('APODERADO') and (hasAuthority('MATRICULA_LEER') or hasAuthority('CALIFICACIONES_LEER'))")
    public List<FamiliaService.Estudiante> estudiantes() {
        return service.estudiantes();
    }

    @GetMapping("/oferta")
    public List<FamiliaService.Oferta> oferta() {
        return service.oferta();
    }
}
