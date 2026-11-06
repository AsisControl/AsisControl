// asistencia alumnos
package com.asiscontrol.config.asistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Reloj del servidor en la zona de la institución. Las reglas de asistencia
 * ("hoy", vencimiento del borrador) no dependen de la zona horaria del servidor.
 */
@Configuration
public class RelojAsistenciaConfig {

    public static final ZoneId ZONA_INSTITUCION = ZoneId.of("America/Lima");

    private static final Logger log = LoggerFactory.getLogger(RelojAsistenciaConfig.class);

    @Bean
    public Clock relojInstitucion(@Value("${app.asistencia.fecha-simulada:}") String fechaSimulada) {
        return crearReloj(fechaSimulada, Clock.system(ZONA_INSTITUCION));
    }

    /**
     * Solo para desarrollo: la asistencia se toma el día de la clase, de modo que sin esto un curso
     * de otro año no se puede probar. Cambia el día y conserva la hora real, que sigue avanzando.
     */
    static Clock crearReloj(String fechaSimulada, Clock real) {
        if (fechaSimulada == null || fechaSimulada.isBlank()) {
            return real;
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaSimulada.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    "ASISTENCIA_FECHA_SIMULADA debe tener el formato AAAA-MM-DD: " + fechaSimulada, e);
        }
        log.warn("FECHA SIMULADA ACTIVA: la asistencia opera como si hoy fuera {}. No usar en producción.", fecha);
        return Clock.offset(real, Duration.between(
                LocalDate.now(real).atStartOfDay(), fecha.atStartOfDay()));
    }
}
