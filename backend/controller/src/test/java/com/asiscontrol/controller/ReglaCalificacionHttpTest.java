package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReglaCalificacionHttpTest extends ApiIntegrationSupport {
    private static final String REGLAS = "/api/reglas-calificacion";
    private static final String INFORMES = "/api/calificaciones/informes/";

    @Test void consolidaEnElPeriodoYLaPublicacionExcluyeBorradoresDelDenominador() throws Exception {
        var datos = entrada(course, null, 60, 40);
        datos.put("formula", "CUALITATIVA_DINAMICA_V1");
        activar(putOk(REGLAS + "/borrador", datos));
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year); periodo.setOrden(1); periodo.setNombre("Primer período");
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1)); periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        long evaluacion = registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.AD);
        em.find(Evaluacion.class, evaluacion).setModeloCualitativo(true);
        long borrador = postOk("/api/evaluaciones", data("idAsignacionCurso", assignment.getId(),
                "titulo", "Proyecto", "tipoEvaluacion", "PROYECTO", "periodoEvaluacion", "PERIODO_ACADEMICO",
                "fechaEvaluacion", "2090-04-15", "idsCompetencia", List.of(competence.getId()))).path("id").asLong();
        String path = "/api/calificaciones/consolidados?idAlumno=" + student.getId()
                + "&idAsignacionCurso=" + assignment.getId();
        request("GET", path, null, 422);
        var resultado = getOk(path + "&idPeriodoAcademico=" + periodo.getId()).get(0);
        assertThat(resultado.path("promedioPonderado").decimalValue()).isEqualByComparingTo("4");
        assertThat(resultado.path("nivelConsolidado").asText()).isEqualTo("AD");
        var nota = getOk("/api/evaluaciones/" + evaluacion + "/planilla").path("alumnos").get(0).path("notas").get(0);
        assertThat(nota.path("pesoEfectivo").decimalValue()).isEqualByComparingTo("100");
        request("POST", "/api/calificaciones/correcciones", data("motivo", "Verificación de evidencia",
                "correcciones", List.of(data("idCalificacion", nota.path("idCalificacion").asLong(),
                        "version", nota.path("version").asLong(), "nivelLogro", "B"))), 200);
        assertThat(getOk(path + "&idPeriodoAcademico=" + periodo.getId()).get(0)
                .path("nivelConsolidado").asText()).isEqualTo("B");
        var tarea = postOk("/api/tareas", data("asignacionCursoId", assignment.getId(), "nombre", "Proyecto asignado",
                "descripcion", "Presentar el proyecto", "fechaLimite", "2090-04-15T18:00:00",
                "maxArchivosEntrega", 0, "archivoIds", List.of(), "cuentaParaResultadoAcademico", true,
                "idEvaluacionAcademica", borrador));
        request("POST", "/api/tareas/" + tarea.path("id").asLong() + "/publicar", null, 200);
        var pendiente = getOk(path + "&idPeriodoAcademico=" + periodo.getId()).get(0);
        assertThat(pendiente.path("nivelConsolidado").isNull()).isTrue();
        assertThat(pendiente.path("componentesFaltantes").toString()).contains("PROYECTO");
        var notaConCompromiso = getOk("/api/evaluaciones/" + evaluacion + "/planilla").path("alumnos").get(0).path("notas").get(0);
        assertThat(notaConCompromiso.path("pesoEfectivo").decimalValue()).isEqualByComparingTo("60");
    }

    @Test void detectaDuplicadosSemanticosYPermiteRecuperarUnaHistorica() throws Exception {
        var primera = putOk(REGLAS + "/borrador", entrada(course, null, 60, 40));
        var misma = entrada(course, null, 60, 40);
        misma.put("componentes", List.of(
                data("tipoEvaluacion", "PROYECTO", "nombre", " Proyectos ", "peso", new BigDecimal("40.00")),
                data("tipoEvaluacion", "EXAMEN", "nombre", "Exámenes", "peso", new BigDecimal("60.00"))));
        assertThat(putOk(REGLAS + "/borrador", misma).path("id")).isEqualTo(primera.path("id"));
        activar(primera);
        assertThat(request("PUT", REGLAS + "/borrador", misma, 422).path("code").asText())
                .isEqualTo("REGLA_CONFIGURACION_VIGENTE");
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 70, 30)));
        var restaurada = putOk(REGLAS + "/borrador", misma);
        assertThat(restaurada.path("numeroVersion").asInt()).isEqualTo(3);
        assertThat(restaurada.path("id")).isNotEqualTo(primera.path("id"));
    }

    @Test void validaComponentesNuevosYPermiteConservarElTipoHistorico() throws Exception {
        String evaluaciones = "/api/evaluaciones";
        var datos = data("idAsignacionCurso", assignment.getId(), "titulo", "Evaluación",
                "tipoEvaluacion", "PRACTICA_CALIFICADA", "periodoEvaluacion", "BIMESTRAL",
                "fechaEvaluacion", LocalDate.of(2090, 3, 15), "ponderacionPorcentaje", 75,
                "idsCompetencia", List.of(competence.getId()));
        var historica = postOk(evaluaciones, datos);
        em.find(Evaluacion.class, historica.path("id").asLong()).setPonderacionPorcentaje(BigDecimal.valueOf(75));
        String detalle = evaluaciones + "/" + historica.path("id").asLong();
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 60, 40)));

        assertThat(request("POST", evaluaciones, datos, 422).path("code").asText()).isEqualTo("COMPONENTE_NO_VIGENTE");
        datos.put("titulo", "Borrador anterior editado");
        assertThat(putOk(detalle, datos).path("tipoEvaluacion").asText()).isEqualTo("PRACTICA_CALIFICADA");
        datos.put("tipoEvaluacion", "EVALUACION_ORAL");
        assertThat(request("PUT", detalle, datos, 422).path("code").asText()).isEqualTo("COMPONENTE_NO_VIGENTE");

        datos.put("tipoEvaluacion", "PROYECTO");
        var nueva = postOk(evaluaciones, datos);
        assertThat(nueva.path("tipoEvaluacion").asText()).isEqualTo("PROYECTO");
        assertThat(nueva.path("ponderacionPorcentaje").decimalValue()).isEqualByComparingTo("100");
        datos.put("tipoEvaluacion", "EXAMEN");
        var editada = putOk(detalle, datos);
        assertThat(editada.path("tipoEvaluacion").asText()).isEqualTo("EXAMEN");
        assertThat(editada.path("ponderacionPorcentaje").decimalValue()).isEqualByComparingTo("75");
    }

    @Test void guardaEditaYActivaReglasConControlDeVersionYPermisos() throws Exception {
        var borrador = putOk(REGLAS + "/borrador", entrada(course, null, 40, 40));
        long id = borrador.path("id").asLong();
        long version = borrador.path("version").asLong();
        assertThat(borrador.path("estado").asText()).isEqualTo("BORRADOR");
        request("GET", REGLAS + "/vigente?idAsignacionCurso=" + assignment.getId(), null, 204);
        var invalida = request("POST", REGLAS + "/" + id + "/activar", data("version", version, "motivo", "Acuerdo académico"), 422);
        assertThat(invalida.path("code").asText()).isEqualTo("PESOS_INVALIDOS");
        var actualizado = putOk(REGLAS + "/borrador", entrada(course, version, 60, 40));
        assertThat(actualizado.path("version").asLong()).isGreaterThan(version);
        assertThat(request("PUT", REGLAS + "/borrador", entrada(course, version, 40, 60), 409)
                .path("code").asText()).isEqualTo("VERSION_DESACTUALIZADA");
        activar(actualizado);
        mvc.perform(get(REGLAS + "/vigente?idAsignacionCurso=" + assignment.getId()).with(como("DOCENTE", teacher.getPersona())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numeroVersion").value(1))
                .andExpect(jsonPath("$.componentes[0].tipoEvaluacion").value("EXAMEN"));
        mvc.perform(get(REGLAS + "/vigente?idAsignacionCurso=" + assignment.getId()).with(como("DOCENTE", person())))
                .andExpect(status().isForbidden());
        for (var rol : List.of("DOCENTE", "ALUMNO", "APODERADO")) {
            var sesion = como(rol, person());
            mvc.perform(put(REGLAS + "/borrador").with(sesion).contentType("application/json")
                    .content(json.writeValueAsBytes(entrada(course, null, 50, 50)))).andExpect(status().isForbidden());
            mvc.perform(post(REGLAS + "/" + id + "/activar").with(sesion).contentType("application/json")
                    .content(json.writeValueAsBytes(data("version", version, "motivo", "Acuerdo académico")))).andExpect(status().isForbidden());
        }
    }

    @Test void laVistaPreviaValidaTipoFormulaYComponentes() throws Exception {
        var componentes = List.of(componente("EXAMEN", "Exámenes", 60), componente("PROYECTO", "Proyectos", 40));
        var ejemplo = data("formula", "PROMEDIO_PONDERADO", "componentes", componentes,
                "muestra", List.of(data("tipoEvaluacion", "EXAMEN", "nivel", "A"),
                                  data("tipoEvaluacion", "PROYECTO", "nivel", "C")));
        var resultado = request("POST", REGLAS + "/vista-previa", ejemplo, 200);
        assertThat(resultado.path("nivelConsolidado").asText()).isEqualTo("B");
        assertThat(resultado.path("promedioPonderado").decimalValue()).isEqualByComparingTo("2.20");
        ejemplo.put("formula", "EXPRESION_LIBRE");
        request("POST", REGLAS + "/vista-previa", ejemplo, 400);
        ejemplo.put("formula", "PROMEDIO_PONDERADO");
        ejemplo.put("componentes", List.of(componentes.getFirst(), componentes.getFirst()));
        assertThat(request("POST", REGLAS + "/vista-previa", ejemplo, 422).path("code").asText())
                .isEqualTo("COMPONENTE_DUPLICADO");
    }

    @Test void aplicaPesosDistintosPorCursoYConservaLaReglaDeLaLibretaPublicada() throws Exception {
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Primer bimestre");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1));
        periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        var otroCurso = new Curso();
        otroCurso.setCodigo("OTRO" + key());
        otroCurso.setNombre("Ciencias");
        otroCurso.setHorasSemanales(4);
        otroCurso.setGradoAcademico(grade);
        em.persist(otroCurso);
        grade.incluirCurso(otroCurso);
        var otraAsignacion = new AsignacionCurso();
        otraAsignacion.setCurso(otroCurso);
        otraAsignacion.setDocente(teacher);
        otraAsignacion.setSeccion(section);
        em.persist(otraAsignacion);
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 80, 20)));
        activar(putOk(REGLAS + "/borrador", entrada(otroCurso, null, 20, 80)));
        long examen = registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.AD);
        registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.AD);
        var planilla = getOk("/api/evaluaciones/" + examen + "/planilla");
        assertThat(planilla.path("evaluacion").path("pesoComponente").decimalValue()).isEqualByComparingTo("80");
        assertThat(planilla.path("alumnos").get(0).path("notas").get(0).path("pesoEfectivo").decimalValue())
                .isEqualByComparingTo("40");
        registrar(otraAsignacion, TipoEvaluacion.EXAMEN, NivelLogro.AD);
        registrar(otraAsignacion, TipoEvaluacion.PROYECTO, NivelLogro.C);

        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        var params = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId();
        var reporte = params + "&idPeriodoAcademico=" + periodo.getId();
        var consolidado = "/api/calificaciones/consolidados";
        assertThat(getOk(consolidado + params).get(0).path("componentesFaltantes").get(0).asText())
                .isEqualTo("Proyectos");
        request("POST", INFORMES + "generar", entrada, 200);
        var borrador = getOk(INFORMES + "borrador" + reporte);
        assertThat(borrador.path("consolidado").get(0).path("nivelConsolidado").isNull()).isTrue();
        assertThat(borrador.path("filas").findValuesAsText("ponderacion")).containsExactly("40 %", "40 %");
        assertThat(borrador.path("consolidado").get(0).path("estadoAvance").asText()).isEqualTo("PENDIENTE");
        request("POST", INFORMES + "publicar", entrada, 200);
        mvc.perform(get("/api/calificaciones/avance" + reporte).with(como("APODERADO", guardian.getPersona())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consolidado[0].estadoAvance").value("PENDIENTE"))
                .andExpect(jsonPath("$.consolidado[0].promedioPonderado").isEmpty());
        registrar(assignment, TipoEvaluacion.PROYECTO, NivelLogro.C);
        request("POST", INFORMES + "generar", entrada, 200);
        revisarInforme(periodo.getId());
        request("POST", INFORMES + "publicar", entrada, 200);

        var actual = getOk(consolidado + params).get(0);
        assertThat(actual.path("nivelConsolidado").asText()).isEqualTo("A");
        assertThat(actual.path("promedioPonderado").decimalValue()).isEqualByComparingTo("3.40");
        var otro = getOk(consolidado + "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + otraAsignacion.getId()).get(0);
        assertThat(otro.path("nivelConsolidado").asText()).isEqualTo("B");
        assertThat(otro.path("promedioPonderado").decimalValue()).isEqualByComparingTo("1.60");
        assertThat(getOk(consolidado + "?idAlumno=" + student.getId())).hasSize(2);
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 20, 80)));
        assertThat(getOk(consolidado + params).get(0).path("numeroVersionRegla").asInt()).isEqualTo(2);
        mvc.perform(get("/api/calificaciones/avance" + reporte).with(como("ALUMNO", student.getPersona())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reglaCalificacion.numeroVersion").value(1))
                .andExpect(jsonPath("$.consolidado[0].nivelConsolidado").value("A"))
                .andExpect(jsonPath("$.filas[*].ponderacion", org.hamcrest.Matchers.containsInAnyOrder("40 %", "40 %", "20 %")));
        var pdf = mvc.perform(get("/api/calificaciones/reporte-avance" + reporte + "&idEvaluacion=" + examen)
                        .with(como("ALUMNO", student.getPersona())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var reader = new com.lowagie.text.pdf.PdfReader(pdf)) {
            var texto = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(texto).contains("EXAMEN", "40 %", "Regla v1")
                    .doesNotContain("PROYECTO", "80 %", "Consolidado por regla");
        }
        var historial = getOk(REGLAS + "?idCurso=" + course.getId() + "&idGrado=" + grade.getId());
        assertThat(historial).hasSize(2);
        assertThat(historial.get(1).path("estado").asText()).isEqualTo("HISTORICA");
        request("POST", INFORMES + "generar", entrada, 200);
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 70, 30)));
        assertThat(request("POST", INFORMES + "publicar", entrada, 422).path("code").asText())
                .isEqualTo("INFORME_REGLA_DESACTUALIZADA");
    }

    @Test void guardaConclusionesYExigeTextoParaPublicarUnConsolidadoC() throws Exception {
        var regla = putOk(REGLAS + "/borrador", entrada(course, null, 30, 70));
        request("POST", REGLAS + "/" + regla.path("id").asLong() + "/activar",
                data("version", regla.path("version").asLong(), "motivo", "   "), 400);
        activar(regla);
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Primer bimestre");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1));
        periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.C);
        registrar(assignment, TipoEvaluacion.PROYECTO, NivelLogro.C);
        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        String params = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId();
        request("POST", INFORMES + "generar", entrada, 200);
        request("POST", INFORMES + "publicar", entrada, 200);
        assertThat(getOk("/api/calificaciones/avance" + params).path("conclusionesDescriptivas").isEmpty()).isTrue();
        var texto = "Practicar la explicación del procedimiento.";
        var historico = em.createQuery("from InformeProgreso i where i.alumno.id = :alumno",
                        com.asiscontrol.entity.InformeProgreso.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        historico.setConclusionesDescriptivas(json.writeValueAsString(Map.of(competence.getId(), texto)));
        em.flush();
        mvc.perform(get("/api/calificaciones/avance" + params).with(como("ALUMNO", student.getPersona())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conclusionesDescriptivas." + competence.getId()).value(texto))
                .andExpect(jsonPath("$.consolidado[0].promedioPonderado").isEmpty());
        var pdf = mvc.perform(get("/api/calificaciones/reporte-avance" + params)
                        .with(como("ALUMNO", student.getPersona())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var reader = new com.lowagie.text.pdf.PdfReader(pdf)) {
            assertThat(new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1)
                    .replaceAll("\\s+", " ")).contains(texto);
        }
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 50, 50)));
        request("POST", INFORMES + "generar", entrada, 200);
        assertThat(getOk(INFORMES + "borrador" + params).path("conclusionesDescriptivas").isEmpty()).isTrue();
        assertThat(getOk("/api/calificaciones/avance" + params).path("conclusionesDescriptivas")
                .path(competence.getId().toString()).asText()).isEqualTo(texto);
        var auditoria = em.createQuery("""
                from RegistroAuditoria r where r.entidad = 'ReglaCalificacion' and r.registroId = :id
                and r.accion in ('ACTIVAR_REGLA_CALIFICACION', 'ARCHIVAR_REGLA_CALIFICACION')
                """, RegistroAuditoria.class).setParameter("id", regla.path("id").asText()).getResultList();
        assertThat(auditoria).extracting(RegistroAuditoria::getAccion)
                .containsExactlyInAnyOrder("ACTIVAR_REGLA_CALIFICACION", "ARCHIVAR_REGLA_CALIFICACION");
        assertThat(auditoria).allSatisfy(evento -> {
            assertThat(evento.getMotivo()).isEqualTo("Acuerdo académico");
            assertThat(evento.getFechaHora()).isNotNull();
            assertThat(evento.getActorIdentificador()).isNotBlank();
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = NivelLogro.class, names = {"AD", "A", "B"})
    void permitePublicarSinConclusionParaLosDemasNiveles(NivelLogro nivel) throws Exception {
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 30, 70)));
        var periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Primer bimestre");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 3, 1));
        periodo.setFechaFin(LocalDate.of(2090, 4, 30));
        em.persist(periodo);
        registrar(assignment, TipoEvaluacion.EXAMEN, nivel);
        registrar(assignment, TipoEvaluacion.PROYECTO, nivel);
        var entrada = data("idAsignacionCurso", assignment.getId(), "idPeriodoAcademico", periodo.getId());
        request("POST", INFORMES + "generar", entrada, 200);
        revisarInforme(periodo.getId());
        request("POST", INFORMES + "publicar", entrada, 200);
    }

    @Test void reparteElPesoEntreResultadosValidosDeCadaCompetencia() throws Exception {
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 30, 70)));
        long primerExamen = registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.A);
        long segundoExamen = registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.B);
        var adicional = new Competencia();
        adicional.setCodigo("ADIC" + key());
        adicional.setNombre("Comunica sus resultados");
        em.persist(adicional);
        var primero = em.find(Evaluacion.class, primerExamen);
        primero.getCompetencias().add(adicional);
        em.find(Evaluacion.class, segundoExamen).getCompetencias().add(adicional);
        var notaAdicional = new Calificacion();
        notaAdicional.setAlumno(student);
        notaAdicional.setCompetencia(adicional);
        notaAdicional.setEvaluacion(primero);
        notaAdicional.setNivelLogro(NivelLogro.A);
        notaAdicional.setFechaRegistro(Instant.now());
        em.persist(notaAdicional);
        em.flush();

        var notas = getOk("/api/evaluaciones/" + primerExamen + "/planilla")
                .path("alumnos").get(0).path("notas");
        var pesos = new java.util.HashMap<Long, BigDecimal>();
        notas.forEach(nota -> pesos.put(nota.path("idCompetencia").asLong(), nota.path("pesoEfectivo").decimalValue()));
        assertThat(pesos.get(competence.getId())).isEqualByComparingTo("15");
        assertThat(pesos.get(adicional.getId())).isEqualByComparingTo("30");
    }

    @Test void consolidadoNumericoConCeroExplicitoYPendienteDuranteLaTransicion() throws Exception {
        String escalas = "/api/escala-institucional";
        var escala = putOk(escalas + "/borrador", data("rangos", List.of(
                data("nivel", "C", "minimo", 0, "maximo", 8), data("nivel", "B", "minimo", 9, "maximo", 12),
                data("nivel", "A", "minimo", 13, "maximo", 17), data("nivel", "AD", "minimo", 18, "maximo", 20))));
        request("POST", escalas + "/" + escala.path("id").asLong() + "/activar",
                data("version", escala.path("version").asLong(), "motivo", "Escala de prueba"), 200);
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 80, 20)));

        long examen = registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.AD);
        long proyecto = registrar(assignment, TipoEvaluacion.PROYECTO, NivelLogro.C);
        var notas = em.createQuery("from Calificacion c where c.evaluacion.id in :ids", Calificacion.class)
                .setParameter("ids", List.of(examen, proyecto)).getResultList();
        var notaExamen = notas.stream().filter(nota -> nota.getEvaluacion().getId().equals(examen)).findFirst().orElseThrow();
        var notaProyecto = notas.stream().filter(nota -> nota.getEvaluacion().getId().equals(proyecto)).findFirst().orElseThrow();
        notaExamen.setNotaNumerica(new BigDecimal("17.50"));
        String path = "/api/calificaciones/consolidados?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId();
        assertThat(getOk(path).get(0).path("nivelConsolidado").isNull()).isTrue();

        notaProyecto.setNotaNumerica(BigDecimal.ZERO);
        var consolidado = getOk(path).get(0);
        assertThat(consolidado.path("promedioPonderado").decimalValue()).isEqualByComparingTo("14");
        assertThat(consolidado.path("nivelConsolidado").asText()).isEqualTo("A");
        assertThat(consolidado.path("evaluacionesConsideradas").asInt()).isEqualTo(2);
        mvc.perform(get(path).with(como("APODERADO", guardian.getPersona())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].promedioPonderado").isEmpty())
                .andExpect(jsonPath("$[0].nivelConsolidado").value("A"));
    }

    private Map<String, Object> entrada(Curso curso, Long version, int examenes, int proyectos) {
        return data("idCurso", curso.getId(), "idGrado", grade.getId(), "formula", "PROMEDIO_PONDERADO",
                "version", version, "componentes", List.of(componente("EXAMEN", "Exámenes", examenes),
                        componente("PROYECTO", "Proyectos", proyectos)));
    }

    private Map<String, Object> componente(String tipo, String nombre, int peso) {
        return data("tipoEvaluacion", tipo, "nombre", nombre, "peso", peso);
    }

    private void activar(com.fasterxml.jackson.databind.JsonNode regla) throws Exception {
        var resultado = request("POST", REGLAS + "/" + regla.path("id").asLong() + "/activar",
                data("version", regla.path("version").asLong(), "motivo", "Acuerdo académico"), 200);
        assertThat(resultado.path("estado").asText()).isEqualTo("VIGENTE");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor como(String rol, Persona persona) {
        return jwt().jwt((token) -> token.subject(rol).claim("rol", rol).claim("personaId", persona.getId()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rol),
                        new SimpleGrantedAuthority("CALIFICACIONES_LEER"), new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR")));
    }

    private long registrar(AsignacionCurso asignacion, TipoEvaluacion tipo, NivelLogro nivel) {
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(asignacion);
        evaluacion.setTitulo(tipo.name());
        evaluacion.setTipoEvaluacion(tipo);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setFechaEvaluacion(LocalDate.of(2090, 3, 15));
        evaluacion.setPonderacionPorcentaje(BigDecimal.valueOf(100));
        evaluacion.setCompetencias(new java.util.LinkedHashSet<>(Set.of(competence)));
        evaluacion.setEstado(EstadoEvaluacion.PUBLICADA);
        em.persist(evaluacion);
        var nota = new Calificacion();
        nota.setAlumno(student);
        nota.setEvaluacion(evaluacion);
        nota.setCompetencia(competence);
        nota.setNivelLogro(nivel);
        nota.setFechaRegistro(Instant.now());
        em.persist(nota);
        em.flush();
        return evaluacion.getId();
    }
}
