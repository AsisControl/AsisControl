package com.asiscontrol.exception;

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

    private final com.asiscontrol.service.AuditoriaService auditoria;

    public GlobalExceptionHandler(com.asiscontrol.service.AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    private void registrarTecnico(
            com.asiscontrol.entity.enums.SeveridadError severidad,
            String componente,
            String mensaje) {
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
                com.asiscontrol.entity.enums.SeveridadError.ADVERTENCIA,
                "FormatoSolicitud",
                "La solicitud contiene un formato o valor no admitido");
        return build(
                HttpStatus.BAD_REQUEST,
                "FORMATO_INVALIDO",
                "Revise el formato de las fechas, estados y valores enviados",
                request,
                List.of());
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
                List.of(),
                exception.getDetails());
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
                com.asiscontrol.entity.enums.SeveridadError.ADVERTENCIA,
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
                com.asiscontrol.entity.enums.SeveridadError.ADVERTENCIA,
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
                com.asiscontrol.entity.enums.SeveridadError.ADVERTENCIA,
                "Persistencia",
                "La operación incumple una restricción de integridad");
        LOGGER.warn("Restricción de integridad ({})", exception.getClass().getSimpleName());
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
                com.asiscontrol.entity.enums.SeveridadError.ADVERTENCIA,
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
                com.asiscontrol.entity.enums.SeveridadError.ERROR,
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
        return build(status, code, message, request, fieldErrors, List.of());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldValidationError> fieldErrors,
            List<?> details) {
        ApiError error =
                new ApiError(
                        Instant.now(),
                        status.value(),
                        code,
                        message,
                        request.getRequestURI(),
                        MDC.get("correlationId"),
                        fieldErrors,
                        details);
        return ResponseEntity.status(status).body(error);
    }
}
