package com.asiscontrol.exception;

import com.asiscontrol.service.AuditoriaService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Los errores de ficha conservan el contrato compartido de otros módulos. */
class FichaErrorContractTest {
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(mock(AuditoriaService.class));

    @Test void documentoDuplicadoConservaCampoYDetalles() {
        var details = List.of(Map.of("motivo", "Duplicado"));
        var response = handler.handleConflict(
                new ConflictException("DOCUMENTO_DUPLICADO", "Documento registrado", details),
                new MockHttpServletRequest("POST", "/api/alumnos"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().getFirst().field())
                .isEqualTo("persona.numeroDocumento");
        assertThat(response.getBody().details()).isEqualTo(details);
    }

    @Test void conflictoDeOtroModuloConservaSusDetalles() {
        var details = List.of(Map.of("recurso", "Aula"));
        var response = handler.handleConflict(
                new ConflictException("AULA_OCUPADA", "Aula ocupada", details),
                new MockHttpServletRequest("POST", "/api/aulas"));
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().details()).isEqualTo(details);
    }

    @Test void motivoInvalidoSeAsociaAlCampo() {
        var response = handler.handleFormField(
                new FormFieldException("motivo", "El motivo debe tener de 10 a 1000 caracteres"),
                new MockHttpServletRequest("PUT", "/api/alumnos/42"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors().getFirst().field()).isEqualTo("motivo");
        assertThat(response.getBody().fieldErrors().getFirst().message()).contains("10 a 1000");
    }
}
