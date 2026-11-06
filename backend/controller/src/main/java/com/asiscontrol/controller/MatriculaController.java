package com.asiscontrol.controller;

import com.asiscontrol.dto.matricula.MatriculaDtos.ActualizarMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.MatriculaResponse;
import com.asiscontrol.service.MatriculaService;
import com.asiscontrol.service.ModuloAccessGuard;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matriculas")
@PreAuthorize("hasAuthority('MATRICULA_LEER')")
public class MatriculaController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.asiscontrol.service.AuditoriaService auditoria;

    private final MatriculaService matriculaService;
    private final ModuloAccessGuard accessGuard;

    public MatriculaController(MatriculaService matriculaService, ModuloAccessGuard accessGuard) {
        this.matriculaService = matriculaService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_ESCRIBIR')")
    public MatriculaResponse crear(@Valid @RequestBody CrearMatriculaRequest request) {
        return matriculaService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_ESCRIBIR')")
    public MatriculaResponse actualizar(
            @PathVariable Long id, @Valid @RequestBody ActualizarMatriculaRequest request) {
        return matriculaService.actualizar(id, request);
    }

    /** US-49: el apoderado consulta el historial de las matriculas de sus estudiantes asociados. */
    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('MATRICULA_LEER') and (hasRole('ADMINISTRADOR') or"
            + " (hasRole('APODERADO') and @moduloAccessGuard.puedeAccederMatricula(#id)))")
    public java.util.List<com.asiscontrol.dto.auditoria.AuditoriaDtos.HistorialResponse> historial(
            @PathVariable Long id) {
        matriculaService.obtener(id);
        return auditoria.historial("Matricula", id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederMatricula(#id)")
    public MatriculaResponse obtener(@PathVariable Long id) {
        return matriculaService.obtener(id);
    }

    @GetMapping
    public Page<MatriculaResponse> listar(
            @PageableDefault(size = 20, sort = "fechaMatricula") Pageable pageable) {
        return matriculaService.listar(
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        matriculaService.eliminar(id);
    }
}
