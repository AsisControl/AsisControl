package com.asiscontrol.controller;

import com.asiscontrol.entity.Calificacion;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReporteAvanceHttpTest extends ApiIntegrationSupport {
    @Test void generaSinEvaluacionesYConActividadesSinNotasConservaLaPrivacidad() throws Exception {
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Primer bimestre");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1));
        periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        String base = "/api/calificaciones/informes/";
        request("POST", base + "generar", entrada, 200);
        String consulta = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId();
        assertThat(getOk(base + "borrador" + consulta).path("consolidado").get(0).path("estadoAvance").asText())
                .isEqualTo("NO_EVALUADA");

        String filtro = base + "academicos?anio=2090&grado=1&curso=" + course.getId()
                + "&seccion=" + section.getId() + "&periodo=" + periodo.getId();
        var libretas = getOk(filtro);
        assertThat(libretas.size()).isEqualTo(1);
        assertThat(libretas.get(0).path("noEvaluadas").asInt()).isEqualTo(1);
        String detalle = base + "academicos/" + libretas.get(0).path("id").asLong();
        assertThat(getOk(detalle).path("codigoAlumno").asText()).isEqualTo(student.getCodigoAlumno());
        assertThat(getOk(filtro.replace("grado=1", "grado=2")).size()).isZero();
        mvc.perform(get(detalle).with(como("ALUMNO", student.getPersona().getId()))).andExpect(status().isForbidden());

        evaluacionConNota(LocalDate.of(2090, 3, 15), false);
        em.flush();
        assertThat(request("POST", base + "generar", entrada, 200).path("informesProcesados").asInt())
                .isEqualTo(1);
        String params = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId();
        assertThat(getOk(base + "borrador" + params).path("consolidado").get(0).path("estadoAvance").asText())
                .isEqualTo("PENDIENTE");
        mvc.perform(get("/api/calificaciones/informes-disponibles")
                        .with(como("APODERADO", guardian.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        publicarLibretas(entrada, 200);
        mvc.perform(get("/api/calificaciones/avance" + params).with(como("APODERADO", guardian.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas.length()").value(0))
                .andExpect(jsonPath("$.consolidado[0].estadoAvance").value("PENDIENTE"));
    }
    @Test void conservaCompetenciaCurricularNoEvaluadaEnLaVersionPublicada() throws Exception {
        var periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre");
        periodo.setOrden(1); periodo.setFechaInicio(LocalDate.of(2090, 3, 1)); periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var adicional = new com.asiscontrol.entity.Competencia();
        adicional.setCodigo("CURR" + key()); adicional.setNombre("Argumenta sus conclusiones"); em.persist(adicional);
        var asociacion = new com.asiscontrol.entity.CursoCompetencia();
        asociacion.setCurso(course); asociacion.setCompetencia(adicional); asociacion.setOrden(2); em.persist(asociacion);
        var evaluacion = evaluacionConNota(LocalDate.of(2090, 3, 15), false);
        evaluacion.setEstado(EstadoEvaluacion.PUBLICADA);
        var base = "/api/calificaciones/informes/";
        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        String contexto = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId();
        request("POST", base + "generar", entrada, 200);
        var borrador = getOk(base + "borrador" + contexto);
        assertThat(borrador.path("consolidado").findValuesAsText("estadoAvance"))
                .containsExactlyInAnyOrder("NO_EVALUADA", "PENDIENTE");
        assertThat(borrador.path("filas").get(0).path("resultado").asText()).isEqualTo("Sin registrar");
        revisarInforme(periodo.getId());
        publicarLibretas(entrada, 200);
        asociacion.setActivo(false);
        adicional.setActivo(false);
        em.flush();
        var publicado = getOk("/api/calificaciones/avance" + contexto);
        assertThat(publicado.path("consolidado").findValuesAsText("nombreCompetencia")).contains("Argumenta sus conclusiones");
        request("POST", base + "generar", entrada, 200);
        assertThat(getOk(base + "borrador" + contexto).path("consolidado")
                .findValuesAsText("nombreCompetencia")).doesNotContain("Argumenta sus conclusiones");
        assertThat(getOk("/api/calificaciones/avance" + contexto).path("consolidado")).isEqualTo(publicado.path("consolidado"));
    }

    @Test void descargaSoloParaAlumnoYFamiliaVinculadosDespuesDePublicar() throws Exception {
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
        var alumno = como("ALUMNO",student.getPersona().getId());
        mvc.perform(get(url).with(alumno)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/calificaciones/informes-disponibles").with(alumno)).andExpect(jsonPath("$.length()").value(0));
        String body = json.writeValueAsString(data("idAsignacionCurso",assignment.getId(),"idPeriodoAcademico",periodo.getId()));
        request("POST", "/api/calificaciones/informes/generar",
                data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId()), 200);
        revisarInforme(periodo.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.informesProcesados").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(asAdmin()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
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

    @Test void descargaParcialFiltraEvaluacionesDeLaLibretaYConservaPermisos() throws Exception {
        var periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre");
        periodo.setOrden(1); periodo.setFechaInicio(LocalDate.of(2090, 3, 1)); periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var examen = evaluacionConNota(LocalDate.of(2090, 3, 15), true);
        var practica = evaluacionConNota(LocalDate.of(2090, 3, 22), true);
        var proyecto = evaluacionConNota(LocalDate.of(2090, 4, 5), true);
        examen.setTitulo("Examen de números"); practica.setTitulo("Práctica de formas"); proyecto.setTitulo("Proyecto de medidas");
        em.flush();
        examen.setEstado(EstadoEvaluacion.PUBLICADA);
        practica.setEstado(EstadoEvaluacion.PUBLICADA);
        proyecto.setEstado(EstadoEvaluacion.CERRADA);
        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        request("POST", "/api/calificaciones/informes/generar", entrada, 200);
        revisarInforme(periodo.getId());
        publicarLibretas(entrada, 200);

        String url = "/api/calificaciones/reporte-avance?idAlumno=" + student.getId()
                + "&idAsignacionCurso=" + assignment.getId() + "&idPeriodoAcademico=" + periodo.getId();
        var alumno = como("ALUMNO", student.getPersona().getId());
        var completo = mvc.perform(get(url).with(alumno)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"Informe_completo_")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(textoPdf(completo)).contains("Informe de progreso por competencias", "Evaluación", "Nivel", "Peso", "Observación")
                .containsSubsequence(examen.getTitulo(), "Fecha: 15/03/2090", practica.getTitulo(), proyecto.getTitulo())
                .doesNotContain("Evaluación / fecha", "EVALUACIÓN 1", "Título:");

        var parcial = mvc.perform(get(url).param("idEvaluacion", examen.getId().toString(), proyecto.getId().toString()).with(alumno))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"Informe_parcial_")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(textoPdf(parcial)).contains("Informe parcial de progreso", "Primer bimestre")
                .containsSubsequence(examen.getTitulo(), "Fecha: 15/03/2090", proyecto.getTitulo())
                .doesNotContain("Informe de progreso por competencias", "Evaluación / fecha", "EVALUACIÓN 1", "Título:", practica.getTitulo());
        Files.createDirectories(Path.of("target", "reportes-prueba"));
        Files.write(Path.of("target", "reportes-prueba", "avance-parcial.pdf"), parcial);

        mvc.perform(get(url).param("idEvaluacion", "").with(alumno)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url).param("idEvaluacion", "999999").with(alumno)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url).param("idEvaluacion", examen.getId().toString()).param("idCompetencia", "999999").with(alumno))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url).param("idEvaluacion", examen.getId().toString()).with(como("ALUMNO", person().getId())))
                .andExpect(status().isForbidden());
    }

    @Test void generaParcialDocenteSinLibretaPublicadaYRechazaContextoNoAutorizado() throws Exception {
        var periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre");
        student.getPersona().setNombres("Sofía");
        student.getPersona().setApellidoPaterno("Quispe");
        student.getPersona().setApellidoMaterno("Flóres");
        periodo.setOrden(1); periodo.setFechaInicio(LocalDate.of(2090, 3, 1)); periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var primera = evaluacionConNota(LocalDate.of(2090, 3, 15), true);
        primera.setEstado(EstadoEvaluacion.PUBLICADA);
        var otraCompetencia = new com.asiscontrol.entity.Competencia();
        otraCompetencia.setCodigo("PARC" + key()); otraCompetencia.setNombre("Comunica resultados del parcial");
        em.persist(otraCompetencia);
        primera.setCompetencias(Set.of(competence, otraCompetencia));
        var segunda = evaluacionConNota(LocalDate.of(2090, 3, 20), true);
        segunda.setEstado(EstadoEvaluacion.CERRADA);
        var privada = evaluacionConNota(LocalDate.of(2090, 3, 25), true);
        var sinPeso = evaluacionConNota(LocalDate.of(2090, 3, 26), true);
        sinPeso.setCuentaResultado(false);
        sinPeso.setEstado(EstadoEvaluacion.PUBLICADA);
        var componente = primera.getTipoEvaluacion().name();
        var regla = putOk("/api/reglas-calificacion/borrador", data("idCurso", course.getId(), "idGrado", grade.getId(),
                "formula", "PROMEDIO_PONDERADO", "componentes", java.util.List.of(
                        data("tipoEvaluacion", componente, "nombre", "Evaluaciones", "peso", 80),
                        data("tipoEvaluacion", TipoEvaluacion.PROYECTO.name(), "nombre", "Proyectos", "peso", 20))));
        request("POST", "/api/reglas-calificacion/" + regla.path("id").asLong() + "/activar",
                data("version", regla.path("version").asLong(), "motivo", "Acuerdo académico"), 200);
        var base = "/api/calificaciones/informes/parcial";
        var parametros = "?idAsignacionCurso=" + assignment.getId() + "&idPeriodoAcademico=" + periodo.getId();
        var contexto = parametros + "&idAlumno=" + student.getId();
        var disponibles = getOk(base + "/evaluaciones" + parametros);
        assertThat(disponibles).hasSize(3);
        assertThat(disponibles.get(0).path("competencias")).hasSize(2);
        var docente = como("DOCENTE", teacher.getPersona().getId());
        mvc.perform(get(base + contexto).param("idEvaluacion", primera.getId().toString()).param("idCompetencia", competence.getId().toString()).with(docente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas.length()").value(1))
                .andExpect(jsonPath("$.filas[0].ponderacion").value("40 %"))
                .andExpect(jsonPath("$.consolidado").isEmpty());
        mvc.perform(get(base + contexto).param("idEvaluacion", sinPeso.getId().toString()).with(docente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].ponderacion").value("Sin peso"))
                .andExpect(jsonPath("$.consolidado").isEmpty());
        mvc.perform(get(base + contexto).param("idEvaluacion", privada.getId().toString()).with(docente))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(base + contexto).param("idEvaluacion", primera.getId().toString())
                        .with(como("APODERADO", guardian.getPersona().getId())))
                .andExpect(status().isForbidden());
        var pdf = mvc.perform(get(base + "/pdf" + contexto).param("idEvaluacion", primera.getId().toString()).param("idCompetencia", competence.getId().toString()).with(docente))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"Informe_parcial_Sofia_Quispe_Flores_[A-Za-z0-9_]+_Primer_bimestre\\.pdf\"")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(textoPdf(pdf)).contains("Informe parcial de progreso", "40 %", competence.getNombre())
                .doesNotContain("Consolidado", otraCompetencia.getNombre());
        assertThat(em.createQuery("select count(i) from InformeProgreso i", Long.class).getSingleResult()).isZero();
    }

    private String textoPdf(byte[] pdf) throws Exception {
        try (var reader = new PdfReader(pdf)) {
            var extractor = new PdfTextExtractor(reader);
            var texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) texto.append(extractor.getTextFromPage(pagina));
            return texto.toString();
        }
    }

    @Test void publicarAvanceNoPublicaEvaluacionesYRespetaLasFechas() throws Exception {
        var p = new PeriodoAcademico(); p.setAnioAcademico(year); p.setNombre("Periodo"); p.setOrden(1);
        p.setFechaInicio(LocalDate.of(2090,3,1)); p.setFechaFin(LocalDate.of(2090,4,30)); em.persist(p);
        var completa = evaluacionConNota(LocalDate.of(2090,3,1),true);
        var incompleta = evaluacionConNota(LocalDate.of(2090,4,30),false);
        var fuera = evaluacionConNota(LocalDate.of(2090,5,1),true); em.flush();
        String body = json.writeValueAsString(data("idAsignacionCurso",assignment.getId(),"idPeriodoAcademico",p.getId()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORME_BORRADOR_REQUERIDO"));
        assertThat(completa.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        assertThat(incompleta.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        completa.setEstado(EstadoEvaluacion.PUBLICADA); em.flush();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/generar")
                .with(asAdmin()).contentType("application/json").content(body)).andExpect(status().isOk());
        revisarInforme(p.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.informesProcesados").value(1));
        assertThat(incompleta.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        assertThat(fuera.getEstado()).isEqualTo(EstadoEvaluacion.BORRADOR);
        String url="/api/calificaciones/avance?idAlumno="+student.getId()+"&idAsignacionCurso="+assignment.getId()+"&idPeriodoAcademico="+p.getId();
        var detalle = em.createQuery("from DetalleInformeProgreso d where d.informe.alumno.id = :alumno",
                        com.asiscontrol.entity.DetalleInformeProgreso.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        detalle.setNotaNumerica(new java.math.BigDecimal("0"));
        em.flush();
        assertThat(getOk(url).path("filas").get(0).path("notaNumerica").decimalValue()).isEqualByComparingTo("0");
        mvc.perform(get(url).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"))
                .andExpect(jsonPath("$.filas[0].notaNumerica").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.filas.length()").value(1));
        mvc.perform(get(url).with(como("APODERADO", guardian.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].notaNumerica").doesNotHaveJsonPath());
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
        var comentario = "Explica su procedimiento";
        var lote = json.writeValueAsString(data("calificaciones", java.util.List.of(data(
                "idEvaluacion", e.getId(), "idAlumno", student.getId(), "idCompetencia", competence.getId(),
                "nivelLogro", "A", "observacion", comentario))));
        var docente = como("DOCENTE", teacher.getPersona().getId());
        mvc.perform(put("/api/calificaciones/lote").with(docente).contentType("application/json").content(lote))
                .andExpect(status().isOk());
        mvc.perform(get("/api/evaluaciones/" + e.getId() + "/planilla").with(docente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alumnos[0].notas[0].observacion").value(comentario));
        e.setEstado(EstadoEvaluacion.PUBLICADA);
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
        revisarInforme(p.getId());
        mvc.perform(get(estadoUrl).with(como("DOCENTE", teacher.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicable").value(true))
                .andExpect(jsonPath("$.alumnos[0].borrador").value(true));
        mvc.perform(get(estadoUrl).with(como("DOCENTE", person().getId()))).andExpect(status().isForbidden());
        mvc.perform(get(estadoUrl).with(como("APODERADO", guardian.getPersona().getId()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/calificaciones/informes/borrador"+params).with(como("DOCENTE",teacher.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"))
                .andExpect(jsonPath("$.filas[0].observacion").value(comentario));
        for (String rol : new String[]{"ALUMNO", "APODERADO"}) {
            Long persona = rol.equals("ALUMNO") ? student.getPersona().getId() : guardian.getPersona().getId();
            mvc.perform(get("/api/calificaciones/informes/borrador"+params).with(como(rol,persona))).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/generar")
                    .with(como(rol,persona)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/calificaciones/avance"+params).with(asAdmin())).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body)).andExpect(status().isOk());
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
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORME_DESACTUALIZADO"));
        nota.setObservacion("Resultado corregido"); em.flush();
        mvc.perform(get("/api/calificaciones/informes/borrador"+params).with(asAdmin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("AD"));
        mvc.perform(get("/api/calificaciones/avance"+params).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("A"));
        revisarInforme(p.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calificaciones/informes/publicar")
                .with(como("DOCENTE",teacher.getPersona().getId())).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/calificaciones/avance"+params).with(como("ALUMNO",student.getPersona().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filas[0].resultado").value("AD"))
                .andExpect(jsonPath("$.filas[0].observacion").value("Resultado corregido"));
        mvc.perform(get(estadoUrl).with(asAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.desactualizada").value(false))
                .andExpect(jsonPath("$.publicable").value(false));
        mvc.perform(get("/api/calificaciones/reporte-avance" + params).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"Informe_completo_[A-Za-z0-9_]+_Periodo_V2\\.pdf\"")));
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
    @Test void administradorConsultaEvaluacionesDelPeriodoConAvanceReal() throws Exception {
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year); periodo.setNombre("Primer bimestre"); periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090,3,1)); periodo.setFechaFin(LocalDate.of(2090,4,30)); em.persist(periodo);
        evaluacionConNota(periodo.getFechaInicio(), false);
        var calificada = evaluacionConNota(periodo.getFechaFin(), true);
        evaluacionConNota(periodo.getFechaFin().plusDays(1), true);
        em.flush();
        String url = "/api/evaluaciones/academicas?anio=2090&grado=1&curso=" + course.getId()
                + "&seccion=" + section.getId() + "&periodo=" + periodo.getId();
        var res = getOk(url);
        assertThat(res.path("totalElements").asInt()).isEqualTo(2);
        var filas = res.path("content");
        assertThat(filas.get(0).path("evaluacion").path("id").asLong()).isEqualTo(calificada.getId());
        assertThat(filas.get(0).path("registradas").asInt()).isEqualTo(1);
        assertThat(filas.get(0).path("esperadas").asInt()).isEqualTo(1);
        assertThat(filas.get(1).path("registradas").asInt()).isZero();
        assertThat(getOk(url.replace("grado=1", "grado=2")).path("totalElements").asInt()).isZero();
        mvc.perform(get(url).with(como("DOCENTE", teacher.getPersona().getId()))).andExpect(status().isForbidden());
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
