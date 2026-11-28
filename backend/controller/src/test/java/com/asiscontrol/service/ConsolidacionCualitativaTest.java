package com.asiscontrol.service;

import com.asiscontrol.dto.ReglaCalificacionDto.Componente;
import com.asiscontrol.dto.ReglaCalificacionDto.Muestra;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static com.asiscontrol.entity.enums.NivelLogro.*;
import static com.asiscontrol.entity.enums.TipoEvaluacion.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConsolidacionCualitativaTest {
    private final List<Componente> regla = List.of(
            componente(TRABAJO_ACADEMICO, "20"), componente(EVALUACION_ORAL, "20"), componente(EXAMEN, "60"));

    @Test void reproduceLosEjemplosDeLaPropuesta() {
        verificar(regla, "3.25", A, muestra(TRABAJO_ACADEMICO, B), muestra(TRABAJO_ACADEMICO, A),
                muestra(EXAMEN, A), muestra(EXAMEN, AD));
        verificar(regla, "2.10", B, muestra(TRABAJO_ACADEMICO, C), muestra(EVALUACION_ORAL, B),
                muestra(EXAMEN, C), muestra(EXAMEN, AD));
        verificar(regla, "2.50", A, muestra(TRABAJO_ACADEMICO, A), muestra(TRABAJO_ACADEMICO, B),
                muestra(EXAMEN, B), muestra(EXAMEN, A));
        verificar(regla, "2.20", B, muestra(TRABAJO_ACADEMICO, B), muestra(EVALUACION_ORAL, A),
                muestra(EXAMEN, A), muestra(EXAMEN, C));
        verificar(regla, "4", AD, muestra(EXAMEN, AD));
    }

    @Test void distingueMuestraVaciaDeEvaluacionRequeridaSinNivel() {
        var vacia = ReglaCalificacionService.calcularCualitativo(regla, List.of());
        assertThat(vacia.nivelConsolidado()).isNull();
        assertThat(vacia.componentesFaltantes()).isEmpty();
        var pendiente = ReglaCalificacionService.calcularCualitativo(regla,
                List.of(muestra(EXAMEN, AD), muestra(EXAMEN, null)));
        assertThat(pendiente.nivelConsolidado()).isNull();
        assertThat(pendiente.promedioPonderado()).isNull();
        assertThat(pendiente.componentesFaltantes()).containsExactly("EXAMEN");
    }

    @Test void comparaLosCortesAntesDeRedondear() {
        var corte = List.of(componente(EXAMEN, "50"), componente(PROYECTO, "50"));
        var debajo = List.of(componente(EXAMEN, "50.01"), componente(PROYECTO, "49.99"));
        verificar(corte, "1.50", B, muestra(EXAMEN, C), muestra(PROYECTO, B));
        verificar(corte, "2.50", A, muestra(EXAMEN, B), muestra(PROYECTO, A));
        verificar(corte, "3.50", AD, muestra(EXAMEN, A), muestra(PROYECTO, AD));
        verificar(debajo, "1.4999", C, muestra(EXAMEN, C), muestra(PROYECTO, B));
        verificar(debajo, "2.4999", B, muestra(EXAMEN, B), muestra(PROYECTO, A));
        verificar(debajo, "3.4999", A, muestra(EXAMEN, A), muestra(PROYECTO, AD));
    }

    private static Componente componente(TipoEvaluacion tipo, String peso) {
        return new Componente(tipo, tipo.name(), new BigDecimal(peso));
    }

    private static Muestra muestra(TipoEvaluacion tipo, NivelLogro nivel) {
        return new Muestra(tipo, nivel);
    }

    private static void verificar(List<Componente> componentes, String promedio, NivelLogro nivel, Muestra... muestras) {
        var resultado = ReglaCalificacionService.calcularCualitativo(componentes, List.of(muestras));
        assertThat(resultado.promedioPonderado()).isEqualByComparingTo(promedio);
        assertThat(resultado.nivelConsolidado()).isEqualTo(nivel);
    }
}
