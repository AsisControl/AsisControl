package com.asiscontrol.security;

import java.security.SecureRandom;

public final class GeneradorContrasenaTemporal {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MAYUSCULAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMEROS = "0123456789";
    private static final String CARACTERES = MAYUSCULAS + MINUSCULAS + NUMEROS;

    private GeneradorContrasenaTemporal() {}

    public static String generar(String username) {
        String resultado;
        do {
            char[] caracteres = new char[20];
            caracteres[0] = MAYUSCULAS.charAt(RANDOM.nextInt(MAYUSCULAS.length()));
            caracteres[1] = MINUSCULAS.charAt(RANDOM.nextInt(MINUSCULAS.length()));
            caracteres[2] = NUMEROS.charAt(RANDOM.nextInt(NUMEROS.length()));
            for (int indice = 3; indice < caracteres.length; indice++) {
                caracteres[indice] = CARACTERES.charAt(RANDOM.nextInt(CARACTERES.length()));
            }
            for (int indice = caracteres.length - 1; indice > 0; indice--) {
                int destino = RANDOM.nextInt(indice + 1);
                char anterior = caracteres[indice];
                caracteres[indice] = caracteres[destino];
                caracteres[destino] = anterior;
            }
            resultado = new String(caracteres);
        } while (resultado.equalsIgnoreCase(username));
        return resultado;
    }
}
