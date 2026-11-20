package com.asiscontrol.controller;

import com.asiscontrol.dto.SustentoInformeDto;
import com.asiscontrol.service.SustentoInformeService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/calificaciones/informes/{idInforme}/filas/{idDetalle}/evidencias")
@PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE','ALUMNO','APODERADO')")
public class SustentoInformeController {
    private final SustentoInformeService sustento;

    public SustentoInformeController(SustentoInformeService sustento) { this.sustento = sustento; }

    @GetMapping
    public ResponseEntity<SustentoInformeDto> listar(@PathVariable Long idInforme, @PathVariable Long idDetalle) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sustento.listar(idInforme, idDetalle));
    }

    @GetMapping("/{idEvidencia}")
    public ResponseEntity<SustentoInformeDto.Evidencia> obtener(@PathVariable Long idInforme,
            @PathVariable Long idDetalle, @PathVariable Long idEvidencia) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sustento.obtener(idInforme, idDetalle, idEvidencia));
    }

    @GetMapping("/{idEvidencia}/archivo")
    public ResponseEntity<Resource> descargar(@PathVariable Long idInforme, @PathVariable Long idDetalle,
            @PathVariable Long idEvidencia) {
        var archivo = sustento.descargar(idInforme, idDetalle, idEvidencia);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(archivo.tipoMime())).contentLength(archivo.tamanioBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombreOriginal(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(archivo.recurso());
    }
}
