package com.asiscontrol.controller;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.CrearJustificacionRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.JustificacionAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RevisarJustificacionRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.SubsanarJustificacionRequest;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import com.asiscontrol.service.JustificacionAsistenciaService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/justificaciones-asistencia")
@PreAuthorize("hasAuthority('ASISTENCIA_LEER')")
public class JustificacionAsistenciaController {

    private final JustificacionAsistenciaService justificacionService;
    private final ModuloAccessGuard accessGuard;

    public JustificacionAsistenciaController(
            JustificacionAsistenciaService justificacionService,
            ModuloAccessGuard accessGuard
    ) {
        this.justificacionService = justificacionService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    @PreAuthorize("hasRole('APODERADO')")
    public ResponseEntity<JustificacionAsistenciaResponse> crear(
            @Valid @RequestBody CrearJustificacionRequest request) {
        return ResponseEntity.status(201).body(justificacionService.crear(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederJustificacion(#id)")
    public JustificacionAsistenciaResponse obtener(@PathVariable Long id) {
        return justificacionService.obtener(id);
    }

    @GetMapping
    public Page<JustificacionAsistenciaResponse> listar(
            @RequestParam(required = false) Set<EstadoJustificacion> estado,
            @PageableDefault(size = 20, sort = "fechaHoraEnvio") Pageable pageable) {
        return justificacionService.listar(
                estado,
                accessGuard.restriccionDocentePersonaId(),
                accessGuard.restriccionAlumnoPersonaId(),
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @PostMapping("/{id}/iniciar-revision")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public JustificacionAsistenciaResponse iniciarRevision(
            @PathVariable Long id,
            @Valid @RequestBody RevisarJustificacionRequest request) {
        return justificacionService.iniciarRevision(id, request);
    }

    @PostMapping("/{id}/observar")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public JustificacionAsistenciaResponse observar(
            @PathVariable Long id,
            @Valid @RequestBody RevisarJustificacionRequest request) {
        return justificacionService.observar(id, request);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public JustificacionAsistenciaResponse aprobar(
            @PathVariable Long id,
            @Valid @RequestBody RevisarJustificacionRequest request) {
        return justificacionService.aprobar(id, request);
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public JustificacionAsistenciaResponse rechazar(
            @PathVariable Long id,
            @Valid @RequestBody RevisarJustificacionRequest request) {
        return justificacionService.rechazar(id, request);
    }

    @PostMapping("/{id}/subsanar")
    @PreAuthorize("hasRole('APODERADO')")
    public JustificacionAsistenciaResponse subsanar(
            @PathVariable Long id,
            @Valid @RequestBody SubsanarJustificacionRequest request) {
        return justificacionService.subsanar(id, request);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('APODERADO')")
    public JustificacionAsistenciaResponse cancelar(@PathVariable Long id) {
        return justificacionService.cancelar(id);
    }
}
