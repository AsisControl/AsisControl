package com.asiscontrol.config;

import com.asiscontrol.service.CierreAutomaticoTareasService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "app.tareas.cierre-automatico.enabled", havingValue = "true", matchIfMissing = true)
public class CierreAutomaticoTareas {
    private final CierreAutomaticoTareasService service;

    public CierreAutomaticoTareas(CierreAutomaticoTareasService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${app.tareas.cierre-automatico.intervalo-ms:60000}")
    public void cerrarVencidas() {
        // También recupera las tareas que vencieron mientras el backend estaba apagado.
        LocalDateTime ahora = LocalDateTime.now();
        while (service.cerrarVencidas(ahora) == CierreAutomaticoTareasService.TAMANIO_LOTE) {
            // Cada lote se confirma en una transacción independiente.
        }
    }
}
