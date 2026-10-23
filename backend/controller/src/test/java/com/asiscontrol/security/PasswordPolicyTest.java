package com.asiscontrol.security;

import com.asiscontrol.exception.BusinessRuleException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"Ab1","solominusculas123","SOLOMAYUSCULAS123","SinNumeros","123456789","Con espacio123","Con\tTab123","Con\u00a0Espacio123"})
    void rechazaClavesQueNoCumplenLaPolitica(String password) {
        assertThatThrownBy(()->PasswordPolicy.validate(password,"usuario"))
                .isInstanceOf(BusinessRuleException.class);
    }
    @Test void limitaCaracteresBytesUtf8YNombreDeUsuario() {
        assertThatThrownBy(()->PasswordPolicy.validate("Aa1"+"x".repeat(63),"usuario")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->PasswordPolicy.validate("Aa1"+"ñ".repeat(40),"usuario")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->PasswordPolicy.validate("Usuario123","usuario123")).isInstanceOf(BusinessRuleException.class);
        assertThatCode(()->PasswordPolicy.validate("ClaveValida123!","usuario")).doesNotThrowAnyException();
    }
}
