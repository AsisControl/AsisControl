package com.asiscontrol.controller;

import com.asiscontrol.entity.Calificacion;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReporteAvanceHttpTest extends ApiIntegrationSupport {
    @Test void descargaPdfDesdeDatosPersistidosYDeniegaRolAlumno() throws Exception {
        var periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre");
        periodo.setOrden(1); periodo.setFechaInicio(LocalDate.of(2090,3,1)); periodo.setFechaFin(LocalDate.of(2090,4,30));
        em.persist(periodo);
        var evaluacion = new Evaluacion(); evaluacion.setAsignacionCurso(assignment); evaluacion.setTitulo("Práctica de matemática");
        evaluacion.setTipoEvaluacion(TipoEvaluacion.values()[0]); evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setFechaEvaluacion(LocalDate.of(2090,3,15)); evaluacion.setPonderacionPorcentaje(BigDecimal.valueOf(30));
        evaluacion.setEstado(EstadoEvaluacion.PUBLICADA); evaluacion.setCompetencias(Set.of(competence)); em.persist(evaluacion);
        var nota = new Calificacion(); nota.setAlumno(student); nota.setEvaluacion(evaluacion); nota.setCompetencia(competence);
        nota.setNivelLogro(NivelLogro.AD); nota.setFechaRegistro(Instant.now()); em.persist(nota); em.flush();
        String url = "/api/calificaciones/reporte-avance?idAlumno=" + student.getId()
                + "&idAsignacionCurso=" + assignment.getId() + "&idPeriodoAcademico=" + periodo.getId();
        var pdf = mvc.perform(get(url).with(asAdmin()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mvc.perform(get(url).with(jwt().jwt(j -> j.subject("alumno").claim("personaId",student.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_ALUMNO"),new SimpleGrantedAuthority("CALIFICACIONES_LEER"))))
                .andExpect(status().isForbidden());
    }
}
