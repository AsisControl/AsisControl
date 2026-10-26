package com.asiscontrol.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldValidationError> fieldErrors,
        List<?> details
) {

    public ApiError(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String correlationId,
            List<FieldValidationError> fieldErrors) {
        this(timestamp, status, code, message, path, correlationId, fieldErrors, List.of());
    }
}
