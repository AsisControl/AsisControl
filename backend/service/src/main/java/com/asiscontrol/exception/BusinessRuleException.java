package com.asiscontrol.exception;

public class BusinessRuleException extends RuntimeException {

    private final String code;
    /** Campo del formulario al que corresponde el error; null si es de toda la operación. */
    private final String field;

    public BusinessRuleException(String code, String message) {
        this(code, message, null);
    }

    public BusinessRuleException(String code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
