package com.asiscontrol.dto;

import com.asiscontrol.dto.persona.PersonaDtos.ImportIssue;

import java.util.List;

/** OBS-05: respuesta común de las cargas masivas por módulo. */
public final class CargaMasivaDto {

    private CargaMasivaDto() {}

    /**
     * @param total filas con datos leídas del archivo
     * @param validas filas que pasan todas las reglas y se registran (o se registrarían)
     * @param creadas filas guardadas; siempre 0 al solo revisar el archivo
     * @param omitidas filas ya registradas o repetidas en el archivo: no se duplican
     * @param errores filas rechazadas con su fila, campo y motivo; no se guardan
     */
    public record Resultado(
            int total,
            int validas,
            int creadas,
            List<ImportIssue> omitidas,
            List<ImportIssue> errores) {}
}
