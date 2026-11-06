// asistencia alumnos
package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoPeriodoAcademico;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU14 fase 4: solo lo finalizado es oficial, y el docente ve sus secciones asignadas. */
class AsistenciaVisibilidadHttpTest extends ApiIntegrationSupport {

    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
    private Horario horario;

    @BeforeEach void datos() {
        var aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        horario = new Horario(); horario.setAsignacionCurso(assignment); horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.LUNES); horario.setHoraInicio(LocalTime.of(8, 0)); horario.setHoraFin(LocalTime.of(10, 0));
        em.persist(horario);
    }

    private long inasistencia(EstadoSesionClase estado, int horaInicio) {
        var sesion = new SesionClase(); sesion.setAsignacionCurso(assignment); sesion.setHorario(horario);
        sesion.setFecha(hoy); sesion.setHoraInicio(LocalTime.of(horaInicio, 0)); sesion.setHoraFin(LocalTime.of(horaInicio + 1, 0));
        sesion.setEstado(estado); em.persist(sesion);
        var fila = new AsistenciaAlumno(); fila.setAlumno(student); fila.setAsignacionCurso(assignment);
        fila.setSesionClase(sesion); fila.setDocenteRegistrador(teacher); fila.setFechaRegistro(hoy);
        fila.setHoraRegistro(LocalTime.of(horaInicio, 5)); fila.setEstadoAsistencia(EstadoAsistencia.INASISTENCIA);
        em.persist(fila); em.flush();
        return fila.getId();
    }

    private JsonNode comoRol(String rol, Persona persona, String ruta, int esperado) throws Exception {
        var respuesta = mvc.perform(get(ruta).with(jwt().jwt(j -> j.subject(rol + key()).claim("personaId", persona.getId()))
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + rol), new SimpleGrantedAuthority("ASISTENCIA_LEER")))))
                .andExpect(status().is(esperado)).andReturn().getResponse();
        return json.readTree(respuesta.getContentAsByteArray());
    }

    private long inasistenciasDeHoy() throws Exception {
        for (JsonNode indicador : getOk("/api/dashboard").path("indicadores"))
            if (indicador.path("codigo").asText().equals("INASISTENCIAS_HOY")) return indicador.path("valor").asLong();
        throw new AssertionError("El dashboard no informa INASISTENCIAS_HOY");
    }

    @Test void borradorNoApareceEnConsultasNiSePuedeJustificar() throws Exception {
        long tomada = inasistencia(EstadoSesionClase.TOMADA, 8);
        long borrador = inasistencia(EstadoSesionClase.BORRADOR, 11);
        String ruta = "/api/asistencias/alumnos/";

        var lista = getOk("/api/asistencias/alumnos?alumnoId=" + student.getId()).path("content");
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).path("id").asLong()).isEqualTo(tomada);
        getOk(ruta + tomada);
        request("GET", ruta + borrador, null, 404);

        comoRol("ALUMNO", student.getPersona(), ruta + tomada, 200);
        comoRol("ALUMNO", student.getPersona(), ruta + borrador, 403);
        comoRol("APODERADO", guardian.getPersona(), ruta + tomada, 200);
        comoRol("APODERADO", guardian.getPersona(), ruta + borrador, 403);
        comoRol("DOCENTE", teacher.getPersona(), ruta + tomada, 200);
        comoRol("DOCENTE", teacher.getPersona(), ruta + borrador, 403);

        request("POST", "/api/justificaciones-asistencia", data("asistenciaAlumnoId", borrador,
                "motivo", "Cita medica", "archivoIds", Set.of()), 404);
    }

    @Test void dashboardSoloCuentaAsistenciasFinalizadas() throws Exception {
        long inicial = inasistenciasDeHoy();
        inasistencia(EstadoSesionClase.BORRADOR, 8);
        assertThat(inasistenciasDeHoy()).isEqualTo(inicial);
        inasistencia(EstadoSesionClase.TOMADA, 11);
        assertThat(inasistenciasDeHoy()).isEqualTo(inicial + 1);
    }

    @Test void docenteVeSoloSusSeccionesAsignadas() throws Exception {
        String ruta = "/api/asistencias/asignaciones";
        var propias = comoRol("DOCENTE", teacher.getPersona(), ruta, 200);
        assertThat(propias).hasSize(1);
        assertThat(propias.get(0).path("id").asLong()).isEqualTo(assignment.getId());
        assertThat(propias.get(0).path("curso").asText()).isEqualTo("Matematica");
        assertThat(propias.get(0).path("seccion").asText()).isEqualTo("1° Grado A");
        assertThat(propias.get(0).path("anioAcademico").asInt()).isEqualTo(2090);

        var ajeno = new Docente(); ajeno.setPersona(person()); ajeno.setCodigoDocente("DOC" + key());
        ajeno.setFechaIngreso(LocalDate.now().minusYears(1)); ajeno.setEspecialidad(Especialidad.MATEMATICA); em.persist(ajeno); em.flush();
        assertThat(comoRol("DOCENTE", ajeno.getPersona(), ruta, 200)).isEmpty();
    }

    @Test void misCursosResumeEstudiantesHorarioAulaYPeriodo() throws Exception {
        var periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Período III"); periodo.setOrden(3);
        periodo.setFechaInicio(LocalDate.of(2090, 8, 1)); periodo.setFechaFin(LocalDate.of(2090, 10, 31));
        periodo.setEstado(EstadoPeriodoAcademico.ACTIVO); em.persist(periodo);
        for (var dia : List.of(DiaSemana.MIERCOLES, DiaSemana.VIERNES)) {
            var bloque = new Horario(); bloque.setAsignacionCurso(assignment); bloque.setAula(horario.getAula());
            bloque.setDiaSemana(dia); bloque.setHoraInicio(LocalTime.of(8, 0)); bloque.setHoraFin(LocalTime.of(10, 0));
            em.persist(bloque);
        }
        var tarde = new Horario(); tarde.setAsignacionCurso(assignment); tarde.setAula(horario.getAula());
        tarde.setDiaSemana(DiaSemana.MARTES); tarde.setHoraInicio(LocalTime.of(13, 30)); tarde.setHoraFin(LocalTime.of(15, 0));
        em.persist(tarde); em.flush();

        var curso = comoRol("DOCENTE", teacher.getPersona(), "/api/asistencias/asignaciones", 200).get(0);
        assertThat(curso.path("estudiantes").asInt()).isEqualTo(1);
        assertThat(curso.path("horario").asText()).isEqualTo("Lun, Mié, Vie 08:00 · Mar 13:30");
        assertThat(curso.path("aula").asText()).isEqualTo("Aula");
        assertThat(curso.path("periodo").asText()).isEqualTo("Período III");
    }

    private AsignacionCurso cursoEnAnio(int numero, EstadoAnioAcademico estado) {
        var anio = new AnioAcademico(); anio.setAnio(numero); anio.setFechaInicio(LocalDate.of(numero, 1, 1));
        anio.setFechaFin(LocalDate.of(numero, 12, 31)); anio.setEstado(estado); em.persist(anio);
        var seccion = new Seccion(); seccion.setNombre("B"); seccion.setGrado(2); seccion.setAnioAcademico(numero);
        seccion.setCapacidadMaxima(30); em.persist(seccion);
        var asignacion = new AsignacionCurso(); asignacion.setCurso(course); asignacion.setDocente(teacher);
        asignacion.setSeccion(seccion); em.persist(asignacion); em.flush();
        return asignacion;
    }

    @Test void misCursosFiltraVigentesPasadosYTodosSinMostrarBorradores() throws Exception {
        var pasado = cursoEnAnio(2071, EstadoAnioAcademico.CERRADO);
        cursoEnAnio(2072, EstadoAnioAcademico.BORRADOR);
        String ruta = "/api/asistencias/asignaciones";

        var vigentes = comoRol("DOCENTE", teacher.getPersona(), ruta, 200);
        assertThat(vigentes).hasSize(1);
        assertThat(vigentes.get(0).path("id").asLong()).isEqualTo(assignment.getId());
        assertThat(vigentes.get(0).path("vigente").asBoolean()).isTrue();

        var pasados = comoRol("DOCENTE", teacher.getPersona(), ruta + "?periodo=PASADOS", 200);
        assertThat(pasados).hasSize(1);
        assertThat(pasados.get(0).path("id").asLong()).isEqualTo(pasado.getId());
        assertThat(pasados.get(0).path("vigente").asBoolean()).isFalse();

        var todos = comoRol("DOCENTE", teacher.getPersona(), ruta + "?periodo=TODOS", 200);
        assertThat(todos).hasSize(2);
        assertThat(todos.get(0).path("id").asLong()).isEqualTo(assignment.getId());
        assertThat(todos.get(1).path("id").asLong()).isEqualTo(pasado.getId());

        comoRol("DOCENTE", teacher.getPersona(), ruta + "?periodo=OTRO", 400);
    }
}
