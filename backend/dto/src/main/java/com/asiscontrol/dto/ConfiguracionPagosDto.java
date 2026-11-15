package com.asiscontrol.dto;

import com.asiscontrol.entity.enums.PeriodicidadCobro;
import com.asiscontrol.entity.enums.TipoBeneficio;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ConfiguracionPagosDto {
    private ConfiguracionPagosDto() {}
    public record ConceptoRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{2,40}") String codigo,
        @NotBlank @Size(max=160) String nombre,@Size(max=600)String descripcion,
        @NotNull PeriodicidadCobro periodicidad,Boolean esPension,
        @DecimalMin("0.01") @Digits(integer=10,fraction=2)BigDecimal montoSugerido,
        Boolean requiereAceptacion,@Min(0)Long version) {
        public ConceptoRequest {esPension=Boolean.TRUE.equals(esPension);requiereAceptacion=Boolean.TRUE.equals(requiereAceptacion);}
    }
    public record ConceptoResponse(Long id,String codigo,String nombre,String descripcion,PeriodicidadCobro periodicidad,
        boolean esPension,BigDecimal montoSugerido,boolean requiereAceptacion,boolean activo,long version) {}
    public record TarifaRequest(@NotNull Long idConcepto,@Min(1900) @Max(9999)int anioAcademico,
        @Min(1) @Max(5)int grado,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2)BigDecimal montoMensual,@Min(0)Long version) {}
    public record TarifaResponse(Long id,Long idConcepto,String concepto,int anioAcademico,int grado,BigDecimal montoMensual,boolean activo,long version) {}
    public record BeneficioRequest(@NotNull Long idAlumno,@Min(1900) @Max(9999)int anioAcademico,@NotNull Long idConcepto,
        @NotBlank @Size(max=160)String nombre,@NotNull TipoBeneficio tipo,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2)BigDecimal valor,
        @Min(1) @Max(12)int mesInicio,@Min(1) @Max(12)int mesFin,@NotBlank @Size(max=500)String motivo,@Min(0)Long version) {}
    public record BeneficioResponse(Long id,Long idAlumno,String alumno,int anioAcademico,Long idConcepto,String concepto,
        String nombre,TipoBeneficio tipo,BigDecimal valor,int mesInicio,int mesFin,String motivo,boolean activo,long version) {}
    public record EstudianteResponse(Long id,String codigoAlumno,String nombreCompleto) {}
    public record PlanItem(int mes,int numeroCuota,String concepto,LocalDate fechaVencimiento,BigDecimal montoBase,
        BigDecimal montoDescuento,BigDecimal montoTotal,String beneficioNombre,Long idCompromiso,String estado) {}
    public record PlanResponse(Long idMatricula,Long idSolicitud,Long idAlumno,String alumno,int anioAcademico,int grado,
        Long idTarifa,BigDecimal montoMensual,boolean puedeGenerar,String mensaje,List<PlanItem>items,
        List<PagoDto.CompromisoResponse>legados,Long tarifaVersion,String firmaPlan) {}
    public record VincularRequest(@NotNull Long idCompromiso,@Min(3) @Max(12)Integer mes,@NotBlank @Size(max=500)String motivo,@NotNull @Min(0)Long version,Long idConcepto) {
        public VincularRequest(Long idCompromiso,Integer mes,String motivo,Long version){this(idCompromiso,mes,motivo,version,null);}
    }
    public record GenerarPlanRequest(@NotBlank @Size(max=64)String firmaPlan) {}
    public record AplicarBeneficioRequest(@NotBlank @Size(max=500)String motivo,@NotNull @Min(0)Long version,@NotBlank @Size(max=64)String firmaAjuste) {
        public AplicarBeneficioRequest(String motivo,Long version){this(motivo,version,null);}
    }
    public record AjusteBeneficioResponse(Long idBeneficio,String mensaje,List<PagoDto.CompromisoResponse>ajustables,List<PagoDto.CompromisoResponse>omitidos,String firmaAjuste) {}
}
