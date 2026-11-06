package com.asiscontrol.dto.persona;

import com.asiscontrol.entity.enums.TipoDocumento;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ASISCTRL-62: replica en el servidor la regla de PersonFields. El DNI debe tener exactamente
 * 8 dígitos; carné de extranjería y pasaporte conservan el patrón general del campo
 * ([A-Za-z0-9-]{6,20}). La ficha de alumno aplica reglas más estrictas en AlumnoPersonaValidator.
 */
public class DocumentoPersonaValidator
        implements ConstraintValidator<ValidDocumentoPersona, PersonaDtos.PersonaRequest> {

    @Override
    public boolean isValid(PersonaDtos.PersonaRequest persona, ConstraintValidatorContext context) {
        if (persona == null
                || persona.tipoDocumento() != TipoDocumento.DNI
                || persona.numeroDocumento() == null
                || persona.numeroDocumento().isBlank()) {
            return true; // Tipo y número obligatorios los validan @NotNull y @NotBlank.
        }
        if (persona.numeroDocumento().matches("[0-9]{8}")) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("El DNI debe tener exactamente 8 dígitos")
                .addPropertyNode("numeroDocumento")
                .addConstraintViolation();
        return false;
    }
}
