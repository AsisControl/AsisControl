package com.asiscontrol.controller;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.service.CalculoRiesgoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/evaluaciones-riesgo")
@PreAuthorize("hasAuthority('RIESGOS_ESCRIBIR')")
public class EvaluacionRiesgoController {

    private final CalculoRiesgoService riesgoService;

    public EvaluacionRiesgoController(CalculoRiesgoService riesgoService) {
        this.riesgoService = riesgoService;
    }

    @PostMapping
    public RiesgoDto.EvaluacionResponse evaluar(
            @Valid @RequestBody RiesgoDto.RecalculoRequest request
    ) {
        return riesgoService.recalcular(request);
    }
}
