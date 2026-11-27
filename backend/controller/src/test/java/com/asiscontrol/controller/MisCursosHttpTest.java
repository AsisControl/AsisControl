// inscripcion cursos
package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.service.InscripcionCursoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mis cursos: las inscripciones del estudiante, vigentes (año activo) y pasadas (años cerrados). */
class MisCursosHttpTest extends ApiIntegrationSupport {

    private static final String RUTA = "/api/alumnos/me/cursos";

    @Autowired InscripcionCursoService inscripcionService;

    private JsonNode como(String rol, Persona persona, String periodo, int esperado) throws Exception {
        var peticion = get(RUTA);
        if (periodo != null) peticion = peticion.param("periodo", periodo);
        var respuesta = mvc.perform(peticion.with(jwt()
                        .jwt(j -> j.subject(rol + key()).claim("personaId", persona.getId()))
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rol),
                                new SimpleGrantedAuthority("ACADEMICO_LEER")))))
                .andExpect(status().is(esperado)).andReturn().getResponse();
        return respuesta.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(respuesta.getContentAsByteArray());
    }

    private List<JsonNode> cursos(JsonNode respuesta) {
        return StreamSupport.stream(respuesta.path("cursos").spliterator(), false).toList();
    }

    private Matricula matriculaActual() {
        return em.createQuery("select m from Matricula m where m.alumno = :alumno", Matricula.class)
                .setParameter("alumno", student).getSingleResult();
    }

    private Curso curso(GradoAcademico grado, String nombre, boolean enPlan) {
        var curso = new Curso(); curso.setCodigo("C" + key()); curso.setNombre(nombre);
        curso.setHorasSemanales(2); curso.setGradoAcademico(grado); em.persist(curso);
        if (enPlan) grado.incluirCurso(curso);
        return curso;
    }

    /** Matrícula FINALIZADA del estudiante en 2089, un año cerrado con su propio plan. */
    private Matricula matriculaAnterior() {
        var anio = new AnioAcademico(); anio.setAnio(2089); anio.setFechaInicio(LocalDate.of(2089, 1, 1));
        anio.setFechaFin(LocalDate.of(2089, 12, 31)); anio.setEstado(EstadoAnioAcademico.CERRADO); em.persist(anio);
        var grado = new GradoAcademico(); grado.setAnioAcademico(anio); grado.setNumero(1);
        grado.setCapacidadDefault(30); grado.setPlanDefinido(true); em.persist(grado);
        curso(grado, "Historia", true);
        var seccion = new Seccion(); seccion.setNombre("B"); seccion.setGrado(1); seccion.setAnioAcademico(2089);
        seccion.setCapacidadMaxima(30); em.persist(seccion);
        var solicitud = new SolicitudMatricula(); solicitud.setAlumno(student); solicitud.setSeccion(seccion);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA); em.persist(solicitud);
        var matricula = new Matricula(); matricula.setAlumno(student); matricula.setSeccion(seccion);
        matricula.setSolicitudMatricula(solicitud); matricula.setCodigoMatricula("MAT" + key());
        matricula.setAnioAcademico(2089); matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.FINALIZADA); em.persist(matricula);
        em.flush();
        return matricula;
    }

    @Test void muestraLosCursosInscritosConDocenteHorarioYAula() throws Exception {
        curso(grade, "Comunicacion", true);
        var aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula 101"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        var bloque = new Horario(); bloque.setAsignacionCurso(assignment); bloque.setAula(aula);
        bloque.setDiaSemana(DiaSemana.LUNES); bloque.setHoraInicio(LocalTime.of(8, 0)); bloque.setHoraFin(LocalTime.of(9, 30));
        em.persist(bloque); em.flush();
        inscripcionService.inscribir(matriculaActual());
        curso(grade, "Taller agregado después", true);
        em.flush();

        var cursos = cursos(como("ALUMNO", student.getPersona(), null, 200));
        assertThat(cursos).extracting(c -> c.path("curso").asText()).containsExactly("Comunicacion", "Matematica");
        var conDocente = cursos.get(1);
        assertThat(conDocente.path("docente").asText()).isEqualTo(teacher.getPersona().nombreCompleto());
        assertThat(conDocente.path("horario").asText()).isEqualTo("Lun 08:00–09:30");
        assertThat(conDocente.path("aula").asText()).isEqualTo("Aula 101");
        assertThat(conDocente.path("seccion").asText()).isEqualTo("1° Grado A");
        assertThat(conDocente.path("anioAcademico").asInt()).isEqualTo(2090);
        assertThat(conDocente.path("vigente").asBoolean()).isTrue();
        var sinAsignar = cursos.get(0);
        assertThat(sinAsignar.path("docente").isNull()).isTrue();
        assertThat(sinAsignar.path("horario").isNull()).isTrue();
    }

    @Test void filtraVigentesPasadosYTodos() throws Exception {
        inscripcionService.inscribir(matriculaActual());
        inscripcionService.inscribir(matriculaAnterior());

        assertThat(cursos(como("ALUMNO", student.getPersona(), "VIGENTES", 200)))
                .extracting(c -> c.path("curso").asText()).containsExactly("Matematica");
        var pasados = cursos(como("ALUMNO", student.getPersona(), "PASADOS", 200));
        assertThat(pasados).extracting(c -> c.path("curso").asText()).containsExactly("Historia");
        assertThat(pasados.get(0).path("vigente").asBoolean()).isFalse();
        assertThat(pasados.get(0).path("anioAcademico").asInt()).isEqualTo(2089);
        assertThat(cursos(como("ALUMNO", student.getPersona(), "TODOS", 200)))
                .extracting(c -> c.path("curso").asText()).containsExactly("Matematica", "Historia");
    }

    @Test void sinMatriculaOConMatriculaRetiradaDevuelveUnaListaVacia() throws Exception {
        var otro = new Alumno(); otro.setPersona(person()); otro.setCodigoAlumno("EST" + key()); em.persist(otro); em.flush();
        assertThat(como("ALUMNO", otro.getPersona(), null, 200).path("cursos")).isEmpty();

        var matricula = matriculaActual();
        inscripcionService.inscribir(matricula);
        matricula.setEstado(EstadoMatricula.RETIRADA);
        inscripcionService.desactivar(matricula);
        em.flush();
        assertThat(como("ALUMNO", student.getPersona(), "TODOS", 200).path("cursos")).isEmpty();
    }

    @Test void soloLoConsultaElEstudiante() throws Exception {
        como("APODERADO", guardian.getPersona(), null, 403);
        como("DOCENTE", teacher.getPersona(), null, 403);
    }
}
