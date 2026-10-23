package com.asiscontrol.service;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.enums.IndicadorRiesgo;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.AlertaRiesgoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.CriterioRiesgoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiesgoServiceTest {

    @Mock
    private CriterioRiesgoRepository criterioRiesgoRepository;
    @Mock
    private AlertaRiesgoRepository alertaRiesgoRepository;
    @Mock
    private AlumnoRepository alumnoRepository;

    @Mock
    private AuditoriaService auditoria;

    @InjectMocks
    private RiesgoService riesgoService;

    @Test
    void rechazaElMismoIndicadorMasDeUnaVezEnLaEvaluacion() {
        Alumno alumno = new Alumno();
        alumno.setId(5L);
        when(alumnoRepository.findByIdAndActivoTrue(5L)).thenReturn(Optional.of(alumno));
        RiesgoDto.IndicadorValor primero = new RiesgoDto.IndicadorValor(
                IndicadorRiesgo.INASISTENCIAS,
                new BigDecimal("4")
        );
        RiesgoDto.IndicadorValor repetido = new RiesgoDto.IndicadorValor(
                IndicadorRiesgo.INASISTENCIAS,
                new BigDecimal("5")
        );

        assertThatThrownBy(() -> riesgoService.evaluar(
                new RiesgoDto.EvaluacionRequest(5L, List.of(primero, repetido))
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("aparece mas de una vez");
    }
}
