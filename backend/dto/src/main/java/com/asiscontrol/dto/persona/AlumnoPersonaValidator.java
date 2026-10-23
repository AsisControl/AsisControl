package com.asiscontrol.dto.persona;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/** Reglas de ficha secundaria reutilizables por API e importación. */
public class AlumnoPersonaValidator
        implements ConstraintValidator<ValidAlumnoPersona, PersonaDtos.PersonaRequest> {

    private static final int EDAD_MINIMA = 10;
    private static final int EDAD_MAXIMA = 20;
    private static final ZoneId ZONA_INSTITUCION = ZoneId.of("America/Lima");

    @Override
    public boolean isValid(PersonaDtos.PersonaRequest persona, ConstraintValidatorContext context) {
        if (persona == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean validNames = validateNames(persona, context);
        boolean validDocument = validateDocument(persona, context);
        boolean validBirthDate = validateBirthDate(persona.fechaNacimiento(), context);
        return validNames && validDocument && validBirthDate;
    }

    private boolean validateNames(
            PersonaDtos.PersonaRequest persona, ConstraintValidatorContext context) {
        String[] names = {persona.nombres(), persona.apellidoPaterno(), persona.apellidoMaterno()};
        String[] fields = {"nombres", "apellidoPaterno", "apellidoMaterno"};
        boolean valid = true;
        for (int index = 0; index < names.length; index++) {
            String name = names[index] == null ? "" : names[index].trim();
            if (!name.isEmpty() && !name.matches(".*\\p{L}.*")) {
                addViolation(
                        context,
                        fields[index],
                        "El nombre o apellido debe contener letras; "
                                + "no solo espacios, guiones o apóstrofos");
                valid = false;
            }
            if (index < 2 && !name.isEmpty() && name.length() < 2) {
                addViolation(
                        context,
                        fields[index],
                        "Escribe al menos 2 caracteres sin contar espacios al inicio o al final");
                valid = false;
            }
        }
        return valid;
    }

    private boolean validateDocument(
            PersonaDtos.PersonaRequest persona, ConstraintValidatorContext context) {
        String number = persona.numeroDocumento() == null ? "" : persona.numeroDocumento().trim();
        if (persona.tipoDocumento() == null) {
            if (number.isEmpty()) {
                return true;
            }
            addViolation(context, "tipoDocumento", "Selecciona el tipo del documento informado");
            return false;
        }
        String pattern =
                switch (persona.tipoDocumento()) {
                    case DNI -> "[0-9]{8}";
                    case CARNET_EXTRANJERIA -> "[0-9]{9}";
                    case PASAPORTE -> "[A-Za-z0-9]{8,12}";
                };
        if (number.matches(pattern)) {
            return true;
        }
        String message =
                switch (persona.tipoDocumento()) {
                    case DNI -> "El DNI debe tener exactamente 8 dígitos";
                    case CARNET_EXTRANJERIA ->
                            "El carné de extranjería debe tener exactamente 9 dígitos";
                    case PASAPORTE ->
                            "El pasaporte debe tener de 8 a 12 letras o números, "
                                    + "sin espacios ni guiones";
                };
        addViolation(context, "numeroDocumento", message);
        return false;
    }

    private boolean validateBirthDate(LocalDate birthDate, ConstraintValidatorContext context) {
        if (birthDate == null) {
            return true; // La obligatoriedad la valida @NotNull en PersonaRequest.
        }
        LocalDate today =
                LocalDate.now(context.getClockProvider().getClock().withZone(ZONA_INSTITUCION));
        if (birthDate.isAfter(today)) {
            addViolation(context, "fechaNacimiento", "La fecha de nacimiento no puede ser futura");
            return false;
        }
        int age = Period.between(birthDate, today).getYears();
        if (age < EDAD_MINIMA || age > EDAD_MAXIMA) {
            addViolation(
                    context,
                    "fechaNacimiento",
                    "Para esta ficha de secundaria, el estudiante debe tener "
                            + "entre 10 y 20 años cumplidos");
            return false;
        }
        return true;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
