// asistencia alumnos
package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.AsistenciaDocenteRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import com.asiscontrol.service.asistencia.PlazoAsistenciaService;
import com.asiscontrol.service.asistencia.VencimientoBorradorJob;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** El plazo de edición termina con la siguiente clase del día o a medianoche en Lima, no en UTC. */
class VencimientoBorradorRelojTest {

    private PlazoAsistenciaService plazoEn(String instante) {
        return new PlazoAsistenciaService(
                mock(SesionClaseRepository.class),
                mock(AsistenciaAlumnoRepository.class),
                mock(MatriculaService.class),
                mock(AuditoriaService.class),
                mock(AsistenciaDocenteRepository.class),
                Clock.fixed(Instant.parse(instante), ZoneId.of("America/Lima")));
    }

    private SesionClase sesion(LocalDate fecha, int hora) {
        var sesion = new SesionClase();
        sesion.setFecha(fecha);
        sesion.setHoraInicio(LocalTime.of(hora, 0));
        return sesion;
    }

    @Test
    void aLasNueveDeLaNocheEnLimaLaClaseDeHoySigueAbiertaAunqueEnUtcSeaManana() {
        var clase = sesion(LocalDate.of(2026, 11, 4), 8);
        var limite = PlazoAsistenciaService.limite(clase, List.of(clase));

        assertThat(limite).isEqualTo(LocalDateTime.of(2026, 11, 5, 0, 0));
        assertThat(plazoEn("2026-11-05T02:00:00Z").abierta(limite)).isTrue();
    }

    @Test
    void pasadaLaMedianocheEnLimaSeCierra() {
        var clase = sesion(LocalDate.of(2026, 11, 4), 8);

        assertThat(plazoEn("2026-11-05T05:05:00Z").abierta(PlazoAsistenciaService.limite(clase, List.of(clase))))
                .isFalse();
    }

    @Test
    void laSiguienteClaseDelMismoDiaAdelantaElLimite() {
        var primera = sesion(LocalDate.of(2026, 11, 4), 8);
        var segunda = sesion(LocalDate.of(2026, 11, 4), 11);
        var manana = sesion(LocalDate.of(2026, 11, 5), 7);
        var curso = List.of(primera, segunda, manana);

        assertThat(PlazoAsistenciaService.limite(primera, curso)).isEqualTo(LocalDateTime.of(2026, 11, 4, 11, 0));
        assertThat(PlazoAsistenciaService.limite(segunda, curso)).isEqualTo(LocalDateTime.of(2026, 11, 5, 0, 0));
        // 10:59 en Lima: la primera sigue abierta; a las 11:00 se cierra.
        assertThat(plazoEn("2026-11-04T15:59:00Z").abierta(PlazoAsistenciaService.limite(primera, curso))).isTrue();
        assertThat(plazoEn("2026-11-04T16:00:00Z").abierta(PlazoAsistenciaService.limite(primera, curso))).isFalse();
    }

    @Test
    void elJobRevisaCadaMinutoEnLaZonaDeLaInstitucion() throws Exception {
        Scheduled programacion = VencimientoBorradorJob.class
                .getMethod("cerrarAsistenciasVencidas")
                .getAnnotation(Scheduled.class);

        assertThat(programacion.zone()).isEqualTo("America/Lima");
        assertThat(programacion.cron()).isEqualTo("0 * * * * *");
    }
}
