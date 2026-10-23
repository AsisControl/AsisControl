package com.asiscontrol.exception;

import com.asiscontrol.service.AuditoriaService;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {
    private AuditoriaService audit;
    private GlobalExceptionHandler handler;
    private final MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/prueba");
    @BeforeEach void setup() { audit=mock(AuditoriaService.class); handler=new GlobalExceptionHandler(audit); }

    @Test void mapeaErroresDeNegocioAPEstadosHttpConsistentes() {
        assertThat(handler.handleNotFound(new ResourceNotFoundException("NO_EXISTE","No existe"),request).getStatusCode().value()).isEqualTo(404);
        assertThat(handler.handleConflict(new ConflictException("DUPLICADO","Repetido"),request).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.handleBusinessRule(new BusinessRuleException("REGLA","Invalido"),request).getStatusCode().value()).isEqualTo(422);
        assertThat(handler.handleAuthentication(new BadCredentialsException("interno"),request).getStatusCode().value()).isEqualTo(401);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("interno"),request).getStatusCode().value()).isEqualTo(403);
        assertThat(handler.handleConcurrentChange(new OptimisticLockingFailureException("version"),request).getStatusCode().value()).isEqualTo(409);
    }

    @Test void ocultaMensajesTecnicosYSoportaFalloDelRegistroDeError() {
        var integrity=handler.handleIntegrity(new DataIntegrityViolationException("SQL y datos privados"),request);
        assertThat(integrity.getBody().message()).doesNotContain("SQL","privados");
        assertThat(handler.handleInvalidFormat(new IllegalArgumentException("valor"),request).getStatusCode().value()).isEqualTo(400);
        doThrow(new IllegalStateException("base indisponible")).when(audit).registrarError(any(),any(),any(),any());
        var error=handler.handleUnexpected(new RuntimeException("password=secreto"),request);
        assertThat(error.getStatusCode().value()).isEqualTo(500);
        assertThat(error.getBody().message()).doesNotContain("password","secreto");
    }

    @Test void devuelveLosCamposInvalidosSinPublicarLosValores() throws Exception {
        var target=new com.asiscontrol.dto.auth.AuthDtos.LoginRequest("","");
        var result=new BeanPropertyBindingResult(target,"loginRequest");
        result.addError(new FieldError("loginRequest","identifier","Es obligatorio"));
        result.addError(new ObjectError("loginRequest","Datos invalidos"));
        var parameter=new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("sample",Object.class),0);
        var response=handler.handleValidation(new MethodArgumentNotValidException(parameter,result),request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().fieldErrors()).hasSize(2);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var violations=factory.getValidator().validate(target);
            var constraint=handler.handleConstraintViolation(new ConstraintViolationException(violations),request);
            assertThat(constraint.getBody().fieldErrors()).hasSize(2);
        }
    }
    private void sample(Object request) {}
}
