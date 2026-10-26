package com.asiscontrol.exception;

import java.util.List;

public class ConflictException extends RuntimeException {

    private final String code;
    private final List<?> details;

    public ConflictException(String code, String message) {
        this(code, message, List.of());
    }

    public ConflictException(String code, String message, List<?> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public List<?> getDetails() {
        return details;
    }
}
