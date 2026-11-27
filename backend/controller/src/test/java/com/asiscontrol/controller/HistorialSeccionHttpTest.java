package com.asiscontrol.controller;

import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.repository.EvaluacionRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.TareaRepository;
import com.asiscontrol.service.ModuloAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistorialSeccionHttpTest extends ApiIntegrationSupport {
    @Autowired TareaRepository tareas;
    @Autowired EvaluacionRepository evaluaciones;
    @Autowired MatriculaRepository matriculas;
    @Autowired ModuloAccessGuard guard;

    private Seccion destino;
    private Tarea anterior;
    private Tarea posterior;
    private Tarea posteriorConEntrega;
    private Evaluacion evaluacionAnterior;
    private Evaluacion evaluacionPosterior;

    @BeforeEach
    void cambiarDeSeccion() throws Exception {
        anterior = tarea(LocalDateTime.now().minusDays(1));
        posterior = tarea(LocalDateTime.now().plusDays(1));
        posteriorConEntrega = tarea(LocalDateTime.now().plusDays(1));
        var entrega = new EntregaTarea();
        entrega.setTarea(posteriorConEntrega);
        entrega.setAlumno(student);
        entrega.setFechaHoraEntrega(LocalDateTime.now());
        entrega.setEstado(EstadoTarea.ENTREGADO);
        em.persist(entrega);
        evaluacionAnterior = evaluacion(Instant.now().minus(1, ChronoUnit.DAYS));
        evaluacionPosterior = evaluacion(Instant.now().plus(1, ChronoUnit.DAYS));

        destino = new Seccion();
        destino.setNombre("B");
        destino.setGrado(1);
        destino.setAnioAcademico(2090);
        destino.setCapacidadMaxima(5);
        em.persist(destino);
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
    void elEstudianteConservaLoPublicadoHastaElCambioYLoQueEntrego() {
        Long persona = student.getPersona().getId();
        assertThat(tareas.buscarVisibles(null, null, null, "", null, persona, null, Pageable.unpaged()))
                .containsExactlyInAnyOrder(anterior, posteriorConEntrega);
        assertThat(tareas.buscarParaAlumno(student.getId(), "", null, LocalDateTime.now().plusDays(2), Pageable.unpaged()))
                .containsExactlyInAnyOrder(anterior, posteriorConEntrega);
        // Lo que quedó sin entregar en la sección anterior ya no está pendiente.
        assertThat(tareas.buscarParaAlumno(student.getId(), "", false, LocalDateTime.now(), Pageable.unpaged()))
                .isEmpty();
        assertThat(evaluacionesVisibles(persona, null)).containsExactly(evaluacionAnterior);
        assertThat(matriculas.estuvoEnSeccion(student.getId(), section.getId())).isTrue();

        iniciarSesion("ALUMNO", student.getPersona());
        assertThat(guard.puedeAccederTarea(anterior.getId())).isTrue();
        assertThat(guard.puedeAccederTarea(posterior.getId())).isFalse();
        assertThat(guard.puedeAccederEvaluacion(evaluacionAnterior.getId())).isTrue();
        assertThat(guard.puedeAccederEvaluacion(evaluacionPosterior.getId())).isFalse();
    }

    @Test
    void elApoderadoVeElMismoHistorialYOtraFamiliaNo() {
        Long persona = guardian.getPersona().getId();
        assertThat(tareas.buscarVisibles(null, null, null, "", null, null, persona, Pageable.unpaged()))
                .containsExactlyInAnyOrder(anterior, posteriorConEntrega);
        assertThat(evaluacionesVisibles(null, persona)).containsExactly(evaluacionAnterior);
        iniciarSesion("APODERADO", guardian.getPersona());
        assertThat(guard.puedeAccederTarea(anterior.getId())).isTrue();
        assertThat(guard.puedeAccederEvaluacion(evaluacionAnterior.getId())).isTrue();
        assertThat(guard.puedeAccederTarea(posterior.getId())).isFalse();

        Persona ajeno = person();
        assertThat(tareas.buscarVisibles(null, null, null, "", null, null, ajeno.getId(), Pageable.unpaged())).isEmpty();
        iniciarSesion("APODERADO", ajeno);
        assertThat(guard.puedeAccederTarea(anterior.getId())).isFalse();
    }

    private List<Evaluacion> evaluacionesVisibles(Long personaAlumno, Long personaApoderado) {
        return evaluaciones.buscar(null, null, null, null, null, null, null,
                personaAlumno, personaApoderado, false, null, Pageable.unpaged()).getContent();
    }

    private void iniciarSesion(String rol, Persona persona) {
        var token = Jwt.withTokenValue("prueba").header("alg", "none")
                .claim("personaId", persona.getId()).claim("rol", rol).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Tarea tarea(LocalDateTime publicacion) {
        var tarea = new Tarea();
        tarea.setAsignacionCurso(assignment);
        tarea.setNombre("Tarea " + key());
        tarea.setDescripcion("Historial de sección");
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.PUBLICADA);
        tarea.setFechaPublicacion(publicacion);
        tarea.setFechaLimite(publicacion.plusDays(10));
        tarea.setPuntajeMaximo(20);
        tarea.setMaxArchivosEntrega(1);
        em.persist(tarea);
        return tarea;
    }

    private Evaluacion evaluacion(Instant publicacion) {
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(assignment);
        evaluacion.setTitulo("Evaluación " + key());
        evaluacion.setTipoEvaluacion(TipoEvaluacion.EXAMEN);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setEstado(EstadoEvaluacion.PUBLICADA);
        evaluacion.setFechaEvaluacion(LocalDate.of(2090, 4, 1));
        evaluacion.setFechaPublicacion(publicacion);
        evaluacion.setPonderacionPorcentaje(BigDecimal.TEN);
        evaluacion.getCompetencias().add(competence);
        em.persist(evaluacion);
        return evaluacion;
    }
}
