package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.auditoria.AuditoriaDtos;
import com.asiscontrol.entity.enums.EstadoError;
import com.asiscontrol.service.AuditoriaService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/registros")
    public PageResponse<AuditoriaDtos.AuditResponse> audits(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long rolId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) com.asiscontrol.entity.enums.ResultadoAuditoria resultado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditoriaService.listarAuditorias(
                search, rolId, modulo, accion, resultado, page, size);
    }

    /** Modulos con registros, para el filtro de la pantalla de auditoria. */
    @GetMapping("/modulos")
    public java.util.List<String> modulos() {
        return auditoriaService.listarModulos();
    }

    /** Acciones con registros, para el filtro de la pantalla de auditoria. */
    @GetMapping("/acciones")
    public java.util.List<String> acciones() {
        return auditoriaService.listarAcciones();
    }

    @GetMapping("/errores")
    public PageResponse<AuditoriaDtos.ErrorResponse> errors(
            @RequestParam(required = false) EstadoError estado,
            @RequestParam(required = false) com.asiscontrol.entity.enums.SeveridadError severidad,
            @RequestParam(required = false) String componente,
            @RequestParam(required = false) String correlacion,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    java.time.LocalDate desde,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    java.time.LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditoriaService.filtrarErrores(
                estado, severidad, componente, correlacion, desde, hasta, page, size);
    }

    @GetMapping("/errores/{id}/seguimientos")
    public java.util.List<AuditoriaDtos.SeguimientoResponse> seguimientos(@PathVariable Long id) {
        return auditoriaService.seguimientos(id);
    }

    @PutMapping("/errores/{id}")
    public AuditoriaDtos.ErrorResponse updateError(
            @PathVariable Long id, @Valid @RequestBody AuditoriaDtos.UpdateErrorRequest request) {
        return auditoriaService.actualizarError(id, request);
    }
}
