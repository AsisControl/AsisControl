package com.asiscontrol.service;
import com.asiscontrol.entity.enums.OperadorComparacion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
class RiesgoServiceTest {
    @ParameterizedTest
    @CsvSource({
        "MENOR_QUE,4,true", "MENOR_QUE,5,false", "MENOR_QUE,6,false",
        "MENOR_IGUAL,4,true", "MENOR_IGUAL,5,true", "MENOR_IGUAL,6,false",
        "MAYOR_QUE,4,false", "MAYOR_QUE,5,false", "MAYOR_QUE,6,true",
        "MAYOR_IGUAL,4,false", "MAYOR_IGUAL,5,true", "MAYOR_IGUAL,6,true",
        "IGUAL,4,false", "IGUAL,5,true", "IGUAL,6,false"
    })
    void comparaAntesEnYDespuesDelUmbral(OperadorComparacion operador, String valor, boolean esperado) {
        assertThat(CalculoRiesgoService.cumple(operador, new BigDecimal(valor), new BigDecimal("5.0000")))
                .isEqualTo(esperado);
    }
}
