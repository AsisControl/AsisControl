package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AsignacionCursoResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import com.asiscontrol.service.AsignacionCursoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/asignaciones-cursos")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class AsignacionCursoController {

    private final AsignacionCursoService asignacionCursoService;

    public AsignacionCursoController(AsignacionCursoService asignacionCursoService) {
        this.asignacionCursoService = asignacionCursoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AsignacionCursoResponse crear(@Valid @RequestBody CrearAsignacionCursoRequest request) {
        return asignacionCursoService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AsignacionCursoResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarAsignacionCursoRequest request) {
        return asignacionCursoService.actualizar(id, request);
    }

    @GetMapping("/{id}")
    public AsignacionCursoResponse obtener(@PathVariable Long id) {
        return asignacionCursoService.obtener(id);
    }

    /** Un docente solo recibe sus propias asignaciones. */
    @GetMapping
    public Page<AsignacionCursoResponse> listar(
            @RequestParam(required = false) com.asiscontrol.dto.academico.AcademicoDtos.EstadoAsignacion estado,
            @RequestParam(required = false) Long docenteId,
            @RequestParam(required = false) Long cursoId,
            @RequestParam(required = false) Long seccionId,
            @RequestParam(required = false) Integer anioAcademico,
            @PageableDefault(size = 20, sort = "fechaCreacion") Pageable pageable) {
        return asignacionCursoService.listar(estado, docenteId, cursoId, seccionId, anioAcademico, pageable);
    }

    /** US-58 / PLA-06: retiro con motivo; se rechaza si el docente tiene bloques de horario. */
    @PatchMapping("/{id}/retirar")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AsignacionCursoResponse retirar(
            @PathVariable Long id,
            @Valid @RequestBody com.asiscontrol.dto.academico.AcademicoDtos.RetirarAsignacionCursoRequest request) {
        return asignacionCursoService.retirar(id, request);
    }
}
