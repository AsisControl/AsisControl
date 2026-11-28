package com.asiscontrol.service;


import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.CalificacionRepository;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import com.asiscontrol.repository.MatriculaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CalificacionServiceTest {
    @Mock
    private FlujoEntregaAcademica flujoEntrega;

    @Mock
    private CalificacionRepository calificacionRepository;
    @Mock
    private EvaluacionRepository evaluacionRepository;
    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private CompetenciaRepository competenciaRepository;
    @Mock
    private MatriculaRepository matriculaRepository;
    @Mock
    private ModuloAccessGuard accessGuard;

    @InjectMocks
    private CalificacionService calificacionService;

    @Test
    void rechazaCombinacionesDuplicadasDentroDelMismoLote() {
        CalificacionDto.Request primera = new CalificacionDto.Request(
                1L, 2L, 3L, NivelLogro.A, null
        );
        CalificacionDto.Request repetida = new CalificacionDto.Request(
                1L, 2L, 3L, NivelLogro.AD, "correccion"
        );

        assertThatThrownBy(() -> calificacionService.registrarLote(
                new CalificacionDto.CargaLoteRequest(List.of(primera, repetida))
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("mas de una calificacion");
    }
}
