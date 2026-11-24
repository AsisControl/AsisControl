package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.CursoEnPlan;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanEstudiosHttpTest extends ApiIntegrationSupport {
    private static final String PLAN = "/api/planes-estudios/2090/1";

    @Test void renombraUnPlanDeAnoCerradoSinCambiarSuContenido() throws Exception {
        year.setEstado(EstadoAnioAcademico.CERRADO);
        em.flush();
        var antes = getOk(PLAN);
        String ruta = "/api/planes-estudios/2090/nombre";
        long version = getOk(ruta).path("version").asLong();
        var actualizado = putOk(ruta, data("version", version, "nombre", "Plan histórico de secundaria"));
        assertThat(actualizado.path("nombre").asText()).isEqualTo("Plan histórico de secundaria");
        assertThat(getOk(PLAN)).isEqualTo(antes);
        request("PUT", ruta, data("version", version, "nombre", "Edición concurrente"), 409);
    }

    @Test void agregaCursoDelCatalogoYRechazaUsarloEnUnGradoDistinto() throws Exception {
        year.setEstado(EstadoAnioAcademico.BORRADOR);
        var otroGrado = new GradoAcademico();
        otroGrado.setAnioAcademico(year);
        otroGrado.setNumero(2);
        otroGrado.setCapacidadDefault(30);
        em.persist(otroGrado);
        long catalogo = curso();
        var entrada = data("cursoId", catalogo, "seccionId", section.getId(), "docenteId", teacher.getId());
        assertThat(request("POST", "/api/asignaciones-cursos", entrada, 409).path("code").asText())
                .isEqualTo("CURSO_FUERA_DEL_PLAN");

        long version = getOk(PLAN).path("version").asLong();
        request("PUT", PLAN, data("version", version, "cursosIds", List.of(catalogo, catalogo)), 422);
        request("PUT", PLAN, data("version", version, "cursosIds", List.of(Long.MAX_VALUE)), 422);
        var guardado = putOk(PLAN, data("version", version, "cursosIds", List.of(course.getId(), catalogo)));
        assertThat(guardado.path("cursos")).hasSize(2);
        assertThat(guardado.path("version").asLong()).isGreaterThan(version);
        var otroPlan = request("PUT", "/api/planes-estudios/2090/2",
                data("version", otroGrado.getVersion(), "cursosIds", List.of(catalogo)), 422);
        assertThat(otroPlan.path("code").asText()).isEqualTo("CURSO_DE_OTRO_GRADO");
        String ficha = "/api/cursos/" + catalogo;
        var registrado = getOk(ficha);
        assertThat(request("PUT", ficha, data("version", registrado.path("version").asLong(),
                "codigo", registrado.path("codigo").asText(), "nombre", "Curso de catálogo", "horasSemanales", 4,
                "gradoAcademicoId", otroGrado.getId()), 409).path("code").asText())
                .isEqualTo("CURSO_EN_PLAN_DE_OTRO_GRADO");

        var creada = postOk("/api/asignaciones-cursos", entrada);
        var aula = postOk("/api/aulas", data("codigo", "AULA1", "nombre", "Aula de prueba",
                "tipoAula", "AULA_REGULAR", "capacidad", 35));
        var bloque = data("asignacionCursoId", creada.path("id").asLong(), "aulaId", aula.path("id").asLong(),
                "diaSemana", "LUNES", "horaInicio", "08:00", "horaFin", "09:00");
        var horario = postOk("/api/horarios", bloque);
        assertThat(horario.path("cursoId").asLong()).isEqualTo(catalogo);
        request("PUT", PLAN, data("version", version, "cursosIds", List.of(course.getId())), 409);
        assertThat(request("PUT", PLAN, data("version", guardado.path("version").asLong(),
                "cursosIds", List.of(course.getId()), "motivo", "Corrección del catálogo"), 409).path("code").asText())
                .isEqualTo("CURSO_CON_ASIGNACIONES");
        assertThat(request("DELETE", "/api/cursos/" + catalogo, null, 409).path("code").asText())
                .isEqualTo("CURSO_EN_USO");

        var externa = new AsignacionCurso();
        externa.setCurso(em.getReference(Curso.class, curso()));
        externa.setSeccion(section);
        externa.setDocente(teacher);
        em.persist(externa);
        bloque.put("asignacionCursoId", externa.getId());
        assertThat(request("POST", "/api/horarios", bloque, 409).path("code").asText())
                .isEqualTo("CURSO_FUERA_DEL_PLAN");
    }

    @Test void previsualizaElMismoGradoYGuardaNombresHorasYAuditoriaPorAnio() throws Exception {
        var anterior = new AnioAcademico();
        anterior.setAnio(2089);
        anterior.setFechaInicio(LocalDate.of(2089, 3, 1));
        anterior.setFechaFin(LocalDate.of(2089, 12, 31));
        anterior.setEstado(EstadoAnioAcademico.CERRADO);
        em.persist(anterior);
        var gradoAnterior = new GradoAcademico();
        gradoAnterior.setNumero(1);
        gradoAnterior.setCapacidadDefault(25);
        gradoAnterior.setAnioAcademico(anterior);
        gradoAnterior.setPlanDefinido(true);
        gradoAnterior.getCursosPlan().put(course, new CursoEnPlan("Matemática histórica", 5));
        gradoAnterior.definirCompetencias(course.getId(), List.of(competence));
        em.persist(gradoAnterior);
        var seccionAnterior = new Seccion();
        seccionAnterior.setNombre("A");
        seccionAnterior.setGrado(1);
        seccionAnterior.setAnioAcademico(2089);
        seccionAnterior.setCapacidadMaxima(25);
        em.persist(seccionAnterior);
        var asignacionAnterior = new AsignacionCurso();
        asignacionAnterior.setCurso(course);
        asignacionAnterior.setDocente(teacher);
        asignacionAnterior.setSeccion(seccionAnterior);
        em.persist(asignacionAnterior);
        var taller = new Curso();
        taller.setCodigo("TAL" + key());
        taller.setNombre("Taller de lectura");
        taller.setHorasSemanales(3);
        taller.setGradoAcademico(gradoAnterior);
        em.persist(taller);
        gradoAnterior.incluirCurso(taller);
        gradoAnterior.definirCompetencias(taller.getId(), List.of(competence));
        grade.setPlanDefinido(true);
        em.flush();

        var consulta = getOk(PLAN + "/anterior");
        assertThat(consulta.path("anioOrigen").asInt()).isEqualTo(2089);
        assertThat(consulta.path("cursos").get(0).path("nombre").asText()).isEqualTo("Matemática histórica");
        assertThat(consulta.path("cursos").get(0).path("horasSemanales").asInt()).isEqualTo(5);
        assertThat(consulta.path("cursos").get(1).path("id").asLong()).isEqualTo(taller.getId());
        assertThat(consulta.path("cursos").get(1).path("competencias").findValuesAsText("codigo"))
                .containsExactly(competence.getCodigo());
        var destino = getOk(PLAN);
        long version = destino.path("version").asLong();
        assertThat(destino.path("cursos").get(0).path("nombre").asText()).isEqualTo(course.getNombre());
        var datos = data("version", version, "cursos", List.of(
                data("id", course.getId(), "nombre", "  Matemática y razonamiento  ", "horasSemanales", 6),
                data("id", taller.getId(), "nombre", taller.getNombre(), "horasSemanales", taller.getHorasSemanales(),
                        "competenciasIds", List.of(competence.getId()))));
        assertThat(request("PUT", PLAN, datos, 422).path("code").asText()).isEqualTo("MOTIVO_EDICION_PLAN_REQUERIDO");
        datos.put("motivo", "Adecuación del currículo");
        var guardado = putOk(PLAN, datos);
        assertThat(guardado.path("version").asLong()).isGreaterThan(version);
        em.clear();
        assertThat(getOk(PLAN)).isEqualTo(guardado);
        assertThat(getOk(PLAN + "/anterior")).isEqualTo(consulta);
        assertThat(getOk("/api/cursos/" + course.getId()).path("nombre").asText()).isEqualTo(course.getNombre());
        var cursosDocente = comoRol("DOCENTE", teacher.getPersona(), "GET",
                "/api/asistencias/asignaciones?periodo=TODOS", null, 200);
        assertThat(cursosDocente.findValuesAsText("curso"))
                .containsExactly("Matemática y razonamiento", "Matemática histórica");
        assertThat(getOk("/api/asignaciones-cursos/" + assignment.getId()).path("curso").asText())
                .isEqualTo("Matemática y razonamiento");
        assertThat(guardado.path("cursos").get(0).path("nombre").asText()).isEqualTo("Matemática y razonamiento");
        assertThat(guardado.path("cursos").get(0).path("horasSemanales").asInt()).isEqualTo(6);
        assertThat(guardado.path("cursos").get(1).path("codigo").asText()).isEqualTo(taller.getCodigo());
        assertThat(guardado.path("cursos").get(0).path("competencias")).isEmpty();
        assertThat(guardado.path("cursos").get(1).path("competencias").findValuesAsText("codigo"))
                .containsExactly(competence.getCodigo());
        var registros = getOk("/api/auditoria/registros?modulo=ACADEMICO&size=50").path("content");
        assertThat(registros.findValuesAsText("nuevoValor")).anyMatch(cambio -> cambio.contains("Matemática y razonamiento · 6 h/semana"));

        var anteriorInexistente = getOk("/api/planes-estudios/2089/1/anterior");
        assertThat(anteriorInexistente.path("cursos")).isEmpty();
    }

    @Test void cambiaSoloLasCompetenciasConMotivoYNoModificaLaPlantillaGeneral() throws Exception {
        var guardado = putOk(PLAN, data("version", getOk(PLAN).path("version").asLong(),
                "cursosIds", List.of(course.getId())));
        assertThat(guardado.path("cursos").get(0).path("competencias").findValuesAsText("codigo"))
                .containsExactly(competence.getCodigo());
        var nueva = new Competencia();
        nueva.setCodigo("ESTAD" + key());
        nueva.setNombre("Interpreta datos");
        em.persist(nueva);

        long version = guardado.path("version").asLong();
        var curso = data("id", course.getId(), "nombre", course.getNombre(), "horasSemanales", course.getHorasSemanales(),
                "competenciasIds", List.of(nueva.getId()));
        var cambio = data("version", version, "cursos", List.of(curso));
        assertThat(request("PUT", PLAN, cambio, 422).path("code").asText()).isEqualTo("MOTIVO_EDICION_PLAN_REQUERIDO");
        cambio.put("motivo", "Seguimiento estadístico este año");
        curso.put("competenciasIds", List.of(Long.MAX_VALUE));
        assertThat(request("PUT", PLAN, cambio, 422).path("code").asText()).isEqualTo("COMPETENCIA_INVALIDA_EN_PLAN");
        curso.put("competenciasIds", List.of(nueva.getId(), nueva.getId()));
        assertThat(request("PUT", PLAN, cambio, 422).path("code").asText()).isEqualTo("COMPETENCIA_REPETIDA_EN_PLAN");
        curso.put("competenciasIds", List.of(nueva.getId()));
        var actualizado = putOk(PLAN, cambio);
        assertThat(actualizado.path("version").asLong()).isGreaterThan(version);
        em.clear();
        assertThat(getOk(PLAN)).isEqualTo(actualizado);
        assertThat(getOk("/api/cursos-competencias/curso/" + course.getId()).findValuesAsText("competenciaCodigo"))
                .containsExactly(competence.getCodigo());
        var rutaDocente = "/api/evaluaciones/competencias?idAsignacionCurso=" + assignment.getId();
        assertThat(getOk(rutaDocente).findValuesAsText("codigo")).containsExactly(nueva.getCodigo());
        var auditoria = getOk("/api/auditoria/registros?modulo=ACADEMICO&size=50").path("content");
        assertThat(auditoria.findValuesAsText("nuevoValor")).anyMatch(valor -> valor.contains("Competencias: " + nueva.getCodigo()));
    }

    @Test void corrigeElPlanVigenteConMotivoYDejaLaAuditoriaConsultable() throws Exception {
        long catalogo = curso();
        var actual = getOk(PLAN);
        long version = actual.path("version").asLong();
        assertThat(actual.path("inicializacion").asBoolean()).isTrue();
        String body = json.writeValueAsString(data("version", version, "cursosIds", List.of(course.getId(), catalogo)));
        mvc.perform(put(PLAN).contentType("application/json").content(body)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR")))).andExpect(status().isForbidden());
        var guardado = putOk(PLAN, data("version", version, "cursosIds", List.of(course.getId(), catalogo)));
        assertThat(guardado.path("editable").asBoolean()).isTrue();
        assertThat(guardado.path("definido").asBoolean()).isTrue();
        long guardada = guardado.path("version").asLong();
        assertThat(request("PUT", PLAN, data("version", guardada, "cursosIds", List.of(course.getId()),
                "motivo", "   "), 422).path("code").asText()).isEqualTo("MOTIVO_EDICION_PLAN_REQUERIDO");
        assertThat(getOk(PLAN)).isEqualTo(guardado);
        assertThat(putOk(PLAN, data("version", guardada, "cursosIds", List.of(catalogo, course.getId()))))
                .isEqualTo(guardado);

        var actualizado = putOk(PLAN, data("version", guardada, "cursosIds", List.of(course.getId()),
                "motivo", "  Rectificación del curso en el plan  "));
        assertThat(actualizado.path("version").asLong()).isGreaterThan(guardada);
        assertThat(actualizado.path("cursos")).hasSize(1);
        assertThat(actualizado.path("cursos").get(0).path("horasSemanales").asInt()).isEqualTo(4);
        assertThat(request("PUT", PLAN, data("version", guardado.path("version").asLong(),
                "cursosIds", List.of(course.getId(), catalogo), "motivo", "Versión anterior"), 409)
                .path("code").asText()).isEqualTo("VERSION_DESACTUALIZADA");
        assertThat(request("PUT", PLAN, data("version", actualizado.path("version").asLong(), "cursosIds", List.of(),
                "motivo", "Se intentó dejar vacío"), 422).path("code").asText()).isEqualTo("PLAN_ESTUDIOS_VACIO");

        var registros = getOk("/api/auditoria/registros?modulo=ACADEMICO&size=50").path("content");
        var exitosos = registros.findValuesAsText("motivo");
        assertThat(exitosos).contains("Rectificación del curso en el plan");
        var auditoria = java.util.stream.StreamSupport.stream(registros.spliterator(), false)
                .filter(registro -> registro.path("motivo").asText().equals("Rectificación del curso en el plan"))
                .findFirst().orElseThrow();
        assertThat(auditoria.path("accion").asText()).isEqualTo("ACTUALIZAR_PLAN_ESTUDIOS");
        assertThat(auditoria.path("valorAnterior").asText()).contains("2090 · 1.° grado", "Curso de catálogo");
        assertThat(auditoria.path("nuevoValor").asText()).contains(course.getCodigo()).doesNotContain("Curso de catálogo");

        year.setEstado(EstadoAnioAcademico.CERRADO);
        em.flush();
        assertThat(getOk(PLAN).path("editable").asBoolean()).isFalse();
        assertThat(request("PUT", PLAN, data("version", actualizado.path("version").asLong(),
                "cursosIds", List.of(course.getId(), catalogo), "motivo", "Cambio en plan histórico"), 409)
                .path("code").asText()).isEqualTo("PLAN_ESTUDIOS_BLOQUEADO");
    }

    private long curso() throws Exception {
        return postOk("/api/cursos", data("codigo", "PLAN" + key(), "nombre", "Curso de catálogo",
                "horasSemanales", 4)).path("id").asLong();
    }
}
