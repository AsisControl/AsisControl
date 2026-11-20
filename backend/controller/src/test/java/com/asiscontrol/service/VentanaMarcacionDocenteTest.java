package com.asiscontrol.service;

import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class VentanaMarcacionDocenteTest {
    private final LocalDate fecha = LocalDate.of(2026, 11, 19);
    private SesionClase sesion(int inicio, int fin) {
        var s = new SesionClase(); s.setFecha(fecha);
        s.setHoraInicio(LocalTime.of(inicio, 0)); s.setHoraFin(LocalTime.of(fin, 0)); return s;
    }
    @Test void aplicaLosLimitesYConservaLaUltimaClaseAunqueHayaHuecos() {
        var ventana = VentanaMarcacionDocente.de(List.of(sesion(15, 17), sesion(9, 11)));
        assertThat(ventana.apertura()).isEqualTo(fecha.atTime(8, 0));
        assertThat(ventana.limitePuntualidad()).isEqualTo(fecha.atTime(8, 45));
        assertThat(ventana.cierre()).isEqualTo(fecha.atTime(17, 0));
        assertThatThrownBy(() -> ventana.clasificar(fecha.atTime(7, 59, 59))).hasMessageContaining("una hora antes");
        assertThat(ventana.clasificar(fecha.atTime(8, 0))).isEqualTo(EstadoAsistencia.PRESENTE);
        assertThat(ventana.clasificar(fecha.atTime(8, 45))).isEqualTo(EstadoAsistencia.PRESENTE);
        assertThat(ventana.clasificar(fecha.atTime(8, 45, 1))).isEqualTo(EstadoAsistencia.TARDANZA);
        assertThat(ventana.clasificar(fecha.atTime(16, 59))).isEqualTo(EstadoAsistencia.TARDANZA);
        assertThatThrownBy(() -> ventana.clasificar(fecha.atTime(17, 0))).hasMessageContaining("jornada terminó");
        assertThatThrownBy(() -> VentanaMarcacionDocente.de(List.of())).hasMessageContaining("No tiene clases");
    }
}
