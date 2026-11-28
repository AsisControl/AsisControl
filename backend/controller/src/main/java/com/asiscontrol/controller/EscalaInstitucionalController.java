package com.asiscontrol.controller;

import com.asiscontrol.dto.EscalaInstitucionalDto;
import com.asiscontrol.service.EscalaInstitucionalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escala-institucional")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class EscalaInstitucionalController {
    private final EscalaInstitucionalService escalas;

    public EscalaInstitucionalController(EscalaInstitucionalService escalas) {
        this.escalas = escalas;
    }

    @GetMapping
    public List<EscalaInstitucionalDto.Escala> historial() {
        return escalas.historial();
    }

    @PutMapping("/borrador")
    public EscalaInstitucionalDto.Escala guardar(@Valid @RequestBody EscalaInstitucionalDto.Borrador request) {
        return escalas.guardar(request);
    }

    @PostMapping("/{id}/activar")
    public EscalaInstitucionalDto.Escala activar(@PathVariable Long id,
            @Valid @RequestBody EscalaInstitucionalDto.Activacion request) {
        return escalas.activar(id, request);
    }
}
