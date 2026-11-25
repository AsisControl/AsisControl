package com.asiscontrol.controller;

import com.asiscontrol.dto.ReglaCalificacionDto;
import com.asiscontrol.service.ReglaCalificacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reglas-calificacion")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ReglaCalificacionController {
    private final ReglaCalificacionService reglas;

    public ReglaCalificacionController(ReglaCalificacionService reglas) {
        this.reglas = reglas;
    }

    @GetMapping
    public List<ReglaCalificacionDto.Regla> historial(@RequestParam Long idCurso, @RequestParam Long idGrado) {
        return reglas.historial(idCurso, idGrado);
    }

    @GetMapping("/vigente")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE')")
    public ResponseEntity<ReglaCalificacionDto.Regla> vigente(@RequestParam Long idAsignacionCurso) {
        return reglas.vigentePorAsignacion(idAsignacionCurso).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/borrador")
    public ReglaCalificacionDto.Regla guardar(@Valid @RequestBody ReglaCalificacionDto.Borrador request) {
        return reglas.guardarBorrador(request);
    }

    @PostMapping("/{id}/activar")
    public ReglaCalificacionDto.Regla activar(@PathVariable Long id, @Valid @RequestBody ReglaCalificacionDto.Activacion request) {
        return reglas.activar(id, request.version());
    }

    @PostMapping("/vista-previa")
    public ReglaCalificacionDto.Resultado vistaPrevia(@Valid @RequestBody ReglaCalificacionDto.VistaPrevia request) {
        return reglas.vistaPrevia(request);
    }
}
