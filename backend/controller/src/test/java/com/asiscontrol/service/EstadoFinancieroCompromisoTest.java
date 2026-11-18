package com.asiscontrol.service;

import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoFinancieroCompromisoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);
    private static final BigDecimal MONTO = new BigDecimal("300.00");

    private static EstadoCompromisoPago estado(String pagado, LocalDate vencimiento) {
        return EstadoFinancieroCompromiso.calcular(MONTO, new BigDecimal(pagado), vencimiento, HOY);
    }

    @Test
    void activoSinPagosYNoVencidoEstaPendiente() {
        assertThat(estado("0.00", HOY.plusDays(5))).isEqualTo(EstadoCompromisoPago.PENDIENTE);
        assertThat(EstadoFinancieroCompromiso.calcular(MONTO, null, HOY.plusDays(5), HOY))
                .isEqualTo(EstadoCompromisoPago.PENDIENTE);
    }

    @Test
    void vencerHoyNoLoVuelveVencidoSinoAlDiaSiguiente() {
        assertThat(estado("0.00", HOY)).isEqualTo(EstadoCompromisoPago.PENDIENTE);
        assertThat(estado("0.00", HOY.minusDays(1))).isEqualTo(EstadoCompromisoPago.VENCIDO);
    }

    @Test
    void pagoMayorQueCeroYMenorQueElMontoEsParcialConSaldo() {
        assertThat(estado("0.01", HOY.plusDays(1))).isEqualTo(EstadoCompromisoPago.PARCIAL);
        assertThat(estado("299.99", HOY)).isEqualTo(EstadoCompromisoPago.PARCIAL);
        assertThat(EstadoFinancieroCompromiso.saldo(MONTO, new BigDecimal("120.50")))
                .isEqualByComparingTo("179.50");
    }

    @Test
    void pagosQueCubrenElMontoEstanPagadosConSaldoCeroAunqueHayaVencido() {
        assertThat(estado("300.00", HOY.plusDays(1))).isEqualTo(EstadoCompromisoPago.PAGADO);
        assertThat(estado("300.00", HOY.minusDays(30))).isEqualTo(EstadoCompromisoPago.PAGADO);
        assertThat(estado("350.00", HOY)).isEqualTo(EstadoCompromisoPago.PAGADO);
        assertThat(EstadoFinancieroCompromiso.saldo(MONTO, new BigDecimal("350.00"))).isEqualByComparingTo("0.00");
    }

    @Test
    void vencidoConSaldoPrevaleceSobreParcial() {
        assertThat(estado("100.00", HOY.minusDays(1))).isEqualTo(EstadoCompromisoPago.VENCIDO);
    }

    @Test
    void montoCeroQuedaExoneradoYAnuladoPrevaleceConSaldoCero() {
        assertThat(EstadoFinancieroCompromiso.calcular(BigDecimal.ZERO, BigDecimal.ZERO, HOY.minusDays(3), HOY))
                .isEqualTo(EstadoCompromisoPago.EXONERADO);
        var compromiso = new CompromisoPago();
        compromiso.setMontoTotal(MONTO);
        compromiso.setMontoPagado(BigDecimal.ZERO);
        compromiso.setFechaVencimiento(HOY.minusDays(3));
        compromiso.setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO);
        assertThat(EstadoFinancieroCompromiso.visible(compromiso, HOY)).isEqualTo(EstadoCompromisoPago.ANULADO);
        assertThat(EstadoFinancieroCompromiso.financiero(compromiso, HOY)).isEqualTo(EstadoCompromisoPago.VENCIDO);
        assertThat(EstadoFinancieroCompromiso.saldo(compromiso)).isEqualByComparingTo("0.00");
    }
}
