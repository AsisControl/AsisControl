package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardDocenteHttpTest extends ApiIntegrationSupport {

    private static final String RUTA = "/api/dashboard/docente";
    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
    private Aula aula;
    private Horario horario;

    @BeforeEach void horario() {
        aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula 201");
        aula.setTipoAula(TipoAula.AULA_REGULAR); aula.setCapacidad(30); aula.setUbicacion("Pabellón A");
        em.persist(aula);
        horario = bloque(assignment, DiaSemana.LUNES, 8);
        bloque(assignment, DiaSemana.MIERCOLES, 10);
    }

    private Horario bloque(AsignacionCurso asignacion, DiaSemana dia, int hora) {
        var bloque = new Horario(); bloque.setAsignacionCurso(asignacion); bloque.setAula(aula);
        bloque.setDiaSemana(dia); bloque.setHoraInicio(LocalTime.of(hora, 0)); bloque.setHoraFin(LocalTime.of(hora + 1, 0));
        em.persist(bloque);
        return bloque;
    }

    private SesionClase sesion(AsignacionCurso asignacion, Horario bloque, LocalTime inicio, LocalTime fin,
            EstadoSesionClase estado) {
        var sesion = new SesionClase(); sesion.setAsignacionCurso(asignacion); sesion.setHorario(bloque);
        sesion.setFecha(hoy); sesion.setHoraInicio(inicio); sesion.setHoraFin(fin); sesion.setEstado(estado);
        em.persist(sesion);
        return sesion;
    }

    private JsonNode comoDocente(Docente docente, int esperado) throws Exception {
        var response = mvc.perform(get(RUTA).with(jwt()
                        .jwt(j -> j.subject("docente" + key()).claim("personaId", docente.getPersona().getId()))
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_DOCENTE")))))
                .andExpect(status().is(esperado)).andReturn().getResponse();
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }

    private AsignacionCurso asignacionEnAnio(int anio, EstadoAnioAcademico estado) {
        var otroAnio = new AnioAcademico(); otroAnio.setAnio(anio); otroAnio.setFechaInicio(LocalDate.of(anio, 1, 1));
        otroAnio.setFechaFin(LocalDate.of(anio, 12, 31)); otroAnio.setEstado(estado); em.persist(otroAnio);
        var seccion = new Seccion(); seccion.setNombre("B"); seccion.setGrado(2); seccion.setAnioAcademico(anio);
        seccion.setCapacidadMaxima(30); em.persist(seccion);
        var asignacion = new AsignacionCurso(); asignacion.setCurso(course); asignacion.setDocente(teacher);
        asignacion.setSeccion(seccion); em.persist(asignacion);
        return asignacion;
    }

    @Test void reuneClasesDeHoyIndicadoresYPendientesDelDocente() throws Exception {
        sesion(assignment, horario, LocalTime.of(0, 0), LocalTime.of(0, 1), EstadoSesionClase.TOMADA);
        sesion(assignment, horario, LocalTime.of(23, 58), LocalTime.of(23, 59), EstadoSesionClase.PENDIENTE);

        var tarea = new Tarea(); tarea.setNombre("Ejercicios Cap. 4"); tarea.setDescripcion("Resolver");
        tarea.setAsignacionCurso(assignment); tarea.setFechaLimite(LocalDateTime.now().minusDays(1)); em.persist(tarea);
        for (EstadoTarea estado : List.of(EstadoTarea.ENTREGADO, EstadoTarea.ENTREGADO_TARDE, EstadoTarea.CALIFICADO)) {
            // Una entrega por alumno y tarea: cada estado lo entrega un alumno distinto.
            var alumno = new Alumno(); alumno.setPersona(person()); alumno.setCodigoAlumno("EST" + key()); em.persist(alumno);
            var entrega = new EntregaTarea(); entrega.setTarea(tarea); entrega.setAlumno(alumno);
            entrega.setFechaHoraEntrega(LocalDateTime.now()); entrega.setEstado(estado); em.persist(entrega);
        }

        var vencida = evaluacion("Práctica 1", hoy.minusDays(2));
        evaluacion("Examen futuro", hoy.plusDays(5));

        var sesionAnterior = sesion(assignment, horario, LocalTime.of(8, 0), LocalTime.of(9, 0), EstadoSesionClase.TOMADA);
        sesionAnterior.setFecha(hoy.minusDays(3));
        var asistencia = new AsistenciaAlumno(); asistencia.setAlumno(student); asistencia.setAsignacionCurso(assignment);
        asistencia.setSesionClase(sesionAnterior); asistencia.setFechaRegistro(hoy.minusDays(3));
        asistencia.setHoraRegistro(LocalTime.of(8, 0)); asistencia.setEstadoAsistencia(EstadoAsistencia.INASISTENCIA);
        em.persist(asistencia);
        var justificacion = new JustificacionAsistencia(); justificacion.setAsistenciaAlumno(asistencia);
        justificacion.setMotivo("Cita médica"); justificacion.setFechaHoraEnvio(LocalDateTime.now());
        em.persist(justificacion);
        em.flush();

        var body = comoDocente(teacher, 200);

        assertThat(body.path("fecha").asText()).isEqualTo(hoy.toString());
        // El año del fixture es 2090: el año de hoy no está ACTIVO en esta base de prueba.
        assertThat(body.path("anioAcademico").isNull()).isTrue();
        assertThat(body.path("periodo").isNull()).isTrue();
        var clases = body.path("clasesHoy");
        assertThat(clases).hasSize(2);
        assertThat(clases.get(0).path("curso").asText()).isEqualTo("Matematica");
        assertThat(clases.get(0).path("seccion").asText()).isEqualTo("1° Grado A");
        assertThat(clases.get(0).path("aula").asText()).isEqualTo("Aula 201");
        assertThat(clases.get(0).path("asignacionCursoId").asLong()).isEqualTo(assignment.getId());
        assertThat(clases.get(0).path("estado").asText()).isEqualTo("TOMADA");
        assertThat(clases.get(0).path("puedeTomar").asBoolean()).isFalse();
        assertThat(clases.get(1).path("horaInicio").asText()).startsWith("23:58");
        assertThat(clases.get(1).path("puedeTomar").asBoolean()).isTrue();
        long esperadas = 0;
        for (JsonNode clase : clases) {
            if (!"TOMADA".equals(clase.path("estado").asText()) && !"PROXIMA".equals(clase.path("momento").asText())) {
                esperadas++;
            }
        }
        assertThat(body.path("asistenciasPendientes").asLong()).isEqualTo(esperadas);
        assertThat(body.path("bloquesSemanales").asLong()).isEqualTo(2);
        // 2 entregas sin calificar (la calificada no cuenta) + 1 evaluación por registrar.
        assertThat(body.path("porRevisar").asLong()).isEqualTo(3);

        List<String> tipos = new ArrayList<>();
        body.path("pendientes").forEach(pendiente -> tipos.add(pendiente.path("tipo").asText()));
        assertThat(tipos).containsExactly("TAREA", "EVALUACION", "JUSTIFICACION");
        var pendienteTarea = body.path("pendientes").get(0);
        assertThat(pendienteTarea.path("id").asLong()).isEqualTo(tarea.getId());
        assertThat(pendienteTarea.path("titulo").asText()).isEqualTo("Ejercicios Cap. 4");
        assertThat(pendienteTarea.path("detalle").asText()).isEqualTo("Matematica · 1° Grado A");
        assertThat(pendienteTarea.path("cantidad").asLong()).isEqualTo(2);
        assertThat(pendienteTarea.path("prioridad").asText()).isEqualTo("ALTA");
        var pendienteEvaluacion = body.path("pendientes").get(1);
        assertThat(pendienteEvaluacion.path("id").asLong()).isEqualTo(vencida.getId());
        // 1 alumno activo × 1 competencia, sin niveles registrados.
        assertThat(pendienteEvaluacion.path("cantidad").asLong()).isEqualTo(1);
        assertThat(pendienteEvaluacion.path("prioridad").asText()).isEqualTo("ALTA");
        var pendienteJustificacion = body.path("pendientes").get(2);
        assertThat(pendienteJustificacion.path("cantidad").asLong()).isEqualTo(1);
        assertThat(pendienteJustificacion.path("prioridad").asText()).isEqualTo("MEDIA");
    }

    private Evaluacion evaluacion(String titulo, LocalDate fecha) {
        var evaluacion = new Evaluacion(); evaluacion.setAsignacionCurso(assignment); evaluacion.setTitulo(titulo);
        evaluacion.setTipoEvaluacion(TipoEvaluacion.PRACTICA_CALIFICADA);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL); evaluacion.setEstado(EstadoEvaluacion.BORRADOR);
        evaluacion.setFechaEvaluacion(fecha); evaluacion.setPonderacionPorcentaje(new BigDecimal("20"));
        evaluacion.getCompetencias().add(competence);
        em.persist(evaluacion);
        return evaluacion;
    }

    @Test void excluyeCursosDeAniosNoActivosYDeOtrosDocentes() throws Exception {
        var enBorrador = asignacionEnAnio(2091, EstadoAnioAcademico.BORRADOR);
        sesion(enBorrador, bloque(enBorrador, DiaSemana.VIERNES, 9), LocalTime.of(9, 0), LocalTime.of(10, 0),
                EstadoSesionClase.PENDIENTE);
        var otro = new Docente(); otro.setPersona(person()); otro.setCodigoDocente("DOC" + key());
        otro.setFechaIngreso(LocalDate.now().minusYears(1)); otro.setEspecialidad(Especialidad.MATEMATICA);
        em.persist(otro);
        em.flush();

        var propio = comoDocente(teacher, 200);
        assertThat(propio.path("clasesHoy")).isEmpty();
        // Solo cuentan los 2 bloques del año ACTIVO, no el del año en BORRADOR.
        assertThat(propio.path("bloquesSemanales").asLong()).isEqualTo(2);
        assertThat(propio.path("pendientes")).isEmpty();

        var ajeno = comoDocente(otro, 200);
        assertThat(ajeno.path("bloquesSemanales").asLong()).isZero();
        assertThat(ajeno.path("porRevisar").asLong()).isZero();
    }

    @Test void soloElDocentePuedeConsultarSuInicio() throws Exception {
        request("GET", RUTA, null, 403);
        comoRol("ALUMNO", student.getPersona(), "GET", RUTA, null, 403);
    }
}
