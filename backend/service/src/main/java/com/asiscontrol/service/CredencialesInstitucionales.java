package com.asiscontrol.service;

import com.asiscontrol.entity.Persona;
import com.asiscontrol.repository.AccesoUsuarioRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Usuario y correo de acceso generados. El usuario es primer nombre + "." + apellido paterno,
 * sin tildes ni signos (María José Quispe → maria.quispe); si ya existe, se agrega 2, 3, … El
 * correo es el código del perfil en minúsculas con el dominio institucional configurado.
 */
@Component
public class CredencialesInstitucionales {

    static final int MAXIMO_USERNAME = 80;
    static final int MINIMO_USERNAME = 4;

    private final AccesoUsuarioRepository accesos;
    private final String dominio;

    public CredencialesInstitucionales(
            AccesoUsuarioRepository accesos,
            @Value("${asiscontrol.correo.dominio:asiscontrol.edu.pe}") String dominio) {
        this.accesos = accesos;
        String limpio = dominio == null ? "" : dominio.trim().toLowerCase(Locale.ROOT);
        if (!limpio.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+"))
            throw new IllegalArgumentException(
                    "asiscontrol.correo.dominio debe ser un dominio como asiscontrol.edu.pe");
        this.dominio = limpio;
    }

    /** Primer usuario libre, comparando sin mayúsculas e incluyendo accesos desactivados. */
    public String username(Persona persona) {
        String base = baseUsername(persona.getNombres(), persona.getApellidoPaterno());
        // Un usuario muy corto (a.b) empieza con sufijo para cumplir el mínimo de 4 caracteres.
        int sufijo = base.length() < MINIMO_USERNAME ? 2 : 1;
        String candidato = conSufijo(base, sufijo);
        while (accesos.existsByUsernameIgnoreCase(candidato)) {
            candidato = conSufijo(base, ++sufijo);
        }
        return candidato;
    }

    public String correo(String codigoPerfil) {
        return codigoPerfil.trim().toLowerCase(Locale.ROOT) + "@" + dominio;
    }

    static String baseUsername(String nombres, String apellidoPaterno) {
        String nombre = normalizar(primeraPalabra(nombres));
        String apellido = normalizar(apellidoPaterno);
        if (nombre.isEmpty()) nombre = "usuario";
        return apellido.isEmpty() ? nombre : nombre + "." + apellido;
    }

    static String conSufijo(String base, int sufijo) {
        String numero = sufijo <= 1 ? "" : Integer.toString(sufijo);
        int espacio = MAXIMO_USERNAME - numero.length();
        String recortado = base.length() > espacio ? base.substring(0, espacio) : base;
        // Sin punto final si el recorte deja solo el nombre.
        if (recortado.endsWith(".")) recortado = recortado.substring(0, recortado.length() - 1);
        return recortado + numero;
    }

    /** Minúsculas, sin tildes (ñ → n) y solo letras y números: sin espacios, apóstrofos ni guiones. */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String sinMarcas =
                Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinMarcas.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String primeraPalabra(String nombres) {
        if (nombres == null) return "";
        String limpio = nombres.trim();
        int espacio = limpio.indexOf(' ');
        return espacio < 0 ? limpio : limpio.substring(0, espacio);
    }
}
