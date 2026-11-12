package com.asiscontrol.support;

import com.asiscontrol.entity.ConceptoCobro;
import com.asiscontrol.entity.TarifaPension;
import com.asiscontrol.entity.enums.PeriodicidadCobro;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

/** Configuración financiera explícita para escenarios de matrícula anteriores al plan anual. */
public final class TarifasTestSupport {
    private TarifasTestSupport() {}

    public static void configurar(EntityManager em, int anio, int grado) {
        var conceptos = em.createQuery("from ConceptoCobro c where c.codigo='PENSION'", ConceptoCobro.class).getResultList();
        ConceptoCobro concepto;
        if (conceptos.isEmpty()) {
            concepto = new ConceptoCobro();
            concepto.setCodigo("PENSION"); concepto.setNombre("Pensión");
            concepto.setPeriodicidad(PeriodicidadCobro.MENSUAL); concepto.setEsPension(true);
            concepto.setRequiereAceptacion(false); em.persist(concepto); em.flush();
        } else concepto = conceptos.getFirst();
        var tarifas = em.createQuery("from TarifaPension t where t.concepto.id=:concepto and t.anioAcademico=:anio and t.grado=:grado", TarifaPension.class)
                .setParameter("concepto", concepto.getId()).setParameter("anio", anio).setParameter("grado", grado).getResultList();
        if (tarifas.isEmpty()) {
            var tarifa = new TarifaPension(); tarifa.setConcepto(concepto);
            tarifa.setAnioAcademico(anio); tarifa.setGrado(grado);
            tarifa.setMontoMensual(new BigDecimal("350.00")); em.persist(tarifa); em.flush();
        }
    }
}
