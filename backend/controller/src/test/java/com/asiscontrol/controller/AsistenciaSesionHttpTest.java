// asistencia alumnos
package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.service.asistencia.AsistenciaSesionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU14 fase 3: borrador, finalización, corrección y vencimiento, como el docente asignado. */
class AsistenciaSesionHttpTest extends ApiIntegrationSupport {

    @Autowired AsistenciaSesionService asistenciaService;

    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
    private Alumno otro;
    private Horario horario;

    @BeforeEach void datos() {
        otro = new Alumno(); otro.setPersona(person()); otro.setCodigoAlumno("EST" + key()); em.persist(otro);
        var solicitud = new SolicitudMatricula(); solicitud.setAlumno(otro); solicitud.setSeccion(section);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA); em.persist(solicitud);
        var matricula = new Matricula(); matricula.setAlumno(otro); matricula.setSeccion(section);
        matricula.setSolicitudMatricula(solicitud); matricula.setCodigoMatricula("MAT" + key());
        matricula.setAnioAcademico(2090); matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA); em.persist(matricula);
        var aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        horario = new Horario(); horario.setAsignacionCurso(assignment); horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.LUNES); horario.setHoraInicio(LocalTime.of(8, 0)); horario.setHoraFin(LocalTime.of(10, 0));
        em.persist(horario);
        em.flush();
    }

    private long sesion(LocalDate fecha, EstadoSesionClase estado) {
        var sesion = new SesionClase(); sesion.setAsignacionCurso(assignment); sesion.setHorario(horario);
        sesion.setFecha(fecha); sesion.setHoraInicio(horario.getHoraInicio()); sesion.setHoraFin(horario.getHoraFin());
        sesion.setEstado(estado); em.persist(sesion); em.flush();
        return sesion.getId();
    }

    private String ruta(long sesionId) { return "/api/asistencias/sesiones/" + sesionId; }

    private Map<String, Object> marca(Alumno alumno, String estado) { return data("alumnoId", alumno.getId(), "estado", estado); }

    /** Petición autenticada como el docente dueño de la persona indicada. */
    private JsonNode comoDocente(Docente docente, String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        MockHttpServletRequestBuilder builder = switch (metodo) {
            case "POST" -> post(ruta); case "PUT" -> put(ruta); default -> get(ruta);
        };
        builder.with(jwt().jwt(j -> j.subject("docente" + docente.getId()).claim("personaId", docente.getPersona().getId()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("ASISTENCIA_LEER"), new SimpleGrantedAuthority("ASISTENCIA_ESCRIBIR"))));
        if (cuerpo != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo));
        var respuesta = mvc.perform(builder).andExpect(status().is(esperado)).andReturn().getResponse();
        return respuesta.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(respuesta.getContentAsByteArray());
    }

    private JsonNode docente(String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
        return comoDocente(teacher, metodo, ruta, cuerpo, esperado);
    }

    private JsonNode finalizarTodos(long id) throws Exception {
        long version = docente("GET", ruta(id), null, 200).path("version").asLong();
        return docente("POST", ruta(id) + "/finalizar",
                data("marcas", List.of(marca(student, "PRESENTE"), marca(otro, "INASISTENCIA")), "version", version), 200);
    }

    @Test void guardaBorradorYFinalizaCuandoTodosTienenEstado() throws Exception {
        long id = sesion(hoy, EstadoSesionClase.PENDIENTE);
        var inicial = docente("GET", ruta(id), null, 200);
        assertThat(inicial.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(inicial.path("alumnos")).hasSize(2);
        assertThat(inicial.path("pendientes").asInt()).isEqualTo(2);
        assertThat(inicial.path("puedeTomar").asBoolean()).isTrue();

        var vacio = docente("PUT", ruta(id) + "/borrador", data("marcas", List.of()), 422);
        assertThat(vacio.path("code").asText()).isEqualTo("ASISTENCIA_SIN_MARCAS");

        var borrador = docente("PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(student, "PRESENTE"))), 200);
        assertThat(borrador.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(borrador.path("pendientes").asInt()).isEqualTo(1);

        var incompleta = docente("POST", ruta(id) + "/finalizar",
                data("marcas", List.of(marca(student, "PRESENTE")), "version", borrador.path("version").asLong()), 422);
        assertThat(incompleta.path("message").asText()).isEqualTo(
                "No se puede finalizar la asistencia. Complete el estado de todos los estudiantes. Pendientes de completar: 1.");

        var tomada = finalizarTodos(id);
        assertThat(tomada.path("estado").asText()).isEqualTo("TOMADA");
        assertThat(tomada.path("pendientes").asInt()).isZero();
        assertThat(tomada.path("puedeEditar").asBoolean()).isTrue();
        em.clear();
        var sesion = em.find(SesionClase.class, id);
        assertThat(sesion.getFechaHoraFinalizacion()).isNotNull();
        assertThat(sesion.getDocenteRegistrador().getId()).isEqualTo(teacher.getId());
        assertThat(em.createQuery("from AsistenciaAlumno a where a.sesionClase.id = :s", AsistenciaAlumno.class)
                .setParameter("s", id).getResultList()).hasSize(2);
        docente("PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(student, "TARDANZA"))), 422);
    }

    @Test void rechazaEstadosReservadosDuplicadosAjenosYVersionDesactualizada() throws Exception {
        long id = sesion(hoy, EstadoSesionClase.PENDIENTE);
        docente("PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(student, "INASISTENCIA_JUSTIFICADA"))), 422);
        docente("PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(student, "PRESENTE"), marca(student, "TARDANZA"))), 409);
        var ajeno = new Alumno(); ajeno.setPersona(person()); ajeno.setCodigoAlumno("EST" + key()); em.persist(ajeno); em.flush();
        docente("PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(ajeno, "PRESENTE"))), 422);
        docente("POST", ruta(id) + "/finalizar",
                data("marcas", List.of(marca(student, "PRESENTE"), marca(otro, "PRESENTE")), "version", 999), 409);
    }

    @Test void noPermiteTomarFechasPasadasNiFuturas() throws Exception {
        long pasada = sesion(hoy.minusDays(7), EstadoSesionClase.PENDIENTE);
        long futura = sesion(hoy.plusDays(7), EstadoSesionClase.PENDIENTE);
        var vista = docente("GET", ruta(pasada), null, 200);
        assertThat(vista.path("puedeTomar").asBoolean()).isFalse();
        var fuera = docente("PUT", ruta(pasada) + "/borrador", data("marcas", List.of(marca(student, "PRESENTE"))), 422);
        assertThat(fuera.path("code").asText()).isEqualTo("ASISTENCIA_FUERA_DE_FECHA");
        docente("PUT", ruta(futura) + "/borrador", data("marcas", List.of(marca(student, "PRESENTE"))), 422);
    }

    @Test void corrigeTomadaConMotivoYRegistraCadaCambio() throws Exception {
        long id = sesion(hoy, EstadoSesionClase.PENDIENTE);
        long version = finalizarTodos(id).path("version").asLong();

        var sinCambios = docente("PUT", ruta(id), data("marcas", List.of(marca(student, "PRESENTE")),
                "motivo", "Revision", "version", version), 422);
        assertThat(sinCambios.path("code").asText()).isEqualTo("ASISTENCIA_SIN_CAMBIOS");
        assertThat(docente("GET", ruta(id) + "/correcciones", null, 200)).isEmpty();
        docente("PUT", ruta(id), data("marcas", List.of(marca(student, "TARDANZA")), "version", version), 400);
        // En el test todas las peticiones comparten una transacción y el rechazo no se revierte
        // por separado como en producción: se vuelve a leer la versión vigente.
        version = docente("GET", ruta(id), null, 200).path("version").asLong();

        var corregida = docente("PUT", ruta(id), data("marcas",
                List.of(marca(student, "TARDANZA"), marca(otro, "INASISTENCIA")),
                "motivo", "Llego a las 8:20", "version", version), 200);
        assertThat(corregida.path("estado").asText()).isEqualTo("TOMADA");
        assertThat(corregida.path("version").asLong()).isGreaterThan(version);
        docente("PUT", ruta(id), data("marcas", List.of(marca(student, "PRESENTE")),
                "motivo", "Otra pantalla", "version", version), 409);

        var correcciones = docente("GET", ruta(id) + "/correcciones", null, 200);
        assertThat(correcciones).hasSize(1);
        assertThat(correcciones.get(0).path("motivo").asText()).isEqualTo("Llego a las 8:20");
        var cambios = correcciones.get(0).path("cambios");
        assertThat(cambios).hasSize(1);
        assertThat(cambios.get(0).path("alumnoId").asLong()).isEqualTo(student.getId());
        assertThat(cambios.get(0).path("estadoAnterior").asText()).isEqualTo("PRESENTE");
        assertThat(cambios.get(0).path("estadoNuevo").asText()).isEqualTo("TARDANZA");

        long pendiente = sesion(hoy.minusDays(1), EstadoSesionClase.PENDIENTE);
        docente("PUT", ruta(pendiente), data("marcas", List.of(marca(student, "PRESENTE")),
                "motivo", "No corresponde", "version", 0), 422);
    }

    @Test void soloElDocenteAsignadoOperaLaAsistencia() throws Exception {
        long id = sesion(hoy, EstadoSesionClase.PENDIENTE);
        var ajeno = new Docente(); ajeno.setPersona(person()); ajeno.setCodigoDocente("DOC" + key());
        ajeno.setFechaIngreso(LocalDate.now().minusYears(1)); ajeno.setEspecialidad(Especialidad.MATEMATICA); em.persist(ajeno); em.flush();
        comoDocente(ajeno, "GET", ruta(id), null, 403);
        comoDocente(ajeno, "PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(student, "PRESENTE"))), 403);
        comoDocente(ajeno, "GET", "/api/asistencias/asignaciones/" + assignment.getId() + "/sesiones", null, 403);
        // El administrador consulta, pero no registra en nombre del docente.
        getOk(ruta(id));
        request("PUT", ruta(id) + "/borrador", data("marcas", List.of(marca(student, "PRESENTE"))), 403);
    }

    @Test void borradorVencidoVuelveAPendienteSinGenerarInasistencias() throws Exception {
        long ayer = sesion(hoy.minusDays(1), EstadoSesionClase.BORRADOR);
        var fila = new AsistenciaAlumno(); fila.setAlumno(student); fila.setAsignacionCurso(assignment);
        fila.setSesionClase(em.find(SesionClase.class, ayer)); fila.setFechaRegistro(hoy.minusDays(1));
        fila.setHoraRegistro(LocalTime.of(8, 5)); fila.setEstadoAsistencia(com.asiscontrol.entity.enums.EstadoAsistencia.PRESENTE);
        em.persist(fila); em.flush();

        var antes = docente("GET", ruta(ayer), null, 200);
        assertThat(antes.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(antes.path("pendientes").asInt()).isEqualTo(2);

        assertThat(asistenciaService.vencerBorradores()).isEqualTo(1);
        em.flush(); em.clear();
        assertThat(em.find(SesionClase.class, ayer).getEstado()).isEqualTo(EstadoSesionClase.PENDIENTE);
        assertThat(em.createQuery("select count(a) from AsistenciaAlumno a where a.sesionClase.id = :s", Long.class)
                .setParameter("s", ayer).getSingleResult()).isZero();
    }
}
