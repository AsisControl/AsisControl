package com.asiscontrol.service;

import com.asiscontrol.security.PasswordPolicy;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Contraseñas temporales para los accesos creados en una importación: 12 caracteres con
 * mayúscula, minúscula y número, sin caracteres que se confunden al imprimirlos (0/O, 1/l/I).
 */
@Component
public class GeneradorContrasenaTemporal {

    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String TODOS = MAYUSCULAS + MINUSCULAS + NUMEROS;
    static final int LONGITUD = 12;

    private final SecureRandom azar = new SecureRandom();

    public String generar(String username) {
        String candidata;
        do {
            StringBuilder texto = new StringBuilder(LONGITUD);
            for (int i = 0; i < LONGITUD; i++) texto.append(TODOS.charAt(azar.nextInt(TODOS.length())));
            candidata = texto.toString();
        } while (!cumple(candidata, username));
        return candidata;
    }

    private static boolean cumple(String candidata, String username) {
        try {
            PasswordPolicy.validate(candidata, username);
            return true;
        } catch (RuntimeException noCumple) {
            return false;
        }
    }
}
