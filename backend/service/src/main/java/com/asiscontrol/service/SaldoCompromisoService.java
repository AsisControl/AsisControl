package com.asiscontrol.service;

import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.repository.TransaccionPagoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Recalcula lo pagado y el estado de un compromiso a partir de sus transacciones aprobadas.
 * Se invoca cuando una transacción cambia a Aprobada o cuando una corrección válida la afecta,
 * de modo que la siguiente consulta refleje el saldo y los indicadores actualizados.
 */
@Service
public class SaldoCompromisoService {

    private final TransaccionPagoRepository transacciones;

    public SaldoCompromisoService(TransaccionPagoRepository transacciones) {
        this.transacciones = transacciones;
    }

    /** Debe llamarse dentro de la transacción que modificó las transacciones del compromiso. */
    public void recalcular(CompromisoPago compromiso) {
        BigDecimal aprobado = transacciones.sumarAprobadas(compromiso.getId());
        compromiso.setMontoPagado((aprobado == null ? BigDecimal.ZERO : aprobado).setScale(2, RoundingMode.HALF_UP));
        compromiso.setEstado(EstadoFinancieroCompromiso.visible(compromiso, EstadoFinancieroCompromiso.hoy()));
    }
}
