package com.asiscontrol.exception;

import com.asiscontrol.entity.enums.SeveridadError;
import com.asiscontrol.service.AuditoriaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditoriaService auditoria;

    public GlobalExceptionHandler(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    private void registrarTecnico(SeveridadError severidad, String componente, String mensaje) {
        try {
            auditoria.registrarError(severidad, componente, mensaje, null);
        } catch (Exception registroFallido) {
            LOGGER.error(
                    "No se pudo persistir un error técnico ({})",
                    registroFallido.getClass().getSimpleName());
        }
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentChange(
            org.springframework.dao.OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "VERSION_DESACTUALIZADA",
                "El registro cambió; actualice los datos antes de continuar",
                request,
                List.of());
    }

    @ExceptionHandler({
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleInvalidFormat(
            Exception exception, HttpServletRequest request) {
        registrarTecnico(
                SeveridadError.ADVERTENCIA,
                "FormatoSolicitud",
                "La solicitud contiene un formato o valor no admitido");
        List<FieldValidationError> fields = invalidFormatFields(exception);
        return build(
                HttpStatus.BAD_REQUEST,
                "FORMATO_INVALIDO",
                fields.isEmpty()
                        ? "No se pudo leer la solicitud. Vuelve a enviar el formulario completo"
                        : fields.getFirst().message(),
                request,
                fields);
    }

    private List<FieldValidationError> invalidFormatFields(Exception exception) {
        String fieldPath = extractFieldPath(exception);
        if (fieldPath == null || fieldPath.isBlank()) {
            return List.of();
        }
        String fieldName = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
        String message =
                switch (fieldName) {
                    case "fechaNacimiento" ->
                            "Ingresa una fecha de nacimiento válida con día, mes y año completos";
                    case "tipoDocumento" -> "Selecciona DNI, carné de extranjería o pasaporte";
                    case "sexo" ->
                            "Selecciona una de las opciones de sexo disponibles "
                                    + "o deja el campo sin registrar";
                    default ->
                            "El valor enviado no tiene el formato esperado. "
                                    + "Revisa el campo indicado y vuelve a guardar";
                };
        return List.of(new FieldValidationError(fieldPath, message));
    }

    /** Identifica el campo sin devolver mensajes internos ni valores de la solicitud. */
    private String extractFieldPath(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            String fieldPath = extractMappingPath(cause);
            if (fieldPath != null) {
                return fieldPath;
            }
            cause = cause.getCause();
        }
        return null;
    }

    private String extractMappingPath(Throwable cause) {
        if (cause instanceof tools.jackson.core.JacksonException mapping
                && !mapping.getPath().isEmpty()) {
            return mapping.getPath().stream()
                    .map(tools.jackson.core.JacksonException.Reference::getPropertyName)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining("."));
        }
        if (cause instanceof com.fasterxml.jackson.databind.JsonMappingException mapping
                && !mapping.getPath().isEmpty()) {
            return mapping.getPath().stream()
                    .map(
                            com.fasterxml.jackson.databind.JsonMappingException.Reference
                                    ::getFieldName)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining("."));
        }
        if (cause
                instanceof
                org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                        mismatch) {
            return mismatch.getName();
        }
        return null;
    }

    private List<FieldValidationError> duplicateFieldErrors(String code) {
        return switch (code) {
            case "DOCUMENTO_DUPLICADO" ->
                    List.of(
                            new FieldValidationError(
                                    "persona.numeroDocumento",
                                    "Este documento ya está registrado. Revisa la ficha"
                                            + " existente"));
            case "CORREO_DUPLICADO" ->
                    List.of(
                            new FieldValidationError(
                                    "persona.correo",
                                    "Este correo ya está asociado a otra persona. "
                                            + "Comprueba el correo de contacto"));
            default -> List.of();
        };
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            ConflictException exception, HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                exception.getCode(),
                exception.getMessage(),
                request,
                duplicateFieldErrors(exception.getCode()));
    }

    @ExceptionHandler(FormFieldException.class)
    public ResponseEntity<ApiError> handleFormField(
            FormFieldException exception, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "DATOS_INVALIDOS",
                exception.getMessage(),
                request,
                List.of(new FieldValidationError(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(
            BusinessRuleException exception, HttpServletRequest request) {
        return build(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getCode(),
                exception.getMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        registrarTecnico(
                SeveridadError.ADVERTENCIA,
                "ValidacionSolicitud",
                "La solicitud no cumple las restricciones de formato o tamaño");
        List<FieldValidationError> errors =
                exception.getBindingResult().getAllErrors().stream()
                        .map(
                                error -> {
                                    String field =
                                            error instanceof FieldError fieldError
                                                    ? fieldError.getField()
                                                    : error.getObjectName();
                                    return new FieldValidationError(
                                            field, error.getDefaultMessage());
                                })
                        .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "DATOS_INVALIDOS",
                "La solicitud contiene datos invalidos",
                request,
                errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        registrarTecnico(
                SeveridadError.ADVERTENCIA,
                "ValidacionSolicitud",
                "La solicitud no cumple las restricciones de formato o tamaño");
        List<FieldValidationError> errors =
                exception.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new FieldValidationError(
                                                violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                        .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "DATOS_INVALIDOS",
                "La solicitud contiene datos invalidos",
                request,
                errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        registrarTecnico(
                SeveridadError.ADVERTENCIA,
                "Persistencia",
                "La operación incumple una restricción de integridad");
        LOGGER.warn("Restricción de integridad ({})", exception.getClass().getSimpleName());
        String constraint =
                java.util.Objects.toString(exception.getMostSpecificCause().getMessage(), "")
                        .toLowerCase(java.util.Locale.ROOT);
        String duplicateCode = null;
        String duplicateMessage = null;
        if (constraint.contains("uk_persona_tipo_numero_documento")) {
            duplicateCode = "DOCUMENTO_DUPLICADO";
            duplicateMessage = "Este documento ya está registrado";
        } else if (constraint.contains("uk_persona_correo")) {
            duplicateCode = "CORREO_DUPLICADO";
            duplicateMessage = "Este correo ya está asociado a otra persona";
        }
        if (duplicateCode != null) {
            return build(
                    HttpStatus.CONFLICT,
                    duplicateCode,
                    duplicateMessage,
                    request,
                    duplicateFieldErrors(duplicateCode));
        }
        return build(
                HttpStatus.CONFLICT,
                "CONFLICTO_DE_DATOS",
                "La operacion entra en conflicto con informacion existente",
                request,
                List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "AUTENTICACION_REQUERIDA",
                "Las credenciales no son validas o la sesion expiro",
                request,
                List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        registrarTecnico(
                SeveridadError.ADVERTENCIA,
                "Autorizacion",
                "Se rechazó una operación por permisos insuficientes");
        return build(
                HttpStatus.FORBIDDEN,
                "ACCESO_DENEGADO",
                "No cuenta con permisos para realizar esta operacion",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        registrarTecnico(
                SeveridadError.ERROR,
                exception.getClass().getSimpleName(),
                "Ocurrió un error interno al procesar la operación");
        LOGGER.error("Error no controlado ({})", exception.getClass().getSimpleName());
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ERROR_INTERNO",
                "No fue posible completar la operacion",
                request,
                List.of());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldValidationError> fieldErrors) {
        ApiError error =
                new ApiError(
                        Instant.now(),
                        status.value(),
                        code,
                        message,
                        request.getRequestURI(),
                        MDC.get("correlationId"),
                        fieldErrors);
        return ResponseEntity.status(status).body(error);
    }
}
