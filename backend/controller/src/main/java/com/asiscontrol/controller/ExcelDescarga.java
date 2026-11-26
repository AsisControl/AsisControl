package com.asiscontrol.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuesta de descarga de una plantilla de carga masiva (.xlsx). */
final class ExcelDescarga {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ExcelDescarga() {}

    static ResponseEntity<byte[]> adjunto(String nombreArchivo, byte[] contenido) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentDisposition(
                ContentDisposition.attachment().filename(nombreArchivo).build());
        cabeceras.setContentType(XLSX);
        return ResponseEntity.ok().headers(cabeceras).body(contenido);
    }
}
