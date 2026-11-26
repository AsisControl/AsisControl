package com.asiscontrol.controller;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.service.ImportacionMatriculaService;
import com.asiscontrol.service.ImportacionTarifaService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OBS-05 · ASISCTRL-232/233: carga masiva de tarifas de pensión y de matrículas con sección.
 * Con {@code confirmar=false} solo revisa el archivo; con {@code confirmar=true} registra las
 * filas válidas. Cada endpoint exige el mismo permiso que el registro manual.
 */
@RestController
public class CargaMasivaMatriculaController {

    private static final String TARIFAS = "hasAuthority('PAGOS_LEER') and hasAuthority('PAGOS_ESCRIBIR')"
            + " and hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','TESORERIA')";
    private static final String MATRICULAS = "hasAuthority('MATRICULA_LEER') and hasRole('ADMINISTRADOR')"
            + " and hasAuthority('MATRICULA_ESCRIBIR')";

    private final ImportacionTarifaService tarifas;
    private final ImportacionMatriculaService matriculas;

    public CargaMasivaMatriculaController(
            ImportacionTarifaService tarifas, ImportacionMatriculaService matriculas) {
        this.tarifas = tarifas;
        this.matriculas = matriculas;
    }

    @PostMapping(value = "/api/configuracion-pagos/tarifas/importaciones",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(TARIFAS)
    public CargaMasivaDto.Resultado importarTarifas(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return tarifas.importar(archivo, confirmar);
    }

    @GetMapping("/api/configuracion-pagos/tarifas/importaciones/plantilla")
    @PreAuthorize(TARIFAS)
    public ResponseEntity<byte[]> plantillaTarifas() {
        return ExcelDescarga.adjunto("plantilla_tarifas.xlsx", tarifas.plantilla());
    }

    @PostMapping(value = "/api/matriculas/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MATRICULAS)
    public CargaMasivaDto.Resultado importarMatriculas(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return matriculas.importar(archivo, confirmar);
    }

    @GetMapping("/api/matriculas/importaciones/plantilla")
    @PreAuthorize(MATRICULAS)
    public ResponseEntity<byte[]> plantillaMatriculas() {
        return ExcelDescarga.adjunto("plantilla_matriculas.xlsx", matriculas.plantilla());
    }
}
