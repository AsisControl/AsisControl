package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarCompetenciaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CompetenciaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCompetenciaRequest;
import com.asiscontrol.service.CompetenciaService;
import com.asiscontrol.service.ImportacionCompetenciaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/competencias")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class CompetenciaController {

    private final CompetenciaService competenciaService;
    private final ImportacionCompetenciaService importacion;

    public CompetenciaController(CompetenciaService competenciaService, ImportacionCompetenciaService importacion) {
        this.competenciaService = competenciaService;
        this.importacion = importacion;
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=competencias.xlsx")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(importacion.plantilla());
    }

    @PostMapping(value = "/importaciones", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public ImportacionCompetenciaService.Resultado importar(@RequestPart MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return importacion.importar(archivo, confirmar);
    }

    @GetMapping
    public Page<CompetenciaResponse> listar(
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return competenciaService.listar(busqueda, pageable);
    }

    @GetMapping("/{id}")
    public CompetenciaResponse obtener(@PathVariable Long id) {
        return competenciaService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public CompetenciaResponse crear(@Valid @RequestBody CrearCompetenciaRequest request) {
        return competenciaService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public CompetenciaResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarCompetenciaRequest request
    ) {
        return competenciaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        competenciaService.eliminar(id);
    }
}
