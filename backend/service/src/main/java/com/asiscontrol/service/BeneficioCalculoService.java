package com.asiscontrol.service;

import com.asiscontrol.entity.enums.TipoBeneficio;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.BeneficioEstudianteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service @Transactional(readOnly=true)
public class BeneficioCalculoService {
    public record CalculoBeneficio(BigDecimal montoBase,BigDecimal montoDescuento,BigDecimal montoTotal,
            String beneficioNombre,Long idBeneficio,Long beneficioVersion) {}
    private final BeneficioEstudianteRepository beneficios;
    public BeneficioCalculoService(BeneficioEstudianteRepository beneficios) { this.beneficios=beneficios; }
    public CalculoBeneficio calcular(Long alumno,int anio,Long concepto,int mes,BigDecimal base) {
        base=base.setScale(2,RoundingMode.HALF_UP);
        var vigentes=beneficios.superpuestos(alumno,concepto,anio,mes,mes);
        if(vigentes.size()>1) throw new BusinessRuleException("BENEFICIOS_SUPERPUESTOS","Los beneficios del estudiante requieren revisión antes de emitir el cobro.");
        if(vigentes.isEmpty()) return new CalculoBeneficio(base,new BigDecimal("0.00"),base,null,null,null);
        var b=vigentes.getFirst();
        BigDecimal descuento=(b.getTipo()==TipoBeneficio.PORCENTAJE
                ? base.multiply(b.getValor()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP):b.getValor()).setScale(2,RoundingMode.HALF_UP);
        if(descuento.compareTo(base)>0) throw new BusinessRuleException("BENEFICIO_SUPERA_IMPORTE","El beneficio fijo supera el importe del cobro. Revisa su configuración.");
        return new CalculoBeneficio(base,descuento,base.subtract(descuento),b.getNombre(),b.getId(),b.getVersion());
    }
}
