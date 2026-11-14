package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.EstadoOfertaCobro;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class OfertaCobroDto {
    private OfertaCobroDto() {}

    public record CrearRequest(
            @NotNull Long idConcepto,
            @NotEmpty @Size(max = 100) List<@NotNull Long> idMatriculas,
            @NotBlank @Size(max = 80) String referencia,
            @Size(max = 160) String titulo,
            @Size(max = 600) String descripcion,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monto,
            @NotNull LocalDate fechaVencimiento,
            LocalDate fechaLimiteAceptacion
    ) {}

    public record AceptarRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 100) String firmaCotizacion
    ) {}

    public record CambioEstadoRequest(
            @Min(0) Long version,
            @Size(max = 500) String motivo
    ) {}

    public record OfertaResponse(
            Long id, String codigo, Long idMatricula, Long idAlumno, String alumno,
            int anioAcademico, Long idConcepto, String concepto, String referencia,
            String titulo, String descripcion,
            BigDecimal montoBase, BigDecimal montoDescuento, BigDecimal monto,
            String beneficioNombre, LocalDate fechaVencimiento, LocalDate fechaLimiteAceptacion,
            EstadoOfertaCobro estado, Long idCompromiso, String codigoCompromiso,
            Instant fechaCreacion, Instant respondidaEn, long version, String firmaCotizacion
    ) {}
}
