package com.asiscontrol.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** SQL sencillo: no admite procedimientos, DDL ni control transaccional. */
final class SentenciasDemostracion {
    private SentenciasDemostracion() {}
    static List<String> separar(String texto) {
        if (texto.contains("/*!") || texto.indexOf('\\') >= 0)
            throw DemostracionService.conflicto("Use SQL estándar sin comentarios ejecutables ni escapes con barra invertida");
        var resultado = new ArrayList<String>(); var actual = new StringBuilder();
        char cita = 0; boolean linea = false, bloque = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i), siguiente = i + 1 < texto.length() ? texto.charAt(i + 1) : 0;
            if (linea) { if (c == '\n') { linea = false; actual.append(' '); } continue; }
            if (bloque) { if (c == '*' && siguiente == '/') { bloque = false; i++; actual.append(' '); } continue; }
            if (cita != 0) {
                actual.append(c);
                if (c == cita) { if (siguiente == cita) { actual.append(siguiente); i++; } else cita = 0; }
                continue;
            }
            if (c == '\uFEFF') continue;
            if (c == '-' && siguiente == '-') { linea = true; i++; continue; }
            if (c == '#') { linea = true; continue; }
            if (c == '/' && siguiente == '*') { bloque = true; i++; continue; }
            if (c == '\'' || c == '"' || c == '`') { cita = c; actual.append(c); continue; }
            if (c == ';') { agregar(resultado, actual); actual.setLength(0); } else actual.append(c);
        }
        if (cita != 0 || bloque) throw DemostracionService.conflicto("El SQL tiene una comilla o comentario sin cerrar");
        agregar(resultado, actual);
        if (resultado.isEmpty() || resultado.size() > 2000) throw DemostracionService.conflicto("El archivo SQL está vacío o tiene demasiadas sentencias");
        return resultado;
    }
    private static void agregar(List<String> resultado, StringBuilder texto) {
        String sql = texto.toString().trim(); if (sql.isEmpty()) return;
        String verbo = sql.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        if (!List.of("SELECT", "INSERT", "UPDATE").contains(verbo))
            throw DemostracionService.conflicto("Solo se admiten SELECT, INSERT y UPDATE; el esquema se gestiona con Flyway");
        resultado.add(sql);
    }
}
