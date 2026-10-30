package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ASISCTRL-97: filtros ejecutados contra H2 a través de la API y la seguridad real de Spring. */
class HorarioFiltrosHttpTest extends ApiIntegrationSupport {

    private Aula aulaA;
    private Horario primero;
    private Horario segundo;
    private Horario tercero;
    private Horario otroAnio;

    @BeforeEach
    void prepararHorarios() {
        aulaA = aula("A");
        Aula aulaB = aula("B");
        primero = horario(assignment, aulaA, DiaSemana.LUNES, 8);
        segundo = horario(assignment, aulaB, DiaSemana.MARTES, 10);

        Docente otroDocente = new Docente();
        otroDocente.setPersona(person());
        otroDocente.setCodigoDocente("DOC" + key());
        otroDocente.setEspecialidad(Especialidad.MATEMATICA);
        otroDocente.setFechaIngreso(LocalDate.of(2025, 1, 1));
        em.persist(otroDocente);
        Curso otroCurso = curso(grade);
        AsignacionCurso otraAsignacion = asignacion(
                seccion(2090, "B"), otroCurso, otroDocente);
        tercero = horario(otraAsignacion, aulaA, DiaSemana.LUNES, 11);

        AnioAcademico siguiente = new AnioAcademico();
        siguiente.setAnio(2091);
        siguiente.setFechaInicio(LocalDate.of(2091, 1, 1));
        siguiente.setFechaFin(LocalDate.of(2091, 12, 31));
        siguiente.setEstado(EstadoAnioAcademico.BORRADOR);
        em.persist(siguiente);
        GradoAcademico siguienteGrado = new GradoAcademico();
        siguienteGrado.setAnioAcademico(siguiente);
        siguienteGrado.setNumero(1);
        siguienteGrado.setCapacidadDefault(30);
        em.persist(siguienteGrado);
        otroAnio = horario(asignacion(seccion(2091, "A"), curso(siguienteGrado), otroDocente),
                aulaB, DiaSemana.MIERCOLES, 9);

        horario(assignment, aulaA, DiaSemana.JUEVES, 8).setActivo(false);
        AsignacionCurso inactiva = asignacion(section, otroCurso, otroDocente);
        inactiva.setActivo(false);
        horario(inactiva, aulaA, DiaSemana.VIERNES, 8);
        em.flush();
        em.clear();
    }

    @Test
    void conservaListadoSinFiltrosYOmiteHorariosOAsignacionesInactivos() throws Exception {
        JsonNode respuesta = getOk("/api/horarios?sort=id,asc");
        assertThat(respuesta.path("totalElements").asLong()).isEqualTo(4);
        assertThat(respuesta.path("content").findValuesAsText("id"))
                .containsExactly(id(primero), id(segundo), id(tercero), id(otroAnio));
        JsonNode bloque = respuesta.path("content").get(0);
        assertThat(bloque.path("curso").asText()).isEqualTo(course.getNombre());
        assertThat(bloque.path("seccionId").asLong()).isEqualTo(section.getId());
        assertThat(bloque.path("docenteId").asLong()).isEqualTo(teacher.getId());
        assertThat(bloque.path("aulaId").asLong()).isEqualTo(aulaA.getId());
        assertThat(bloque.path("diaSemana").asText()).isEqualTo("LUNES");
        assertThat(bloque.path("horaInicio").asText()).startsWith("08:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"anioAcademico", "seccionId", "docenteId", "aulaId", "cursoId"})
    void filtraCadaPerspectivaEnLaBaseDeDatos(String filtro) throws Exception {
        String valor = switch (filtro) {
            case "anioAcademico" -> "2090";
            case "seccionId" -> section.getId().toString();
            case "docenteId" -> teacher.getId().toString();
            case "aulaId" -> aulaA.getId().toString();
            default -> course.getId().toString();
        };
        String[] esperados = switch (filtro) {
            case "anioAcademico" -> new String[] {id(primero), id(segundo), id(tercero)};
            case "aulaId" -> new String[] {id(primero), id(tercero)};
            default -> new String[] {id(primero), id(segundo)};
        };
        JsonNode respuesta = getOk("/api/horarios?" + filtro + "=" + valor + "&sort=id,asc");
        assertThat(respuesta.path("content").findValuesAsText("id")).containsExactly(esperados);
        assertThat(respuesta.path("totalElements").asInt()).isEqualTo(esperados.length);
    }

    @Test
    void combinaFiltrosConInterseccionYPaginaDespuesDeFiltrar() throws Exception {
        String filtros = "anioAcademico=2090&seccionId=" + section.getId()
                + "&docenteId=" + teacher.getId() + "&cursoId=" + course.getId();
        JsonNode pagina = getOk("/api/horarios?" + filtros + "&size=1&page=1&sort=id,asc");
        assertThat(pagina.path("totalElements").asInt()).isEqualTo(2);
        assertThat(pagina.path("totalPages").asInt()).isEqualTo(2);
        assertThat(pagina.path("content").findValuesAsText("id")).containsExactly(id(segundo));
        JsonNode interseccion = getOk("/api/horarios?" + filtros + "&aulaId=" + aulaA.getId());
        assertThat(interseccion.path("content").findValuesAsText("id")).containsExactly(id(primero));
    }

    @Test
    void devuelvePaginaVaciaConFiltrosIncompatiblesOSinResultados() throws Exception {
        JsonNode incompatible = getOk("/api/horarios?anioAcademico=2091&seccionId=" + section.getId());
        assertThat(incompatible.path("totalElements").asInt()).isZero();
        assertThat(incompatible.path("content")).isEmpty();
        JsonNode inexistente = getOk("/api/horarios?aulaId=9223372036854775807");
        assertThat(inexistente.path("content")).isEmpty();
    }

    @Test
    void reflejaLaEdicionDelBloqueSinDuplicarloAlRecargar() throws Exception {
        String path = "/api/horarios/" + primero.getId();
        putOk(path, data("asignacionCursoId", assignment.getId(), "aulaId", aulaA.getId(),
                "diaSemana", "LUNES", "horaInicio", "12:00:00", "horaFin", "13:00:00",
                "version", getOk(path).path("version").asLong()));
        JsonNode respuesta = getOk("/api/horarios?seccionId=" + section.getId() + "&aulaId=" + aulaA.getId());
        assertThat(respuesta.path("content").findValuesAsText("id")).containsExactly(id(primero));
        assertThat(respuesta.path("content").get(0).path("horaInicio").asText()).startsWith("12:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"anioAcademico=1999", "anioAcademico=2101", "seccionId=0",
            "docenteId=-1", "aulaId=0", "cursoId=-1", "anioAcademico=texto", "cursoId=texto"})
    void rechazaFiltrosInvalidos(String filtro) throws Exception {
        request("GET", "/api/horarios?" + filtro, null, 400);
    }

    @Test
    void exigeAutenticacionYPermisoAcademico() throws Exception {
        mvc.perform(get("/api/horarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/horarios").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isForbidden());
    }

    private String id(Horario horario) {
        return horario.getId().toString();
    }

    private Aula aula(String nombre) {
        Aula aula = new Aula();
        aula.setCodigo("AULA" + key());
        aula.setNombre(nombre);
        aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30);
        aula.setUbicacion("Pabellon de pruebas");
        em.persist(aula);
        return aula;
    }

    private Seccion seccion(int anio, String nombre) {
        Seccion seccion = new Seccion();
        seccion.setNombre(nombre);
        seccion.setAnioAcademico(anio);
        seccion.setGrado(1);
        seccion.setCapacidadMaxima(30);
        em.persist(seccion);
        return seccion;
    }

    private Curso curso(GradoAcademico grado) {
        Curso curso = new Curso();
        curso.setCodigo("CUR" + key());
        curso.setNombre("Curso de prueba");
        curso.setGradoAcademico(grado);
        curso.setHorasSemanales(4);
        em.persist(curso);
        return curso;
    }

    private AsignacionCurso asignacion(Seccion seccion, Curso curso, Docente docente) {
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setSeccion(seccion);
        asignacion.setCurso(curso);
        asignacion.setDocente(docente);
        em.persist(asignacion);
        return asignacion;
    }

    private Horario horario(AsignacionCurso asignacion, Aula aula, DiaSemana dia, int hora) {
        Horario horario = new Horario();
        horario.setAsignacionCurso(asignacion);
        horario.setAula(aula);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(LocalTime.of(hora, 0));
        horario.setHoraFin(LocalTime.of(hora + 1, 0));
        em.persist(horario);
        return horario;
    }
}
