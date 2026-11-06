package com.asiscontrol.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Lectura de los valores "clave=valor,clave=valor" que guarda RegistroAuditoria. */
final class ValoresAuditoria {

    /** Solo separa ante "clave=": un curso puede llamarse "Ciencia, Tecnologia y Ambiente". */
    private static final Pattern SEPARADOR_CAMPOS = Pattern.compile(",(?=[A-Za-z]+=)");

    private ValoresAuditoria() {}

    static Map<String, String> separar(String valores) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (valores == null) {
            return campos;
        }
        for (String campo : SEPARADOR_CAMPOS.split(valores)) {
            int igual = campo.indexOf('=');
            if (igual > 0) {
                campos.put(campo.substring(0, igual), campo.substring(igual + 1));
            }
        }
        return campos;
    }
}
