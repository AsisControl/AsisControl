package com.asiscontrol.service;

import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.exception.BusinessRuleException;

import java.time.LocalDateTime;
import java.util.List;

/** Regla institucional acordada: apertura -60 min, puntualidad -15 min y cierre al final del día. */
public record VentanaMarcacionDocente(LocalDateTime apertura, LocalDateTime limitePuntualidad,
                                      LocalDateTime cierre) {
    public static VentanaMarcacionDocente de(List<SesionClase> sesiones) {
        if (sesiones.isEmpty()) {
            throw new BusinessRuleException("JORNADA_NO_PROGRAMADA",
                    "No tiene clases programadas para esta fecha; solicite revisión administrativa");
        }
        LocalDateTime inicio = sesiones.stream().map(s -> s.getFecha().atTime(s.getHoraInicio()))
                .min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime fin = sesiones.stream().map(s -> s.getFecha().atTime(s.getHoraFin()))
                .max(LocalDateTime::compareTo).orElseThrow();
        return new VentanaMarcacionDocente(inicio.minusHours(1), inicio.minusMinutes(15), fin);
    }

    public EstadoAsistencia clasificar(LocalDateTime horaServidor) {
        if (horaServidor.isBefore(apertura)) {
            throw new BusinessRuleException("MARCACION_NO_ABIERTA",
                    "La marcación se habilita una hora antes de su primera clase");
        }
        // El cierre es exclusivo: al terminar la última clase ya no se acepta QR.
        if (!horaServidor.isBefore(cierre)) {
            throw new BusinessRuleException("MARCACION_CERRADA",
                    "La jornada terminó; solicite un registro administrativo con motivo");
        }
        return horaServidor.isAfter(limitePuntualidad) ? EstadoAsistencia.TARDANZA : EstadoAsistencia.PRESENTE;
    }
}
