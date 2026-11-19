package com.asiscontrol.service;

import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.MomentoClase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardDocenteMomentoTest {

    @ParameterizedTest
    @CsvSource({
            "07:59, PROXIMA",
            "08:00, EN_CURSO",
            "08:44, EN_CURSO",
            "08:45, FINALIZADA",
            "13:00, FINALIZADA"
    })
    void clasificaLaClaseSegunLaHoraDelColegio(String ahora, MomentoClase esperado) {
        assertThat(DashboardDocenteService.momento(LocalTime.of(8, 0), LocalTime.of(8, 45), LocalTime.parse(ahora)))
                .isEqualTo(esperado);
    }
}
