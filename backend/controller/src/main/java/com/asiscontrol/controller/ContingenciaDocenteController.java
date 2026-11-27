package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.asistencia.ContingenciaDocenteDto;
import com.asiscontrol.entity.asistencia.SolicitudContingenciaDocente.Estado;
import com.asiscontrol.service.ContingenciaDocenteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asistencias/docentes/contingencias")
public class ContingenciaDocenteController {
    private static final String ADMIN = "hasRole('ADMINISTRADOR') and hasAuthority('ASISTENCIA_ESCRIBIR')";
    private final ContingenciaDocenteService servicio;

    public ContingenciaDocenteController(ContingenciaDocenteService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE') and hasAuthority('ASISTENCIA_ESCRIBIR')")
    public ContingenciaDocenteDto.Response solicitar(@Valid @RequestBody ContingenciaDocenteDto.Solicitar datos) {
        return servicio.solicitar(datos);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ASISTENCIA_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE')")
    public PageResponse<ContingenciaDocenteDto.Response> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Estado estado,
            @PageableDefault(sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pagina) {
        return servicio.listar(estado, pagina, "ADMINISTRADOR".equals(jwt.getClaimAsString("rol")));
    }

    @PostMapping("/{id}/resoluciones")
    @PreAuthorize(ADMIN)
    public ContingenciaDocenteDto.Response resolver(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ContingenciaDocenteDto.Resolucion resolucion) {
        return servicio.resolver(id, resolucion, jwt.getSubject());
    }
}
