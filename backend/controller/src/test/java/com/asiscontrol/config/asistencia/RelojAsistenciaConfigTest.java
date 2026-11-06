package com.asiscontrol.config.asistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

class RelojAsistenciaConfigTest {

    /** 06-11-2026 a las 13:45 en Lima. */
    private final Clock real = Clock.fixed(
            Instant.parse("2026-11-06T18:45:00Z"), RelojAsistenciaConfig.ZONA_INSTITUCION);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void sinFechaUsaElRelojReal(String fecha) {
        assertThat(RelojAsistenciaConfig.crearReloj(fecha, real)).isSameAs(real);
    }

    @Test
    void cambiaElDiaYConservaLaHoraReal() {
        Clock simulado = RelojAsistenciaConfig.crearReloj(" 2027-03-01 ", real);

        assertThat(LocalDate.now(simulado)).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(LocalTime.now(simulado)).isEqualTo(LocalTime.of(13, 45));
        assertThat(simulado.getZone()).isEqualTo(RelojAsistenciaConfig.ZONA_INSTITUCION);
    }

    @Test
    void tambienAdmiteFechasPasadas() {
        Clock simulado = RelojAsistenciaConfig.crearReloj("2026-03-02", real);

        assertThat(LocalDate.now(simulado)).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01-03-2027", "2027-13-01", "mañana"})
    void rechazaUnaFechaInvalidaAlArrancar(String fecha) {
        assertThatThrownBy(() -> RelojAsistenciaConfig.crearReloj(fecha, real))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASISTENCIA_FECHA_SIMULADA")
                .hasMessageContaining(fecha);
    }
}
