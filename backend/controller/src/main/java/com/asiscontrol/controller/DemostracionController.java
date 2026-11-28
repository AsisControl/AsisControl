package com.asiscontrol.controller;

import com.asiscontrol.dto.DemostracionDtos.*;
import com.asiscontrol.service.DemostracionService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/administracion/demostracion")
@ConditionalOnProperty(name = "app.demostracion.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class DemostracionController {
    private final DemostracionService servicio;
    public DemostracionController(DemostracionService servicio) { this.servicio = servicio; }
    @GetMapping
    public Estado estado() { return servicio.estado(); }
    @PostMapping(consumes = "multipart/form-data")
    public Resultado preparar(@RequestParam Etapa etapa, @RequestPart("archivo") MultipartFile archivo) {
        return servicio.preparar(etapa, archivo);
    }
}
