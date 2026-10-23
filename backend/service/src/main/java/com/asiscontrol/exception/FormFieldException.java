package com.asiscontrol.exception;

/** Rechazo de negocio asociado a un campo, sin exponer SQL ni datos personales. */
public class FormFieldException extends RuntimeException {
    private final String field;

    public FormFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
