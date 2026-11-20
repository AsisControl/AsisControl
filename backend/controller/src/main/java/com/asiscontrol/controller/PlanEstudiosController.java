package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.PlanEstudiosDtos.GuardarPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanEstudios;
import com.asiscontrol.service.PlanEstudiosService;
import com.asiscontrol.service.GeneradorPlanEstudios;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planes-estudios/{anio}")
public class PlanEstudiosController {
    private final PlanEstudiosService service;
    private final GeneradorPlanEstudios generador;

    public PlanEstudiosController(PlanEstudiosService service, GeneradorPlanEstudios generador) {
        this.service = service;
        this.generador = generador;
    }

    @GetMapping("/{grado}")
    @PreAuthorize("hasAuthority('ACADEMICO_LEER')")
    public PlanEstudios consultar(@PathVariable int anio, @PathVariable int grado) {
        return service.consultar(anio, grado);
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_LEER')")
    public ResponseEntity<byte[]> descargar(@PathVariable int anio) {
        var plan = service.consultarPlanAnual(anio);
        var nombre = ContentDisposition.attachment().filename("Plan_de_Estudios_AsisControl_" + anio + ".pdf").build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, nombre.toString())
                .cacheControl(CacheControl.noStore())
                .body(generador.generar(plan));
    }

    @PutMapping("/{grado}")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public PlanEstudios guardar(@PathVariable int anio, @PathVariable int grado, @Valid @RequestBody GuardarPlan request) {
        return service.guardar(anio, grado, request);
    }
}
