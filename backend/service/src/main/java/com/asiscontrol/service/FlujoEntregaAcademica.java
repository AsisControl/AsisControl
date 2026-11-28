package com.asiscontrol.service;

import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.TareaRepository;
import org.springframework.stereotype.Component;

@Component
class FlujoEntregaAcademica {
    private final TareaRepository tareas;

    FlujoEntregaAcademica(TareaRepository tareas) {
        this.tareas = tareas;
    }

    Long tareaVinculada(Evaluacion evaluacion) {
        var tarea = actividadVinculada(evaluacion);
        return tarea == null ? null : tarea.getId();
    }

    Tarea actividadVinculada(Evaluacion evaluacion) {
        return tareas.findByEvaluacionAcademicaId(evaluacion.getId()).filter(Tarea::isActivo).orElse(null);
    }

    void verificar(Evaluacion evaluacion, Long tareaSolicitada) {
        var tarea = tareaVinculada(evaluacion);
        if (tareaSolicitada != null && !tareaSolicitada.equals(tarea)) {
            throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE", "La evaluación no pertenece a esta tarea.");
        }
        if (tareaSolicitada == null && (tarea != null || evaluacion.isEntregaPorConfigurar())) {
            throw new BusinessRuleException("GESTIONAR_DESDE_TAREAS", "Gestiona la actividad desde Tareas o cancela la configuración pendiente.");
        }
    }
}
