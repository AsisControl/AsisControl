package com.asiscontrol.controller;

import com.asiscontrol.dto.SustentoInformeDto;
import com.asiscontrol.service.SustentoInformeService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/calificaciones/informes")
@PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE','ALUMNO','APODERADO')")
public class SustentoInformeController {
    private final SustentoInformeService sustento;

    public SustentoInformeController(SustentoInformeService sustento) { this.sustento = sustento; }

    @GetMapping("/{idInforme}/filas/{idDetalle}/evidencias")
    public ResponseEntity<SustentoInformeDto> listar(@PathVariable Long idInforme, @PathVariable Long idDetalle) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sustento.listar(idInforme, idDetalle));
    }

    @GetMapping("/{idInforme}/filas/{idDetalle}/evidencias/{idEvidencia}")
    public ResponseEntity<SustentoInformeDto.Evidencia> obtener(@PathVariable Long idInforme,
            @PathVariable Long idDetalle, @PathVariable Long idEvidencia) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sustento.obtener(idInforme, idDetalle, idEvidencia));
    }

    @GetMapping("/{idInforme}/filas/{idDetalle}/evidencias/{idEvidencia}/archivo")
    public ResponseEntity<Resource> descargar(@PathVariable Long idInforme, @PathVariable Long idDetalle,
            @PathVariable Long idEvidencia) {
        return archivo(sustento.descargar(idInforme, idDetalle, idEvidencia));
    }

    @GetMapping("/parcial/evidencias")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE')")
    public ResponseEntity<java.util.Map<String, java.util.List<SustentoInformeDto.Evidencia>>> listarParcial(
            @RequestParam Long idAlumno, @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico,
            @RequestParam Long idEvaluacion, @RequestParam Long idCompetencia) {
        var fila = new SustentoInformeService.FilaParcial(idAlumno, idAsignacionCurso, idPeriodoAcademico, idEvaluacion, idCompetencia);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("evidencias", sustento.listarParcial(fila)));
    }

    @GetMapping("/parcial/evidencias/{idEvidencia}/archivo")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE')")
    public ResponseEntity<Resource> descargarParcial(@PathVariable Long idEvidencia,
            @RequestParam Long idAlumno, @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico,
            @RequestParam Long idEvaluacion, @RequestParam Long idCompetencia) {
        var fila = new SustentoInformeService.FilaParcial(idAlumno, idAsignacionCurso, idPeriodoAcademico, idEvaluacion, idCompetencia);
        return archivo(sustento.descargarParcial(fila, idEvidencia));
    }

    private ResponseEntity<Resource> archivo(com.asiscontrol.service.ArchivoStorageService.DescargaArchivo archivo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(archivo.tipoMime())).contentLength(archivo.tamanioBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombreOriginal(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(archivo.recurso());
    }
}
