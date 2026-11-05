// asistencia alumnos
package com.asiscontrol.service.asistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Al terminar el día en Lima, los borradores no finalizados se eliminan y su sesión vuelve a
 * PENDIENTE (R08), sin generar inasistencias (R09). La zona es explícita: no depende de la del
 * servidor. Si el job no corre, la lectura igual trata el borrador vencido como PENDIENTE.
 */
@Component
public class VencimientoBorradorJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoBorradorJob.class);

    private final AsistenciaSesionService asistenciaService;

    public VencimientoBorradorJob(AsistenciaSesionService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Lima")
    public void vencerBorradores() {
        int vencidos = asistenciaService.vencerBorradores();
        if (vencidos > 0) {
            log.info("Borradores de asistencia vencidos: {}", vencidos);
        }
    }
}
