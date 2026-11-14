package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.EstadoOrdenPago;
import com.asiscontrol.entity.enums.MetodoPagoSimulado;
import com.asiscontrol.entity.enums.TipoComprobante;
import com.asiscontrol.entity.enums.MedioPago;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;

public final class PagosFamiliaDto {
    private PagosFamiliaDto() {}

    public enum EscenarioTarjeta { APROBADO, FONDOS_INSUFICIENTES, RECHAZADO, REQUIERE_AUTENTICACION }
    public enum AccionQr { APROBAR, RECHAZAR }

    public record InicioRequest(
            @NotEmpty @Size(max = 20) List<@NotNull @Positive Long> obligacionesIds,
            @NotNull MetodoPagoSimulado metodo) {
        @AssertTrue(message = "Selecciona cada obligación una sola vez")
        public boolean isSeleccionSinDuplicados() {
            return obligacionesIds == null || new HashSet<>(obligacionesIds).size() == obligacionesIds.size();
        }
    }

    public record TarjetaRequest(@NotNull EscenarioTarjeta escenario) {}
    public record AutenticacionRequest(@NotNull Boolean aprobar) {}
    public record QrConfirmacionRequest(@NotNull AccionQr accion) {}

    public record EstudianteResponse(Long id, String codigoAlumno, String nombreCompleto) {}

    public record ItemResponse(Long idCompromiso, String codigoCompromiso, String alumno,
                               String concepto, Integer numeroCuota, BigDecimal monto) {}

    public record OrdenResponse(Long id, String codigo, MetodoPagoSimulado metodo,
                                EstadoOrdenPago estado, String moneda, BigDecimal montoBase,
                                BigDecimal mora, BigDecimal montoTotal, Instant creadaEn,
                                Instant expiraEn, Instant pagadaEn, String institucion,
                                String apoderado, List<ItemResponse> items, String qrUrl,
                                String paymentIntentId, String clientSecret, String mensaje,
                                boolean simulador) {}

    public record ComprobanteResponse(Long id, Long idTransaccionPago,
                                      TipoComprobante tipoComprobante, String serie, String numero,
                                      Instant fechaEmision, BigDecimal montoTotal,
                                      Long idArchivoAdjunto, Instant fechaCreacion) {}

    public record ReciboResponse(OrdenResponse orden, List<ComprobanteResponse> comprobantes) {}

    public record HistorialComprobanteResponse(Long id, String codigo, Instant fechaEmision,
            BigDecimal montoTotal, String moneda, String alumno, String concepto,
            Integer numeroCuota, String codigoCompromiso, MedioPago medioPago,
            Long idOrden, String codigoOrden, String institucion, String apoderado) {}

    public record ObligacionDetalle(PagoDto.CompromisoResponse compromiso,
                                     List<PagoDto.TransaccionResponse> transacciones,
                                     List<PagoDto.ComprobanteResponse> comprobantes) {}
}
