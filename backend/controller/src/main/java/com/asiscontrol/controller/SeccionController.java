package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.ConfirmarPropuestasSeccionesRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.GenerarPropuestasSeccionesRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionPropuestaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.SeccionResponse;
import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.service.SeccionService;
import com.asiscontrol.service.ImportacionSeccionService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/secciones")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class SeccionController {

    private final SeccionService seccionService;
    private final ImportacionSeccionService importacion;

    public SeccionController(SeccionService seccionService, ImportacionSeccionService importacion) {
        this.seccionService = seccionService;
        this.importacion = importacion;
    }

    /**
     * OBS-05: con {@code confirmar=false} solo revisa el archivo y no guarda nada; con
     * {@code confirmar=true} registra las filas válidas. Mismo permiso que el alta manual.
     */
    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public CargaMasivaDto.Resultado importar(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return importacion.importar(archivo, confirmar);
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public ResponseEntity<byte[]> plantilla() {
        return ExcelDescarga.adjunto("plantilla_secciones.xlsx", importacion.plantilla());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public SeccionResponse crear(@Valid @RequestBody CrearSeccionRequest request) {
        return seccionService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public SeccionResponse actualizar(
            @PathVariable Long id, @Valid @RequestBody ActualizarSeccionRequest request) {
        return seccionService.actualizar(id, request);
    }

    @PostMapping("/propuestas")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public List<SeccionPropuestaResponse> generarPropuestas(
            @Valid @RequestBody GenerarPropuestasSeccionesRequest request) {
        return seccionService.generarPropuestas(request);
    }

    @PostMapping("/propuestas/confirmar")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public List<SeccionResponse> confirmarPropuestas(
            @Valid @RequestBody ConfirmarPropuestasSeccionesRequest request) {
        return seccionService.confirmarPropuestas(request);
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public SeccionResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody
                    com.asiscontrol.dto.academico.ConfiguracionDtos.EstadoSeccion request) {
        return seccionService.cambiarEstado(id, request);
    }

    @GetMapping("/{id}")
    public SeccionResponse obtener(@PathVariable Long id) {
        return seccionService.obtener(id);
    }

    @GetMapping
    public Page<SeccionResponse> listar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) Integer anioAcademico,
            @RequestParam(required = false) Integer grado,
            @PageableDefault(
                            size = 20,
                            sort = {"anioAcademico", "grado", "nombre"})
                    Pageable pageable) {
        return seccionService.listar(activo, anioAcademico, grado, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        seccionService.eliminar(id);
    }
}
