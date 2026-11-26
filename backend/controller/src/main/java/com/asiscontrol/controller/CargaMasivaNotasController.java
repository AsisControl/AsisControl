package com.asiscontrol.controller;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.service.ImportacionNotasService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OBS-05 · ASISCTRL-234: carga masiva de notas de una evaluación. Mismo permiso que la planilla
 * ({@code CALIFICACIONES_ESCRIBIR}); el servicio además exige ser el docente de la asignación.
 */
@RestController
@PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAuthority('CALIFICACIONES_ESCRIBIR')")
public class CargaMasivaNotasController {

    private final ImportacionNotasService notas;

    public CargaMasivaNotasController(ImportacionNotasService notas) {
        this.notas = notas;
    }

    @PostMapping(value = "/api/evaluaciones/{id}/calificaciones/importaciones",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CargaMasivaDto.Resultado importar(
            @PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return notas.importar(id, archivo, confirmar);
    }

    @GetMapping("/api/evaluaciones/{id}/calificaciones/importaciones/plantilla")
    public ResponseEntity<byte[]> plantilla(@PathVariable Long id) {
        return ExcelDescarga.adjunto("plantilla_notas_evaluacion_" + id + ".xlsx", notas.plantilla(id));
    }
}
