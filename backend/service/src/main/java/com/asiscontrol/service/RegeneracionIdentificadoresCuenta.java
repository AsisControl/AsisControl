package com.asiscontrol.service;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.repository.AccesoUsuarioRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completa las cuentas migradas desde la autenticación unificada, donde el nombre de usuario era el
 * código de la cuenta: les genera el usuario nombre.apellido y, si su correo es el generado del
 * dominio institucional, el correo a{código}@dominio. Un correo personal o ya verificado no se toca.
 * Es idempotente: solo actúa sobre las cuentas cuyo usuario sigue siendo igual a su código.
 */
@Service
public class RegeneracionIdentificadoresCuenta {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegeneracionIdentificadoresCuenta.class);

    private final AccesoUsuarioRepository cuentas;
    private final CredencialesInstitucionales credenciales;

    public RegeneracionIdentificadoresCuenta(
            AccesoUsuarioRepository cuentas, CredencialesInstitucionales credenciales) {
        this.cuentas = cuentas;
        this.credenciales = credenciales;
    }

    @Transactional
    public int regenerar() {
        int actualizadas = 0;
        for (AccesoUsuario cuenta : cuentas.pendientesDeUsername()) {
            cuenta.setUsername(credenciales.username(cuenta.getPersona()));
            if (!cuenta.isCorreoVerificado() && credenciales.esCorreoInstitucional(cuenta.getCorreo())) {
                String correo = credenciales.correoDeCuenta(cuenta.getCodigo());
                if (!correo.equalsIgnoreCase(cuenta.getCorreo()) && !cuentas.existsByCorreoIgnoreCase(correo)) {
                    cuenta.setCorreo(correo);
                }
            }
            // El siguiente usuario de la misma persona base debe ver este ya guardado.
            cuentas.saveAndFlush(cuenta);
            actualizadas++;
        }
        if (actualizadas > 0) {
            LOGGER.info("Se generó el usuario de {} cuentas que solo tenían su código.", actualizadas);
        }
        return actualizadas;
    }
}
