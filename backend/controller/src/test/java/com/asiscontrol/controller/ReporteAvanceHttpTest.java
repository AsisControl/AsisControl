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
    @Test void sinEvaluacionesRechazaGenerarYConEvaluacionSinNotasDejaBorradorPrivado() throws Exception {
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Primer bimestre");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1));
        periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        String base = "/api/calificaciones/informes/";
        assertThat(request("POST", base + "generar", entrada, 422).path("code").asText())
                .isEqualTo("REPORTE_SIN_CALIFICACIONES");

        evaluacionConNota(LocalDate.of(2090, 3, 15), false);
        em.flush();
        assertThat(request("POST", base + "generar", entrada, 200).path("informesProcesados").asInt())
                .isEqualTo(1);
        String params = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId();
        assertThat(getOk(base + "borrador" + params).path("filas").get(0).path("resultado").asText())
                .isEqualTo("Sin registrar");
        assertThat(request("POST", base + "publicar", entrada, 422).path("code").asText())
                .isEqualTo("INFORME_INCOMPLETO");
        mvc.perform(get("/api/calificaciones/informes-disponibles")
                        .with(como("APODERADO", guardian.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
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
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.informesProcesados").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.informesProcesados").value(0));
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
            mvc.perform(get(url.replace("/reporte-avance?", "/avance?")).with(ajeno))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/calificaciones/informes-disponibles").with(ajeno)).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
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
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORME_INCOMPLETO"));
        assertThat(completa.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        assertThat(incompleta.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        agregarNota(incompleta); em.flush();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/generar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.informesProcesados").value(1));
        assertThat(fuera.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        String url="/api/calificaciones/avance?idAlumno="+student.getId()+"&idAsignacionCurso="+assignment.getId()+"&idPeriodoAcademico="+p.getId();
        mvc.perform(get(url).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"))
                .andExpect(jsonPath("$.filas[1].resultado").value("A"));
        // El informe publicado conserva sus resultados aunque las notas de origen se desactiven.
        em.createQuery("update Calificacion c set c.activo=false where c.alumno.id=:id")
                .setParameter("id",student.getId()).executeUpdate(); em.flush(); em.clear();
        mvc.perform(get("/api/calificaciones/informes-disponibles").with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(url).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"));
    }

    @Test void borradoresVersionesYResultadosPublicadosSonIndependientes() throws Exception {
        var p = new PeriodoAcademico(); p.setAnioAcademico(year); p.setNombre("Periodo"); p.setOrden(1);
        p.setFechaInicio(LocalDate.of(2090,3,1)); p.setFechaFin(LocalDate.of(2090,4,30)); em.persist(p);
        var e = evaluacionConNota(LocalDate.of(2090,3,15), true); em.flush();
        var body = json.writeValueAsString(data("idAsignacionCurso",assignment.getId(),"idPeriodoAcademico",p.getId()));
        String params = "?idAlumno="+student.getId()+"&idAsignacionCurso="+assignment.getId()+"&idPeriodoAcademico="+p.getId();
        String estadoUrl = "/api/calificaciones/informes/estado?idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + p.getId();
        for (int n = 0; n < 2; n++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/generar")
                    .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk());
        }
        assertThat(em.createQuery("select count(i) from InformeProgreso i", Long.class).getSingleResult()).isEqualTo(1);
        assertThat(em.createQuery("select count(d) from DetalleInformeProgreso d", Long.class).getSingleResult()).isEqualTo(1);
        mvc.perform(get(estadoUrl).with(como("DOCENTE", teacher.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicable").value(true))
                .andExpect(jsonPath("$.alumnos[0].borrador").value(true));
        mvc.perform(get(estadoUrl).with(como("DOCENTE", person().getId()))).andExpect(status().isForbidden());
        mvc.perform(get(estadoUrl).with(como("APODERADO", guardian.getPersona().getId()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/calificaciones/informes/borrador"+params).with(como("DOCENTE",teacher.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"));
        for (String rol : new String[]{"ALUMNO", "APODERADO"}) {
            Long persona = rol.equals("ALUMNO") ? student.getPersona().getId() : guardian.getPersona().getId();
            mvc.perform(get("/api/calificaciones/informes/borrador"+params).with(como(rol,persona))).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/generar")
                    .with(como(rol,persona)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/calificaciones/avance"+params).with(asAdmin())).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk());
        var nota = em.createQuery("select c from Calificacion c where c.evaluacion = :e",Calificacion.class)
                .setParameter("e",e).getSingleResult();
        nota.setNivelLogro(NivelLogro.AD); nota.setObservacion("Resultado corregido");
        e.setTitulo("Titulo corregido"); em.flush();
        mvc.perform(get(estadoUrl).with(asAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.desactualizada").value(true))
                .andExpect(jsonPath("$.publicable").value(false))
                .andExpect(jsonPath("$.alumnos[0].publicada").value(true));
        mvc.perform(get("/api/calificaciones/avance"+params).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"))
                .andExpect(jsonPath("$.filas[0].evaluacion").value("Practica"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/generar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk());
        nota.setObservacion("Cambio posterior al borrador"); em.flush();
        mvc.perform(get(estadoUrl).with(asAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicable").value(false))
                .andExpect(jsonPath("$.alumnos[0].borrador").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORME_DESACTUALIZADO"));
        nota.setObservacion("Resultado corregido"); em.flush();
        mvc.perform(get("/api/calificaciones/informes/borrador"+params).with(asAdmin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("AD"));
        mvc.perform(get("/api/calificaciones/avance"+params).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/calificaciones/avance"+params).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("AD"))
                .andExpect(jsonPath("$.filas[0].observacion").value("Resultado corregido"));
        mvc.perform(get(estadoUrl).with(asAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.desactualizada").value(false))
                .andExpect(jsonPath("$.publicable").value(false));
        mvc.perform(get("/api/calificaciones/reporte-avance" + params).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"Avance_[A-Za-z0-9_]+_Periodo_V2\\.pdf\"")));
        var versiones = em.createQuery("select i from InformeProgreso i order by i.numeroVersion",com.asiscontrol.entity.InformeProgreso.class).getResultList();
        assertThat(versiones).hasSize(2);
        assertThat(versiones).allMatch(i -> i.getEstado() == EstadoInformeProgreso.PUBLICADA && i.getFechaPublicacion() != null);
        assertThat(em.createQuery("select d.nivelLogro from DetalleInformeProgreso d where d.informe = :i",NivelLogro.class)
                .setParameter("i",versiones.getFirst()).getSingleResult()).isEqualTo(NivelLogro.A);
        mvc.perform(get("/api/calificaciones/informes-disponibles").with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void validarCursoPeriodoYMatriculaAntesDeLeerInforme() throws Exception {
        var p = new PeriodoAcademico(); p.setAnioAcademico(year); p.setNombre("Periodo"); p.setOrden(1);
        p.setFechaInicio(LocalDate.of(2090,3,1)); p.setFechaFin(LocalDate.of(2090,4,30)); em.persist(p);
        String url="/api/calificaciones/avance?idAlumno="+student.getId()+"&idAsignacionCurso="+assignment.getId()+"&idPeriodoAcademico="+p.getId();
        section.setAnioAcademico(2091); em.flush();
        mvc.perform(get(url).with(asAdmin())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIODO_CURSO_INVALIDO"));
        section.setAnioAcademico(2090);
        em.createQuery("update Matricula m set m.activo = false where m.alumno = :a").setParameter("a",student).executeUpdate();
        mvc.perform(get(url).with(asAdmin())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALUMNO_CURSO_INVALIDO"));
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
