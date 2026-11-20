package com.asiscontrol.config;

import com.asiscontrol.service.RegeneracionIdentificadoresCuenta;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Al arrancar completa el usuario y el correo generados de las cuentas migradas. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegeneracionIdentificadoresRunner implements ApplicationRunner {

    private final RegeneracionIdentificadoresCuenta regeneracion;

    public RegeneracionIdentificadoresRunner(RegeneracionIdentificadoresCuenta regeneracion) {
        this.regeneracion = regeneracion;
    }

    @Override
    public void run(ApplicationArguments args) {
        regeneracion.regenerar();
    }
}
