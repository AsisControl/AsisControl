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

    @Test void guardaEditaYActivaReglasConControlDeVersionYPermisos() throws Exception {
        var borrador = putOk(REGLAS + "/borrador", entrada(course, null, 40, 40));
        long id = borrador.path("id").asLong();
        long version = borrador.path("version").asLong();
        assertThat(borrador.path("estado").asText()).isEqualTo("BORRADOR");
        request("GET", REGLAS + "/vigente?idAsignacionCurso=" + assignment.getId(), null, 204);
        var invalida = request("POST", REGLAS + "/" + id + "/activar", data("version", version), 422);
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
                    .content(json.writeValueAsBytes(data("version", version)))).andExpect(status().isForbidden());
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
        registrar(assignment, TipoEvaluacion.EXAMEN, NivelLogro.AD);
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
        assertThat(getOk(INFORMES + "estado?idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId()).path("impedimento").asText()).contains("Proyectos");
        registrar(assignment, TipoEvaluacion.PROYECTO, NivelLogro.C);
        request("POST", INFORMES + "generar", entrada, 200);
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
                .andExpect(jsonPath("$.consolidado[0].nivelConsolidado").value("A"));
        var historial = getOk(REGLAS + "?idCurso=" + course.getId() + "&idGrado=" + grade.getId());
        assertThat(historial).hasSize(2);
        assertThat(historial.get(1).path("estado").asText()).isEqualTo("HISTORICA");
        request("POST", INFORMES + "generar", entrada, 200);
        activar(putOk(REGLAS + "/borrador", entrada(course, null, 70, 30)));
        assertThat(request("POST", INFORMES + "publicar", entrada, 422).path("code").asText())
                .isEqualTo("INFORME_REGLA_DESACTUALIZADA");
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
                data("version", regla.path("version").asLong()), 200);
        assertThat(resultado.path("estado").asText()).isEqualTo("VIGENTE");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor como(String rol, Persona persona) {
        return jwt().jwt((token) -> token.subject(rol).claim("rol", rol).claim("personaId", persona.getId()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rol),
                        new SimpleGrantedAuthority("CALIFICACIONES_LEER"), new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR")));
    }

    private void registrar(AsignacionCurso asignacion, TipoEvaluacion tipo, NivelLogro nivel) {
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(asignacion);
        evaluacion.setTitulo(tipo.name());
        evaluacion.setTipoEvaluacion(tipo);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setFechaEvaluacion(LocalDate.of(2090, 3, 15));
        evaluacion.setPonderacionPorcentaje(BigDecimal.valueOf(100));
        evaluacion.setCompetencias(Set.of(competence));
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
    }
}
