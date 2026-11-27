package com.asiscontrol.controller;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaDocenteResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.ClaseDiaDocenteResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.CodigoQRResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.HistorialMarcacionDocenteResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.CorregirMarcacionDocenteRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.EmitirCodigoQRRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.EscanearCodigoQRRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RegistrarContingenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.ResumenMarcacionResponse;
import com.asiscontrol.entity.enums.EstadoVerificacionClase;
import com.asiscontrol.service.AsistenciaDocenteService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/asistencias/docentes")
@PreAuthorize("hasAuthority('ASISTENCIA_LEER') and "
        + "hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','DOCENTE')")
public class AsistenciaDocenteController {

    /** El código del punto de marcación lo administra el personal del colegio, no los docentes. */
    private static final String PUNTO_DE_MARCACION = "hasAuthority('ASISTENCIA_ESCRIBIR') and "
            + "hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA')";

    private final AsistenciaDocenteService asistenciaService;
    private final ModuloAccessGuard accessGuard;

    public AsistenciaDocenteController(
            AsistenciaDocenteService asistenciaService,
            ModuloAccessGuard accessGuard
    ) {
        this.asistenciaService = asistenciaService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/qr")
    @PreAuthorize(PUNTO_DE_MARCACION)
    public ResponseEntity<CodigoQRResponse> emitir(@Valid @RequestBody(required = false) EmitirCodigoQRRequest request) {
        return ResponseEntity.status(201).body(asistenciaService.emitir(request));
    }

    @PostMapping("/qr/{id}/renovar")
    @PreAuthorize(PUNTO_DE_MARCACION)
    public CodigoQRResponse renovar(
            @PathVariable Long id,
            @RequestParam(required = false) @Min(30) @Max(900) Integer vigenciaSegundos) {
        return asistenciaService.renovar(id, vigenciaSegundos);
    }

    @GetMapping("/qr/{id}")
    @PreAuthorize(PUNTO_DE_MARCACION)
    public CodigoQRResponse consultarCodigo(@PathVariable Long id) {
        return asistenciaService.consultarCodigo(id);
    }

    @PostMapping("/qr/{id}/cancelar")
    @PreAuthorize(PUNTO_DE_MARCACION)
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        asistenciaService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA')")
    public ResumenMarcacionResponse resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return asistenciaService.resumen(fecha);
    }

    @GetMapping("/{docenteId}/clases")
    public List<ClaseDiaDocenteResponse> clasesDelDia(
            @PathVariable Long docenteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return asistenciaService.clasesDelDia(docenteId, fecha);
    }

    @PostMapping("/marcar")
    @PreAuthorize("hasAuthority('ASISTENCIA_ESCRIBIR')")
    public AsistenciaDocenteResponse marcar(@Valid @RequestBody EscanearCodigoQRRequest request) {
        return asistenciaService.marcarConToken(request.token());
    }

    @PostMapping("/contingencia")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ASISTENCIA_ESCRIBIR')")
    public ResponseEntity<AsistenciaDocenteResponse> registrarContingencia(
            @Valid @RequestBody RegistrarContingenciaRequest request) {
        return ResponseEntity.status(201).body(asistenciaService.registrarContingencia(request));
    }

    // correccion marcacion docente: solo el administrador corrige una marcación existente, con motivo
    @PutMapping("/{id}/correccion")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ASISTENCIA_ESCRIBIR')")
    public AsistenciaDocenteResponse corregir(@PathVariable Long id, @Valid @RequestBody CorregirMarcacionDocenteRequest request) {
        return asistenciaService.corregir(id, request);
    }

    @GetMapping("/{id}/correcciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public HistorialMarcacionDocenteResponse correcciones(@PathVariable Long id) {
        return asistenciaService.correcciones(id);
    }

    @GetMapping
    public Page<AsistenciaDocenteResponse> listar(
            @RequestParam(required = false) Long docenteId,
            @RequestParam(required = false) EstadoVerificacionClase verificacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @PageableDefault(size = 20, sort = "fechaJornada") Pageable pageable) {
        return asistenciaService.listar(
                docenteId,
                accessGuard.restriccionDocentePersonaId(),
                verificacion,
                fecha,
                pageable);
    }
}
