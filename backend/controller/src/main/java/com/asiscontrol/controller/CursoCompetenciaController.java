package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCursoCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CursoCompetenciaResponse;
import com.asiscontrol.service.CursoCompetenciaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cursos-competencias")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class CursoCompetenciaController {

    private final CursoCompetenciaService cursoCompetenciaService;

    public CursoCompetenciaController(CursoCompetenciaService cursoCompetenciaService) {
        this.cursoCompetenciaService = cursoCompetenciaService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public ResponseEntity<CursoCompetenciaResponse> crear(@Valid @RequestBody CrearCursoCompetenciaRequest request) {
        var resultado = cursoCompetenciaService.crear(request);
        return ResponseEntity.status(resultado.creada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.relacion());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public CursoCompetenciaResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarCursoCompetenciaRequest request) {
        return cursoCompetenciaService.actualizar(id, request);
    }

    @GetMapping("/{id}")
    public CursoCompetenciaResponse obtener(@PathVariable Long id) {
        return cursoCompetenciaService.obtener(id);
    }

    @GetMapping
    public Page<CursoCompetenciaResponse> listar(
            @PageableDefault(size = 20, sort = "orden") Pageable pageable) {
        return cursoCompetenciaService.listar(pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        cursoCompetenciaService.eliminar(id);
    }
}
