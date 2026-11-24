// inscripcion cursos
package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.CursoEnPlan;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.TipoAula;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mis cursos (temporal): los del plan del grado de la matrícula activa, con lo que aporta la sección. */
class MisCursosHttpTest extends ApiIntegrationSupport {

    private static final String RUTA = "/api/alumnos/me/cursos";

    private JsonNode como(String rol, Persona persona, int esperado) throws Exception {
        var respuesta = mvc.perform(get(RUTA).with(jwt()
                        .jwt(j -> j.subject(rol + key()).claim("personaId", persona.getId()))
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rol),
                                new SimpleGrantedAuthority("ACADEMICO_LEER")))))
                .andExpect(status().is(esperado)).andReturn().getResponse();
        return respuesta.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(respuesta.getContentAsByteArray());
    }

    private Curso curso(String nombre, boolean enPlan) {
        var curso = new Curso(); curso.setCodigo("C" + key()); curso.setNombre(nombre);
        curso.setHorasSemanales(2); curso.setGradoAcademico(grade); em.persist(curso);
        if (enPlan) grade.incluirCurso(curso);
        return curso;
    }

    @Test void muestraExactamenteLosCursosDelPlanConDocenteHorarioYAula() throws Exception {
        curso("Comunicacion", true);
        curso("Taller fuera del plan", false);
        grade.setPlanDefinido(true);
        grade.getCursosPlan().put(course, new CursoEnPlan("Matemática del año", 4));
        var aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula 101"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        var bloque = new Horario(); bloque.setAsignacionCurso(assignment); bloque.setAula(aula);
        bloque.setDiaSemana(DiaSemana.LUNES); bloque.setHoraInicio(LocalTime.of(8, 0)); bloque.setHoraFin(LocalTime.of(9, 30));
        em.persist(bloque); em.flush();

        var respuesta = como("ALUMNO", student.getPersona(), 200);
        assertThat(respuesta.path("anioAcademico").asInt()).isEqualTo(2090);
        assertThat(respuesta.path("seccion").asText()).isEqualTo("1° Grado A");
        var cursos = StreamSupport.stream(respuesta.path("cursos").spliterator(), false).toList();
        assertThat(cursos).extracting(c -> c.path("curso").asText()).containsExactly("Comunicacion", "Matemática del año");

        var conDocente = cursos.get(1);
        assertThat(conDocente.path("docente").asText()).isEqualTo(teacher.getPersona().nombreCompleto());
        assertThat(conDocente.path("horario").asText()).isEqualTo("Lun 08:00–09:30");
        assertThat(conDocente.path("aula").asText()).isEqualTo("Aula 101");
        var sinAsignar = cursos.get(0);
        assertThat(sinAsignar.path("docente").isNull()).isTrue();
        assertThat(sinAsignar.path("horario").isNull()).isTrue();
    }

    @Test void sinMatriculaActivaDevuelveUnaListaVacia() throws Exception {
        var otro = new Alumno(); otro.setPersona(person()); otro.setCodigoAlumno("EST" + key()); em.persist(otro); em.flush();

        var respuesta = como("ALUMNO", otro.getPersona(), 200);
        assertThat(respuesta.path("anioAcademico").isNull()).isTrue();
        assertThat(respuesta.path("cursos")).isEmpty();
    }

    @Test void soloLoConsultaElEstudiante() throws Exception {
        como("APODERADO", guardian.getPersona(), 403);
        como("DOCENTE", teacher.getPersona(), 403);
    }
}
