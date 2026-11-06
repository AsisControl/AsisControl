// asistencia alumnos
package com.asiscontrol.config.asistencia;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj del servidor en la zona de la institución. Las reglas de asistencia
 * ("hoy", vencimiento del borrador) no dependen de la zona horaria del servidor.
 */
@Configuration
public class RelojAsistenciaConfig {

    public static final ZoneId ZONA_INSTITUCION = ZoneId.of("America/Lima");

    @Bean
    public Clock relojInstitucion() {
        return Clock.system(ZONA_INSTITUCION);
    }
}
