package com.asiscontrol.service;

import com.asiscontrol.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/** Capacidad temporal de una sola orden. No usa el JWT del apoderado en el celular. */
@Service
public class TokenQrPagoService {
    private static final String DOMINIO = "asiscontrol:qr-pago:v1:";
    private final SecretKey clave;

    public TokenQrPagoService(SecretKey jwtSecretKey) {
        this.clave = jwtSecretKey;
    }

    public String generar(String codigo) {
        return codigo + "." + firma(codigo);
    }

    public String validar(String token) {
        if (token == null || token.length() > 150) throw noEncontrado();
        String[] partes = token.split("\\.", -1);
        if (partes.length != 2 || !partes[0].matches("OP-[A-F0-9]{32}")) throw noEncontrado();
        if (!MessageDigest.isEqual(firma(partes[0]).getBytes(StandardCharsets.US_ASCII),
                partes[1].getBytes(StandardCharsets.US_ASCII))) throw noEncontrado();
        return partes[0];
    }

    public String secretoTarjeta(String intento) {
        return intento + "_secret_sim_" + firma("tarjeta:" + intento);
    }

    private String firma(String codigo) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(clave);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    hmac.doFinal((DOMINIO + codigo).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("No se pudo generar la capacidad de pago.", error);
        }
    }

    private ResourceNotFoundException noEncontrado() {
        return new ResourceNotFoundException("QR_NO_DISPONIBLE", "Este enlace de pago no está disponible.");
    }
}
