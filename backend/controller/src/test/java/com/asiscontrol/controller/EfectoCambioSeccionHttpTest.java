package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Calificacion;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.service.CalificacionService;
import com.asiscontrol.service.MisCursosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** OBS-04: tras el cambio de sección se ve el horario y los cursos nuevos, y se conservan asistencia y notas. */
class EfectoCambioSeccionHttpTest extends ApiIntegrationSupport {
    @Autowired HorarioRepository horarios;
    @Autowired MisCursosService misCursos;
    @Autowired CalificacionService calificaciones;

    private Horario horarioAnterior;
    private Horario horarioNuevo;
    private AsistenciaAlumno asistencia;
    private Calificacion calificacion;

    @BeforeEach
    void cambiarDeSeccionConHistorial() throws Exception {
        var destino = new Seccion();
        destino.setNombre("B");
        destino.setGrado(1);
        destino.setAnioAcademico(2090);
        destino.setCapacidadMaxima(5);
        em.persist(destino);
        var asignacionNueva = new AsignacionCurso();
        asignacionNueva.setCurso(course);
        asignacionNueva.setDocente(teacher);
        asignacionNueva.setSeccion(destino);
        em.persist(asignacionNueva);
        horarioAnterior = horario(assignment, "Aula A", DiaSemana.LUNES);
        horarioNuevo = horario(asignacionNueva, "Aula B", DiaSemana.MARTES);
        asistencia = asistencia();
        calificacion = calificacion();
        em.flush();

        Long matricula = em.createQuery("select m.id from Matricula m where m.alumno = :alumno", Long.class)
                .setParameter("alumno", student).getSingleResult();
        String path = "/api/matriculas/" + matricula;
        putOk(path + "/seccion", data("seccionId", destino.getId(),
                "version", getOk(path).path("version").asLong(), "motivo", "Redistribución"));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elHorarioMuestraSoloLaSeccionNueva() {
        assertThat(horarios.buscarDelAlumno(student.getPersona().getId(), 2090))
                .containsExactly(horarioNuevo)
                .doesNotContain(horarioAnterior);
    }

    @Test
    void misCursosTomaSeccionHorarioYAulaNuevos() {
        iniciarSesion("ALUMNO", student.getPersona());

        var respuesta = misCursos.listar();

        assertThat(respuesta.seccion()).isEqualTo("1° Grado B");
        assertThat(respuesta.cursos()).singleElement().satisfies(curso -> {
            assertThat(curso.aula()).isEqualTo("Aula B");
            assertThat(curso.horario()).startsWith("Mar");
        });
    }

    @Test
    void elEstudianteYSuApoderadoConservanLaAsistenciaAnterior() throws Exception {
        for (var sesion : List.of(new Sesion("ALUMNO", student.getPersona()),
                new Sesion("APODERADO", guardian.getPersona()))) {
            var registros = comoRol(sesion.rol(), sesion.persona(), "GET", "/api/asistencias/alumnos", null, 200)
                    .path("content");
            assertThat(registros).hasSize(1);
            assertThat(registros.get(0).path("id").asLong()).isEqualTo(asistencia.getId());
        }
    }

    @Test
    void elEstudianteYSuApoderadoConservanLasNotasAnteriores() {
        for (var sesion : List.of(new Sesion("ALUMNO", student.getPersona()),
                new Sesion("APODERADO", guardian.getPersona()))) {
            iniciarSesion(sesion.rol(), sesion.persona());
            for (Long asignacion : java.util.Arrays.asList(null, assignment.getId())) {
                assertThat(calificaciones.obtenerConsolidado(student.getId(), asignacion))
                        .singleElement()
                        .satisfies(nota -> assertThat(nota.toString()).contains(competence.getNombre()));
            }
        }
        assertThat(em.find(Calificacion.class, calificacion.getId()).getNivelLogro()).isEqualTo(NivelLogro.A);
    }

    private record Sesion(String rol, Persona persona) {}

    private void iniciarSesion(String rol, Persona persona) {
        var token = Jwt.withTokenValue("prueba").header("alg", "none")
                .claim("personaId", persona.getId()).claim("rol", rol).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Horario horario(AsignacionCurso asignacion, String nombreAula, DiaSemana dia) {
        var aula = new Aula();
        aula.setCodigo("AU" + key());
        aula.setNombre(nombreAula);
        aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30);
        aula.setUbicacion("Pabellón A");
        em.persist(aula);
        var horario = new Horario();
        horario.setAsignacionCurso(asignacion);
        horario.setAula(aula);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(10, 0));
        em.persist(horario);
        return horario;
    }

    private AsistenciaAlumno asistencia() {
        var sesion = new SesionClase();
        sesion.setAsignacionCurso(assignment);
        sesion.setHorario(horarioAnterior);
        sesion.setFecha(LocalDate.now().minusDays(7));
        sesion.setHoraInicio(horarioAnterior.getHoraInicio());
        sesion.setHoraFin(horarioAnterior.getHoraFin());
        sesion.setEstado(EstadoSesionClase.TOMADA);
        em.persist(sesion);
        var registro = new AsistenciaAlumno();
        registro.setAlumno(student);
        registro.setAsignacionCurso(assignment);
        registro.setSesionClase(sesion);
        registro.setFechaRegistro(sesion.getFecha());
        registro.setHoraRegistro(sesion.getHoraInicio());
        registro.setEstadoAsistencia(EstadoAsistencia.PRESENTE);
        em.persist(registro);
        return registro;
    }

    private Calificacion calificacion() {
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(assignment);
        evaluacion.setTitulo("Evaluación de la sección anterior");
        evaluacion.setTipoEvaluacion(TipoEvaluacion.EXAMEN);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setEstado(EstadoEvaluacion.PUBLICADA);
        evaluacion.setFechaEvaluacion(LocalDate.of(2090, 4, 1));
        evaluacion.setFechaPublicacion(Instant.now());
        evaluacion.setPonderacionPorcentaje(BigDecimal.TEN);
        evaluacion.getCompetencias().add(competence);
        em.persist(evaluacion);
        var nota = new Calificacion();
        nota.setEvaluacion(evaluacion);
        nota.setAlumno(student);
        nota.setCompetencia(competence);
        nota.setNivelLogro(NivelLogro.A);
        nota.setFechaRegistro(Instant.now());
        em.persist(nota);
        return nota;
    }
}
