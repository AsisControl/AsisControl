// asistencia alumnos
package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.asistencia.CorreccionAsistenciaDetalleRepository;
import com.asiscontrol.repository.asistencia.CorreccionAsistenciaRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import com.asiscontrol.service.asistencia.AsistenciaSesionService;
import com.asiscontrol.service.asistencia.CalendarioSesionService;
import com.asiscontrol.service.asistencia.VencimientoBorradorJob;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** El día de la asistencia termina a medianoche en Lima, no a medianoche UTC. */
class VencimientoBorradorRelojTest {

    private final SesionClaseRepository sesiones = mock(SesionClaseRepository.class);

    private AsistenciaSesionService servicioEn(String instante) {
        return new AsistenciaSesionService(
                sesiones,
                mock(AsistenciaAlumnoRepository.class),
                mock(CorreccionAsistenciaRepository.class),
                mock(CorreccionAsistenciaDetalleRepository.class),
                mock(MatriculaService.class),
                mock(AuditoriaService.class),
                mock(ModuloAccessGuard.class),
                mock(CalendarioSesionService.class),
                Clock.fixed(Instant.parse(instante), ZoneId.of("America/Lima")));
    }

    @Test
    void aLasNueveDeLaNocheEnLimaElBorradorDeHoySigueVigenteAunqueEnUtcSeaManana() {
        servicioEn("2026-11-05T02:00:00Z").vencerBorradores();

        verify(sesiones).findAllByEstadoAndFechaBefore(
                EstadoSesionClase.BORRADOR, LocalDate.of(2026, 11, 4));
    }

    @Test
    void pasadaLaMedianocheEnLimaVenceElBorradorDelDiaAnterior() {
        servicioEn("2026-11-05T05:05:00Z").vencerBorradores();

        verify(sesiones).findAllByEstadoAndFechaBefore(
                EstadoSesionClase.BORRADOR, LocalDate.of(2026, 11, 5));
    }

    @Test
    void elJobDeclaraLaZonaDeLaInstitucion() throws Exception {
        Scheduled programacion = VencimientoBorradorJob.class
                .getMethod("vencerBorradores")
                .getAnnotation(Scheduled.class);

        assertThat(programacion.zone()).isEqualTo("America/Lima");
        assertThat(programacion.cron()).isEqualTo("0 5 0 * * *");
    }
}
