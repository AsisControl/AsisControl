package com.asiscontrol.service;

import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.repository.TareaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class CierreAutomaticoTareasService {
    public static final int TAMANIO_LOTE = 100;
    private final TareaRepository tareas;
    private final AuditoriaService auditoria;

    public CierreAutomaticoTareasService(TareaRepository tareas, AuditoriaService auditoria) {
        this.tareas = tareas;
        this.auditoria = auditoria;
    }

    @Transactional
    public int cerrarVencidas(LocalDateTime ahora) {
        // El bloqueo evita que dos instancias cierren y auditen la misma tarea a la vez.
        var vencidas = tareas.buscarVencidasParaCerrar(ahora, PageRequest.of(0, TAMANIO_LOTE));
        for (var tarea : vencidas) {
            tarea.setEstadoPublicacion(EstadoPublicacionTarea.CERRADA);
            auditoria.registrar("CERRAR_TAREA_AUTOMATICAMENTE", ModuloAuditoria.TAREAS, "Tarea",
                    tarea.getId().toString(), ResultadoAuditoria.EXITOSO, "Fecha límite alcanzada",
                    "estado=PUBLICADA", "estado=CERRADA");
        }
        tareas.flush();
        return vencidas.size();
    }
}
