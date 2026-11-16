package com.asiscontrol.config;

import com.asiscontrol.entity.ConceptoCobro;
import com.asiscontrol.entity.enums.PeriodicidadCobro;
import com.asiscontrol.repository.ConceptoCobroRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Completa únicamente el concepto base en perfiles sin Flyway; nunca inventa tarifas. */
@Component
public class CatalogoPagosInitializer {
    private final ConceptoCobroRepository conceptos;
    public CatalogoPagosInitializer(ConceptoCobroRepository conceptos){this.conceptos=conceptos;}
    @EventListener(ApplicationReadyEvent.class) @Transactional
    public void preparar(){if(conceptos.findByCodigoIgnoreCase("PENSION").isPresent())return;
        var c=new ConceptoCobro();c.setCodigo("PENSION");c.setNombre("Pensión");c.setPeriodicidad(PeriodicidadCobro.MENSUAL);c.setEsPension(true);c.setRequiereAceptacion(false);conceptos.save(c);}
}
