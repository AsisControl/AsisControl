package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.MedioPago;
import com.asiscontrol.entity.enums.TipoComprobante;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PagoDto {

    private PagoDto() {
    }

    public record CompromisoRequest(
            @NotNull Long idMatricula,
            @Size(max = 50) String codigoCompromiso,
            @NotBlank @Size(max = 160) String concepto,
            @Size(max = 600) String descripcion,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal montoTotal,
            @NotBlank @Pattern(regexp = "PEN", message = "Los compromisos se emiten en soles (PEN)") String moneda,
            @NotNull LocalDate fechaVencimiento,
            @NotNull @Min(1) Integer numeroCuota
    ) {
    }

    public record CompromisoActualizacionRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal montoTotal,
            @NotNull LocalDate fechaVencimiento,
            @NotBlank @Size(max = 500) String motivo,
            @NotNull @Min(0) Long version
    ) {
    }

    public record CompromisoAnulacionRequest(
            @NotBlank @Size(max = 500) String motivo,
            @NotNull @Min(0) Long version
    ) {
    }

    public record MatriculaCompromisoResponse(
            Long id,
            String codigoMatricula,
            String alumno,
            String codigoAlumno,
            Long alumnoId,
            int anioAcademico,
            int grado,
            String seccion,
            String estado
    ) {
    }

    public record CompromisoResponse(
            Long id,
            Long idMatricula,
            String codigoCompromiso,
            String concepto,
            String descripcion,
            BigDecimal montoTotal,
            BigDecimal montoPagado,
            BigDecimal saldo,
            String moneda,
            LocalDate fechaVencimiento,
            EstadoCompromisoPago estado,
            Instant fechaCreacion,
            Instant fechaActualizacion,
            Integer numeroCuota,
            EstadoAdministrativoCompromiso estadoAdministrativo,
            EstadoCompromisoPago estadoFinanciero,
            Long idAlumno,
            String alumno,
            String codigoAlumno,
            int anioAcademico,
            String codigoMatricula,
            String motivoAnulacion,
            Instant fechaAnulacion,
            long version
    ) {
    }

    public record TransaccionRequest(
            @NotNull Long idCompromisoPago,
            Long idApoderado,
            Long idAdministrador,
            @NotBlank @Size(max = 100) String numeroOperacion,
            @NotNull @DecimalMin("0.01") BigDecimal monto,
            @NotNull MedioPago medioPago,
            @PastOrPresent Instant fechaTransaccion,
            @Size(max = 600) String observacion
    ) {
    }

    public record TransaccionResponse(
            Long id,
            Long idCompromisoPago,
            Long idApoderado,
            Long idAdministrador,
            String numeroOperacion,
            BigDecimal monto,
            MedioPago medioPago,
            EstadoTransaccionPago estado,
            Instant fechaTransaccion,
            String observacion,
            Instant fechaCreacion
    ) {
    }

    public record ComprobanteRequest(
            @NotNull Long idTransaccionPago,
            @NotNull TipoComprobante tipoComprobante,
            @NotBlank @Size(max = 20) String serie,
            @NotBlank @Size(max = 30) String numero,
            Long idArchivoAdjunto
    ) {
    }

    public record ComprobanteResponse(
            Long id,
            Long idTransaccionPago,
            Long idArchivoAdjunto,
            TipoComprobante tipoComprobante,
            String serie,
            String numero,
            Instant fechaEmision,
            BigDecimal montoTotal
    ) {
    }

    public record SimulacionRequest(
            @NotNull @DecimalMin("0.01") BigDecimal montoTotal,
            @NotNull @DecimalMin("0.00") BigDecimal cuotaInicial,
            @Min(1) @Max(60) int numeroCuotas,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal tasaInteresMensual,
            @NotNull LocalDate fechaPrimeraCuota
    ) {
    }

    public record CuotaSimulada(
            int numero,
            LocalDate fechaVencimiento,
            BigDecimal capital,
            BigDecimal interes,
            BigDecimal montoCuota,
            BigDecimal saldoRestante
    ) {
    }

    public record SimulacionResponse(
            BigDecimal montoTotal,
            BigDecimal cuotaInicial,
            BigDecimal montoFinanciado,
            BigDecimal totalInteres,
            BigDecimal totalPagar,
            List<CuotaSimulada> cronograma
    ) {
    }
}
