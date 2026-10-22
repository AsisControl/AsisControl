package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResumenResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAula;
import com.asiscontrol.service.AulaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/aulas")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class AulaController {

    private final AulaService aulaService;

    public AulaController(AulaService aulaService) {
        this.aulaService = aulaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AulaResponse crear(@Valid @RequestBody CrearAulaRequest request) {
        return aulaService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AulaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarAulaRequest request) {
        return aulaService.actualizar(id, request);
    }

    @GetMapping("/{id}")
    public AulaResponse obtener(@PathVariable Long id) {
        return aulaService.obtener(id);
    }

    @GetMapping("/resumen")
    public AulaResumenResponse resumen() {
        return aulaService.resumen();
    }

    @GetMapping
    public Page<AulaResponse> listar(
            @RequestParam(required = false) EstadoAula estado,
            @PageableDefault(size = 20, sort = "codigo") Pageable pageable) {
        return aulaService.listar(estado, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        aulaService.eliminar(id);
    }
}
