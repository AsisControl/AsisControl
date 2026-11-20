package com.asiscontrol.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Cuánto dura una contraseña temporal (alta de acceso, reactivación, restablecimiento y
 * administrador inicial) antes de que deje de servir para el primer ingreso. Por defecto, 14 días.
 */
@Component
public class VigenciaContrasenaTemporal {

    private final Duration duracion;

    public VigenciaContrasenaTemporal(
            @Value("${asiscontrol.accesos.vigencia-contrasena-temporal:14d}") Duration duracion) {
        if (duracion == null || duracion.isNegative() || duracion.isZero())
            throw new IllegalArgumentException(
                    "asiscontrol.accesos.vigencia-contrasena-temporal debe ser positiva");
        this.duracion = duracion;
    }

    public Duration duracion() {
        return duracion;
    }

    public Instant expiraDesde(Instant momento) {
        return momento.plus(duracion);
    }
}
