package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.AulaResumenResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAula;
import com.asiscontrol.dto.academico.AcademicoDtos.HabilitarAulaRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.InhabilitarAulaRequest;
import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.service.AulaService;
import com.asiscontrol.service.ImportacionAulaService;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/aulas")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class AulaController {

    private final AulaService aulaService;
    private final ImportacionAulaService importacion;

    public AulaController(AulaService aulaService, ImportacionAulaService importacion) {
        this.aulaService = aulaService;
        this.importacion = importacion;
    }

    /**
     * OBS-05: con {@code confirmar=false} solo revisa el archivo y no guarda nada; con
     * {@code confirmar=true} registra las filas válidas. Mismo permiso que el alta manual.
     */
    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public CargaMasivaDto.Resultado importar(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return importacion.importar(archivo, confirmar);
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public ResponseEntity<byte[]> plantilla() {
        return ExcelDescarga.adjunto("plantilla_aulas.xlsx", importacion.plantilla());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AulaResponse crear(@Valid @RequestBody CrearAulaRequest request) {
        return aulaService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AulaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarAulaRequest request) {
        return aulaService.actualizar(id, request);
    }

    @PatchMapping("/{id}/inhabilitar")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AulaResponse inhabilitar(@PathVariable Long id, @Valid @RequestBody InhabilitarAulaRequest request) {
        return aulaService.inhabilitar(id, request);
    }

    @PatchMapping("/{id}/habilitar")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public AulaResponse habilitar(@PathVariable Long id, @Valid @RequestBody HabilitarAulaRequest request) {
        return aulaService.habilitar(id, request);
    }

    /** US-30: incluye bloques de cualquier sección; el alumno usa /api/horarios/mio. */
    @GetMapping("/{id}/horarios")
    @PreAuthorize("hasAuthority('ACADEMICO_LEER') and !hasRole('ALUMNO')")
    public List<HorarioResponse> horarios(@PathVariable Long id) {
        return aulaService.horarios(id);
    }

    @GetMapping("/{id}")
    public AulaResponse obtener(@PathVariable Long id) {
        return aulaService.obtener(id);
    }

    @GetMapping("/resumen")
    public AulaResumenResponse resumen() {
        return aulaService.resumen();
    }

    @GetMapping
    public Page<AulaResponse> listar(
            @RequestParam(required = false) EstadoAula estado,
            @PageableDefault(size = 20, sort = "codigo") Pageable pageable) {
        return aulaService.listar(estado, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        aulaService.eliminar(id);
    }
}
