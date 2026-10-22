package com.asiscontrol.dto.persona;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AlumnoPersonaValidator.class)
public @interface ValidAlumnoPersona {
    String message() default "Revisa los datos de la ficha";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
