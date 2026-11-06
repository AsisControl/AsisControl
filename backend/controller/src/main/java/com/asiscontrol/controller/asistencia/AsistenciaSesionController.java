// asistencia alumnos
package com.asiscontrol.controller.asistencia;

import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.CorregirAsistenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.CorreccionAsistenciaResponse;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.FinalizarAsistenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.GuardarBorradorRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.SesionAsistenciaResponse;
import com.asiscontrol.service.asistencia.AsistenciaSesionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/asistencias/sesiones")
@PreAuthorize("hasAuthority('ASISTENCIA_LEER')")
public class AsistenciaSesionController {

    private final AsistenciaSesionService asistenciaService;

    public AsistenciaSesionController(AsistenciaSesionService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping("/{sesionId}")
    public SesionAsistenciaResponse obtener(@PathVariable Long sesionId) {
        return asistenciaService.obtener(sesionId);
    }

    @PutMapping("/{sesionId}/borrador")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public SesionAsistenciaResponse guardarBorrador(
            @PathVariable Long sesionId, @Valid @RequestBody GuardarBorradorRequest request) {
        return asistenciaService.guardarBorrador(sesionId, request);
    }

    @PostMapping("/{sesionId}/finalizar")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public SesionAsistenciaResponse finalizar(
            @PathVariable Long sesionId, @Valid @RequestBody FinalizarAsistenciaRequest request) {
        return asistenciaService.finalizar(sesionId, request);
    }

    /** Corrección de una asistencia TOMADA: exige motivo y la versión que vio el docente. */
    @PutMapping("/{sesionId}")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public SesionAsistenciaResponse corregir(
            @PathVariable Long sesionId, @Valid @RequestBody CorregirAsistenciaRequest request) {
        return asistenciaService.corregir(sesionId, request);
    }

    @GetMapping("/{sesionId}/correcciones")
    public List<CorreccionAsistenciaResponse> listarCorrecciones(@PathVariable Long sesionId) {
        return asistenciaService.listarCorrecciones(sesionId);
    }
}
