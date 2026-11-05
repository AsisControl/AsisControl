package com.asiscontrol.controller;

import com.asiscontrol.dto.matricula.MatriculaDtos.ActualizarSolicitudMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearSolicitudMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.SolicitudMatriculaResponse;
import com.asiscontrol.dto.auditoria.AuditoriaDtos.HistorialResponse;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.service.SolicitudMatriculaService;

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

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes-matricula")
@PreAuthorize("hasAuthority('MATRICULA_LEER')")
public class SolicitudMatriculaController {

    private final SolicitudMatriculaService solicitudMatriculaService;
    private final ModuloAccessGuard accessGuard;

    public SolicitudMatriculaController(
            SolicitudMatriculaService solicitudMatriculaService, ModuloAccessGuard accessGuard) {
        this.solicitudMatriculaService = solicitudMatriculaService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
            "(hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_ESCRIBIR')) or"
                + " (hasRole('APODERADO') and hasAuthority('SOLICITUDES_ESCRIBIR'))")
    public SolicitudMatriculaResponse crear(
            @Valid @RequestBody CrearSolicitudMatriculaRequest request) {
        return solicitudMatriculaService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "(hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_ESCRIBIR')) or"
                + " (hasRole('APODERADO') and hasAuthority('SOLICITUDES_ESCRIBIR'))")
    public SolicitudMatriculaResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarSolicitudMatriculaRequest request) {
        return solicitudMatriculaService.actualizar(id, request);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('MATRICULA_LEER') and (hasRole('ADMINISTRADOR') or"
            + " (hasRole('APODERADO') and @moduloAccessGuard.puedeAccederSolicitudMatricula(#id)))")
    public List<HistorialResponse> historial(
            @PathVariable Long id) {
        return solicitudMatriculaService.historial(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederSolicitudMatricula(#id)")
    public SolicitudMatriculaResponse obtener(@PathVariable Long id) {
        return solicitudMatriculaService.obtener(id);
    }

    @GetMapping
    public Page<SolicitudMatriculaResponse> listar(
            @PageableDefault(size = 20, sort = "fechaCreacion") Pageable pageable) {
        return solicitudMatriculaService.listar(
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        solicitudMatriculaService.eliminar(id);
    }
}
