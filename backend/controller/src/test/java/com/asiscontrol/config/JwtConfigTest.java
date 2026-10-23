package com.asiscontrol.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtConfigTest {
    @Test void exigeClaveEstableYSuficientementeLargaFueraDelModoEfimero() {
        var config=new JwtConfig();
        assertThatThrownBy(()->config.jwtSecretKey("",false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->config.jwtSecretKey("corta",false)).isInstanceOf(IllegalStateException.class);
        assertThat(config.jwtSecretKey("01234567890123456789012345678901",false).getEncoded()).hasSize(32);
    }
    @Test void generaClavesDistintasSoloCuandoSeHabilitaDesarrolloEfimero() {
        var config=new JwtConfig();
        var first=config.jwtSecretKey(null,true); var second=config.jwtSecretKey("",true);
        assertThat(first.getEncoded()).hasSize(32).isNotEqualTo(second.getEncoded());
    }
}
