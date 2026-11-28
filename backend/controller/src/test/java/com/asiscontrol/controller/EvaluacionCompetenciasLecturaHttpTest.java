package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:competencias_lectura_test;MODE=MySQL;DATABASE_TO_UPPER=false")
class EvaluacionCompetenciasLecturaHttpTest extends ApiIntegrationSupport {

    @Test void consultaElPlanComoDocenteSinTransaccionDePrueba() throws Exception {
        grade.setPlanDefinido(true);
        grade.definirCompetencias(course.getId(), List.of(competence));
        long docenteId = teacher.getPersona().getId();
        long competenciaId = competence.getId();
        String ruta = "/api/evaluaciones/competencias?idAsignacionCurso=" + assignment.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        var docente = jwt().jwt(token -> token.subject("docente-" + docenteId)
                        .claim("rol", "DOCENTE").claim("personaId", docenteId))
                .authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("CALIFICACIONES_LEER"));
        mvc.perform(get(ruta).with(docente))
                .andExpect(resultado -> assertThat(resultado.getResolvedException()).isNull())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(competenciaId));
    }
}
