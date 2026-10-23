package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AdministradorRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.ArchivoAdjuntoRepository;
import com.asiscontrol.repository.ComprobanteRepository;
import com.asiscontrol.repository.CompromisoPagoRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.TransaccionPagoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private CompromisoPagoRepository compromisoPagoRepository;
    @Mock
    private TransaccionPagoRepository transaccionPagoRepository;
    @Mock
    private ComprobanteRepository comprobanteRepository;
    @Mock
    private MatriculaRepository matriculaRepository;
    @Mock
    private ApoderadoRepository apoderadoRepository;
    @Mock
    private AdministradorRepository administradorRepository;
    @Mock
    private ArchivoAdjuntoRepository archivoAdjuntoRepository;

    @Mock
    private AuditoriaService auditoria;

    @InjectMocks
    private PagoService pagoService;

    @Test
    void simularSinInteresConservaElMontoYLiquidaElSaldo() {
        PagoDto.SimulacionRequest request = new PagoDto.SimulacionRequest(
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                3,
                BigDecimal.ZERO,
                LocalDate.of(2026, 10, 15)
        );

        PagoDto.SimulacionResponse response = pagoService.simular(request);

        assertThat(response.montoFinanciado()).isEqualByComparingTo("900.00");
        assertThat(response.totalInteres()).isEqualByComparingTo("0.00");
        assertThat(response.totalPagar()).isEqualByComparingTo("1000.00");
        assertThat(response.cronograma()).hasSize(3);
        assertThat(response.cronograma().get(2).saldoRestante()).isEqualByComparingTo("0.00");
    }

    @Test
    void rechazaUnPagoQueExcedeElSaldo() {
        CompromisoPago compromiso = new CompromisoPago();
        compromiso.setId(10L);
        compromiso.setMontoTotal(new BigDecimal("100.00"));
        compromiso.setMontoPagado(new BigDecimal("80.00"));
        compromiso.setEstado(EstadoCompromisoPago.PARCIAL);
        compromiso.setFechaVencimiento(LocalDate.now().plusDays(5));
        when(compromisoPagoRepository.bloquearPorId(10L)).thenReturn(Optional.of(compromiso));

        PagoDto.TransaccionRequest request = new PagoDto.TransaccionRequest(
                10L,
                20L,
                null,
                "OP-001",
                new BigDecimal("25.00"),
                MedioPago.TRANSFERENCIA_BANCARIA,
                null,
                null
        );

        assertThatThrownBy(() -> pagoService.registrarTransaccion(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("excede el saldo");
    }
}
