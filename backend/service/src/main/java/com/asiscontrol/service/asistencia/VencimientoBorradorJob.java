// asistencia alumnos
package com.asiscontrol.service.asistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada minuto finaliza los borradores cuyo plazo de edición venció: lo marcado se conserva y quien
 * quedó sin marca recibe SIN_MARCACION. El plazo puede vencer a media mañana, por eso no basta un
 * job a medianoche. La zona es explícita y las lecturas también cierran lo vencido.
 */
@Component
public class VencimientoBorradorJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoBorradorJob.class);

    private final PlazoAsistenciaService plazoService;

    public VencimientoBorradorJob(PlazoAsistenciaService plazoService) {
        this.plazoService = plazoService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Lima")
    public void cerrarAsistenciasVencidas() {
        int cerradas = plazoService.cerrarVencidas();
        if (cerradas > 0) {
            log.info("Asistencias finalizadas al vencer su plazo: {}", cerradas);
        }
    }
}
