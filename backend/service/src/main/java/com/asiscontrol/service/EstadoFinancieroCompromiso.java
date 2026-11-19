package com.asiscontrol.service;

import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Única regla del estado financiero de un compromiso (US-44).
 * El estado nunca se recibe del cliente: se deriva del monto, de lo pagado con
 * transacciones aprobadas y de la fecha de vencimiento en la zona del colegio.
 */
public final class EstadoFinancieroCompromiso {

    public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private EstadoFinancieroCompromiso() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA_NEGOCIO);
    }

    /**
     * Pendiente: sin pagos aprobados y no vencido. Parcial: pagos aprobados mayores que cero y menores
     * que el monto. Pagado: los pagos cubren el monto. Vencido: pasó la fecha de vencimiento y hay saldo.
     * Un compromiso de monto cero queda exonerado.
     */
    public static EstadoCompromisoPago calcular(BigDecimal montoTotal, BigDecimal montoPagado,
                                                LocalDate fechaVencimiento, LocalDate hoy) {
        BigDecimal total = valor(montoTotal);
        BigDecimal pagado = valor(montoPagado);
        if (total.signum() == 0) {
            return EstadoCompromisoPago.EXONERADO;
        }
        if (pagado.compareTo(total) >= 0) {
            return EstadoCompromisoPago.PAGADO;
        }
        if (fechaVencimiento != null && fechaVencimiento.isBefore(hoy)) {
            return EstadoCompromisoPago.VENCIDO;
        }
        return pagado.signum() > 0 ? EstadoCompromisoPago.PARCIAL : EstadoCompromisoPago.PENDIENTE;
    }

    /** Estado financiero de un compromiso activo, sin considerar su anulación. */
    public static EstadoCompromisoPago financiero(CompromisoPago compromiso, LocalDate hoy) {
        return calcular(compromiso.getMontoTotal(), compromiso.getMontoPagado(), compromiso.getFechaVencimiento(), hoy);
    }

    /** Estado visible: la anulación administrativa prevalece sobre el cálculo financiero. */
    public static EstadoCompromisoPago visible(CompromisoPago compromiso, LocalDate hoy) {
        return anulado(compromiso) ? EstadoCompromisoPago.ANULADO : financiero(compromiso, hoy);
    }

    public static BigDecimal saldo(BigDecimal montoTotal, BigDecimal montoPagado) {
        return valor(montoTotal).subtract(valor(montoPagado)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Un compromiso anulado no conserva saldo exigible. */
    public static BigDecimal saldo(CompromisoPago compromiso) {
        return anulado(compromiso) ? CERO : saldo(compromiso.getMontoTotal(), compromiso.getMontoPagado());
    }

    public static boolean anulado(CompromisoPago compromiso) {
        return compromiso.getEstadoAdministrativo() == EstadoAdministrativoCompromiso.ANULADO;
    }

    private static BigDecimal valor(BigDecimal monto) {
        return monto == null ? CERO : monto.setScale(2, RoundingMode.HALF_UP);
    }
}
