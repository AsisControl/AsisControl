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
    @Test void descargaSoloParaAlumnoYFamiliaVinculadosDespuesDePublicar() throws Exception {
        var periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre");
        periodo.setOrden(1); periodo.setFechaInicio(LocalDate.of(2090,3,1)); periodo.setFechaFin(LocalDate.of(2090,4,30));
        em.persist(periodo);
        var evaluacion = new Evaluacion(); evaluacion.setAsignacionCurso(assignment); evaluacion.setTitulo("Práctica de matemática");
        evaluacion.setTipoEvaluacion(TipoEvaluacion.values()[0]); evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setFechaEvaluacion(LocalDate.of(2090,3,15)); evaluacion.setPonderacionPorcentaje(BigDecimal.valueOf(30));
        evaluacion.setEstado(EstadoEvaluacion.BORRADOR); evaluacion.setCompetencias(Set.of(competence)); em.persist(evaluacion);
        var nota = new Calificacion(); nota.setAlumno(student); nota.setEvaluacion(evaluacion); nota.setCompetencia(competence);
        nota.setNivelLogro(NivelLogro.AD); nota.setFechaRegistro(Instant.now()); em.persist(nota); em.flush();
        String url = "/api/calificaciones/reporte-avance?idAlumno=" + student.getId()
                + "&idAsignacionCurso=" + assignment.getId() + "&idPeriodoAcademico=" + periodo.getId();
        var alumno = como("ALUMNO",student.getPersona().getId());
        mvc.perform(get(url).with(alumno)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/calificaciones/informes-disponibles").with(alumno)).andExpect(jsonPath("$.length()").value(0));
        String body = json.writeValueAsString(data("idAsignacionCurso",assignment.getId(),"idPeriodoAcademico",periodo.getId()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/evaluaciones/publicar-periodo")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluacionesPublicadas").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/evaluaciones/publicar-periodo")
                .with(asAdmin()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluacionesPublicadas").value(0));
        var pdf = mvc.perform(get(url).with(asAdmin()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        for (var usuario : new org.springframework.test.web.servlet.request.RequestPostProcessor[]{alumno,como("APODERADO",guardian.getPersona().getId())}) {
            mvc.perform(get(url).with(usuario)).andExpect(status().isOk());
            mvc.perform(get("/api/calificaciones/informes-disponibles").with(usuario)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get(url.replace("/reporte-avance?","/avance?")).with(usuario))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("AD"));
        }
        for (String rol : new String[]{"ALUMNO","APODERADO","DOCENTE"}) {
            var ajeno=como(rol,person().getId());
            mvc.perform(get(url).with(ajeno)).andExpect(status().isForbidden());
            mvc.perform(get("/api/calificaciones/informes-disponibles").with(ajeno)).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/evaluaciones/publicar-periodo")
                    .with(ajeno).contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
        em.createQuery("update AlumnoApoderado v set v.activo=false where v.alumno.id=:id")
                .setParameter("id",student.getId()).executeUpdate();
        mvc.perform(get(url).with(como("APODERADO",guardian.getPersona().getId()))).andExpect(status().isForbidden());
    }

    @Test void publicarExigePlanillasCompletasYRespetaLasFechas() throws Exception {
        var p = new PeriodoAcademico(); p.setAnioAcademico(year); p.setNombre("Periodo"); p.setOrden(1);
        p.setFechaInicio(LocalDate.of(2090,3,1)); p.setFechaFin(LocalDate.of(2090,4,30)); em.persist(p);
        var completa = evaluacionConNota(LocalDate.of(2090,3,1),true);
        var incompleta = evaluacionConNota(LocalDate.of(2090,4,30),false);
        var fuera = evaluacionConNota(LocalDate.of(2090,5,1),true); em.flush();
        String body = json.writeValueAsString(data("idAsignacionCurso",assignment.getId(),"idPeriodoAcademico",p.getId()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/evaluaciones/publicar-periodo")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVALUACION_INCOMPLETA"));
        assertThat(completa.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        assertThat(incompleta.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        agregarNota(incompleta); em.flush();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/evaluaciones/publicar-periodo")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluacionesPublicadas").value(2));
        assertThat(fuera.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        String url="/api/calificaciones/avance?idAlumno="+student.getId()+"&idAsignacionCurso="+assignment.getId()+"&idPeriodoAcademico="+p.getId();
        mvc.perform(get(url).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"))
                .andExpect(jsonPath("$.filas[1].resultado").value("A"));
        // Incluso sin ninguna calificacion registrada debe existir una opcion de informe.
        em.createQuery("update Calificacion c set c.activo=false where c.alumno.id=:id")
                .setParameter("id",student.getId()).executeUpdate(); em.flush(); em.clear();
        mvc.perform(get("/api/calificaciones/informes-disponibles").with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(url).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("Sin registrar"));
    }

    private Evaluacion evaluacionConNota(LocalDate fecha, boolean completa) {
        var e = new Evaluacion(); e.setAsignacionCurso(assignment); e.setTitulo("Practica");
        e.setTipoEvaluacion(TipoEvaluacion.values()[0]); e.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        e.setFechaEvaluacion(fecha); e.setPonderacionPorcentaje(BigDecimal.valueOf(30));
        e.setCompetencias(Set.of(competence)); em.persist(e);
        if (completa) agregarNota(e);
        return e;
    }
    private void agregarNota(Evaluacion e) {
        var n=new Calificacion(); n.setAlumno(student); n.setEvaluacion(e); n.setCompetencia(competence);
        n.setNivelLogro(NivelLogro.A); n.setFechaRegistro(Instant.now()); em.persist(n);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor como(String rol,Long persona) {
        return jwt().jwt(j -> j.subject(rol).claim("personaId",persona)).authorities(
                new SimpleGrantedAuthority("ROLE_"+rol),new SimpleGrantedAuthority("CALIFICACIONES_LEER"),
                new SimpleGrantedAuthority("CALIFICACIONES_ESCRIBIR"));
    }
}
