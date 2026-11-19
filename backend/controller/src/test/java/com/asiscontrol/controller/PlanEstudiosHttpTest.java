package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanEstudiosHttpTest extends ApiIntegrationSupport {
    private static final String PLAN = "/api/planes-estudios/2090/1";

    @Test void agregaCursoDelCatalogoPrestaElMismoCursoAOtroGradoYRechazaCambiosInconsistentes() throws Exception {
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
        assertThat(guardado.path("cursos").findValues("id")).hasSize(2);
        assertThat(guardado.path("version").asLong()).isGreaterThan(version);
        var otroPlan = putOk("/api/planes-estudios/2090/2",
                data("version", otroGrado.getVersion(), "cursosIds", List.of(catalogo)));
        assertThat(otroPlan.path("cursos").get(0).path("id").asLong()).isEqualTo(catalogo);

        var creada = postOk("/api/asignaciones-cursos", entrada);
        var aula = postOk("/api/aulas", data("codigo", "AULA1", "nombre", "Aula de prueba",
                "tipoAula", "AULA_REGULAR", "capacidad", 35));
        var bloque = data("asignacionCursoId", creada.path("id").asLong(), "aulaId", aula.path("id").asLong(),
                "diaSemana", "LUNES", "horaInicio", "08:00", "horaFin", "09:00");
        var horario = postOk("/api/horarios", bloque);
        assertThat(horario.path("cursoId").asLong()).isEqualTo(catalogo);
        request("PUT", PLAN, data("version", version, "cursosIds", List.of(course.getId())), 409);
        assertThat(request("PUT", PLAN, data("version", guardado.path("version").asLong(),
                "cursosIds", List.of(course.getId())), 409).path("code").asText()).isEqualTo("CURSO_CON_ASIGNACIONES");
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

    @Test void elAnoActivoSoloAdmiteRegistrarUnPlanInicialPorUnAdministrador() throws Exception {
        long catalogo = curso();
        var actual = getOk(PLAN);
        long version = actual.path("version").asLong();
        assertThat(actual.path("inicializacion").asBoolean()).isTrue();
        String body = json.writeValueAsString(data("version", version, "cursosIds", List.of(course.getId(), catalogo)));
        mvc.perform(put(PLAN).contentType("application/json").content(body)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR")))).andExpect(status().isForbidden());
        var guardado = putOk(PLAN, data("version", version, "cursosIds", List.of(course.getId(), catalogo)));
        assertThat(guardado.path("editable").asBoolean()).isFalse();
        assertThat(request("PUT", PLAN, data("version", guardado.path("version").asLong(),
                "cursosIds", List.of(course.getId())), 409).path("code").asText()).isEqualTo("PLAN_ESTUDIOS_BLOQUEADO");
        year.setEstado(EstadoAnioAcademico.CERRADO);
        em.flush();
        assertThat(getOk(PLAN).path("editable").asBoolean()).isFalse();
        request("PUT", PLAN, data("version", guardado.path("version").asLong(), "cursosIds", List.of()), 409);
    }

    private long curso() throws Exception {
        return postOk("/api/cursos", data("codigo", "PLAN" + key(), "nombre", "Curso de catálogo",
                "horasSemanales", 4)).path("id").asLong();
    }
}
