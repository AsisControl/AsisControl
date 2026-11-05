package com.asiscontrol.dto.persona;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ASISCTRL-62: formato del documento según su tipo (DNI de 8 dígitos). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DocumentoPersonaValidator.class)
public @interface ValidDocumentoPersona {
    String message() default "El documento no tiene un formato valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
