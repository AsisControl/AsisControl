package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ASISCTRL-93: reglas de US-23 con datos persistidos y solicitudes HTTP completas. */
class HorarioValidacionesHttpTest extends ApiIntegrationSupport {
    private static final String RUTA = "/api/horarios";
    protected Aula aulaPrincipal;
    protected Aula otraAula;
    protected Docente otroDocente;
    protected AsignacionCurso otraAsignacion;
    protected AsignacionCurso mismoDocente;
    protected AsignacionCurso mismaSeccion;

    @BeforeEach
    void prepararRecursos() {
        aulaPrincipal = aula("Aula 301");
        otraAula = aula("Aula 302");
        otroDocente = new Docente();
        otroDocente.setPersona(person());
        otroDocente.setCodigoDocente("DOC" + key());
        otroDocente.setEspecialidad(Especialidad.COMUNICACION);
        otroDocente.setFechaIngreso(LocalDate.of(2025, 1, 1));
        em.persist(otroDocente);
        Seccion seccionB = seccion(2090, "B");
        otraAsignacion = asignacion(seccionB, curso(grade), otroDocente);
        mismoDocente = asignacion(seccionB, course, teacher);
        mismaSeccion = asignacion(section, curso(grade), otroDocente);
        em.flush();
    }

    @Test
    void guardaBloqueValidoConAuditoriaYCalendarioDeAsistencia() throws Exception {
        JsonNode creado = crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        assertThat(getOk(RUTA + "/" + creado.path("id").asLong()).path("cursoId").asLong())
                .isEqualTo(course.getId());
        assertThat(sesionesActivas()).isPositive();
        assertThat(auditoriasExitosas()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AULA", "DOCENTE", "SECCION"})
    void rechazaCrucesYNoGuardaBloquesNiSesionesParciales(String recurso) throws Exception {
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        AsignacionCurso candidata = switch (recurso) {
            case "DOCENTE" -> mismoDocente;
            case "SECCION" -> mismaSeccion;
            default -> otraAsignacion;
        };
        long sesiones = sesionesActivas();
        JsonNode error = request("POST", RUTA,
                bloque(candidata, recurso.equals("AULA") ? aulaPrincipal : otraAula,
                        "LUNES", "08:30", "09:30"), 409);
        assertThat(error.path("code").asText()).isEqualTo("HORARIO_" + recurso + "_OCUPAD"
                + (recurso.equals("DOCENTE") ? "O" : "A"));
        assertThat(error.path("details")).hasSize(1);
        assertThat(error.path("details").get(0).path("aulaId").asLong()).isEqualTo(aulaPrincipal.getId());
        assertThat(error.path("message").asText()).contains("bloque #");
        assertThat(cantidadHorarios()).isEqualTo(1);
        assertThat(sesionesActivas()).isEqualTo(sesiones);
        assertThat(auditoriasExitosas()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:00", "07:00"})
    void rechazaHoraFinalIgualOAnteriorALaInicial(String fin) throws Exception {
        JsonNode error = request("POST", RUTA,
                bloque(assignment, aulaPrincipal, "LUNES", "08:00", fin), 422);
        assertThat(error.path("code").asText()).isEqualTo("HORARIO_RANGO_INVALIDO");
        assertThat(cantidadHorarios()).isZero();
        assertThat(sesionesActivas()).isZero();
    }

    @Test
    void admiteBloquesContiguosYEnOtroDia() throws Exception {
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        crear(assignment, aulaPrincipal, "LUNES", "09:00", "10:00");
        crear(assignment, aulaPrincipal, "LUNES", "07:00", "08:00");
        crear(assignment, aulaPrincipal, "MARTES", "08:00", "09:00");
        assertThat(cantidadHorarios()).isEqualTo(4);
    }

    @Test
    void rechazaAforoInsuficienteYAdmiteCapacidadExacta() throws Exception {
        otraAula.setCapacidad(29);
        em.flush();
        JsonNode error = request("POST", RUTA,
                bloque(assignment, otraAula, "LUNES", "08:00", "09:00"), 422);
        assertThat(error.path("code").asText()).isEqualTo("AULA_CAPACIDAD_INSUFICIENTE");
        assertThat(cantidadHorarios()).isZero();
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
    }

    @Test
    void permiteReutilizarAulaYDocenteEnOtroAnioAcademico() throws Exception {
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        AsignacionCurso siguiente = asignacionOtroAnio(teacher);
        crear(siguiente, aulaPrincipal, "LUNES", "08:00", "09:00");
        assertThat(cantidadHorarios()).isEqualTo(2);
    }

    @Test
    void reasignarDocenteNoConfundeSusBloquesDeOtroAnio() throws Exception {
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        crear(asignacionOtroAnio(otroDocente), otraAula, "LUNES", "08:00", "09:00");
        String ruta = "/api/asignaciones-cursos/" + assignment.getId();
        putOk(ruta, data("cursoId", course.getId(), "seccionId", section.getId(),
                "docenteId", otroDocente.getId(), "version", getOk(ruta).path("version").asLong()));
        assertThat(getOk(ruta).path("docenteId").asLong()).isEqualTo(otroDocente.getId());
    }

    @Test
    void unBloqueDeAsignacionRetiradaNoOcupaRecursosVigentes() throws Exception {
        crear(otraAsignacion, aulaPrincipal, "LUNES", "08:00", "09:00");
        // Simula una fila histórica/legacy; la API de retiro actual protege sus propios bloques.
        otraAsignacion.setActivo(false);
        em.flush();
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CURSO", "SECCION", "DOCENTE", "PERSONA_DOCENTE", "ANIO", "ANIO_CERRADO"})
    void rechazaContextosInhabilitadosAunqueLaAsignacionSigaActiva(String recurso) throws Exception {
        switch (recurso) {
            case "CURSO" -> course.setActivo(false);
            case "SECCION" -> section.setActivo(false);
            case "DOCENTE" -> teacher.setActivo(false);
            case "PERSONA_DOCENTE" -> teacher.getPersona().setActivo(false);
            case "ANIO" -> year.setActivo(false);
            default -> year.setEstado(EstadoAnioAcademico.CERRADO);
        }
        em.flush();
        request("POST", RUTA, bloque(assignment, aulaPrincipal, "LUNES", "08:00", "09:00"), 422);
        assertThat(cantidadHorarios()).isZero();
        assertThat(sesionesActivas()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AULA", "ASIGNACION"})
    void rechazaRecursosInactivos(String recurso) throws Exception {
        if (recurso.equals("AULA")) aulaPrincipal.setActivo(false);
        else assignment.setActivo(false);
        em.flush();
        request("POST", RUTA, bloque(assignment, aulaPrincipal, "LUNES", "08:00", "09:00"), 404);
    }

    @Test
    void editarRevalidaCrucesYConservaElBloqueOriginalSiFalla() throws Exception {
        crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        JsonNode segundo = crear(otraAsignacion, otraAula, "LUNES", "08:00", "09:00");
        String ruta = RUTA + "/" + segundo.path("id").asLong();
        Map<String, Object> cambios = bloque(otraAsignacion, aulaPrincipal, "LUNES", "08:00", "09:00");
        cambios.put("version", segundo.path("version").asLong());
        request("PUT", ruta, cambios, 409);
        assertThat(getOk(ruta).path("aulaId").asLong()).isEqualTo(otraAula.getId());
        assertThat(cantidadHorarios()).isEqualTo(2);
        assertThat(auditoriasExitosas()).isEqualTo(2);
    }

    @Test
    void editarElMismoBloqueNoGeneraAutoconflictoYRechazaVersionAntigua() throws Exception {
        JsonNode primero = crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        String ruta = RUTA + "/" + primero.path("id").asLong();
        Map<String, Object> cambios = bloque(assignment, aulaPrincipal, "LUNES", "08:00", "09:30");
        cambios.put("version", primero.path("version").asLong());
        JsonNode editado = putOk(ruta, cambios);
        assertThat(editado.path("version").asLong()).isGreaterThan(primero.path("version").asLong());
        request("PUT", ruta, cambios, 409);
        assertThat(getOk(ruta).path("horaFin").asText()).startsWith("09:30");
    }

    @Test
    void eliminarLiberaRecursosYConservaAsistenciaYaTomada() throws Exception {
        JsonNode creado = crear(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        SesionClase tomada = em.createQuery("from SesionClase s order by s.fecha", SesionClase.class)
                .getResultList().getFirst();
        tomada.setEstado(EstadoSesionClase.TOMADA);
        tomada.setDocenteRegistrador(teacher);
        em.flush();
        deleteOk(RUTA + "/" + creado.path("id").asLong());
        assertThat(em.find(Horario.class, creado.path("id").asLong()).isActivo()).isFalse();
        assertThat(sesionesActivas()).isEqualTo(1);
        assertThat(tomada.isActivo()).isTrue();
        crear(otraAsignacion, aulaPrincipal, "LUNES", "08:00", "09:00");
    }

    @Test
    void exigePermisoDeEscrituraYAutenticacion() throws Exception {
        var cuerpo = json.writeValueAsBytes(bloque(assignment, aulaPrincipal, "LUNES", "08:00", "09:00"));
        var lector = jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                new SimpleGrantedAuthority("ACADEMICO_LEER"));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).with(lector).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        Map<String, Object> edicion = bloque(assignment, aulaPrincipal, "LUNES", "08:00", "09:00");
        edicion.put("version", 0);
        mvc.perform(put(RUTA + "/1").with(lector).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(edicion)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(RUTA + "/1").with(lector)).andExpect(status().isForbidden());
    }

    protected JsonNode crear(AsignacionCurso asignacion, Aula aula, String dia, String inicio, String fin)
            throws Exception {
        return postOk(RUTA, bloque(asignacion, aula, dia, inicio, fin));
    }

    protected Map<String, Object> bloque(AsignacionCurso asignacion, Aula aula, String dia,
            String inicio, String fin) {
        return data("asignacionCursoId", asignacion.getId(), "aulaId", aula.getId(),
                "diaSemana", dia, "horaInicio", inicio, "horaFin", fin);
    }

    private long cantidadHorarios() {
        return em.createQuery("select count(h) from Horario h", Long.class).getSingleResult();
    }

    private long sesionesActivas() {
        return em.createQuery("select count(s) from SesionClase s where s.activo = true", Long.class)
                .getSingleResult();
    }

    private long auditoriasExitosas() {
        return em.createQuery("select count(a) from RegistroAuditoria a where a.entidad = 'Horario'"
                + " and a.resultado = com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO", Long.class)
                .getSingleResult();
    }

    protected AsignacionCurso asignacionOtroAnio(Docente docente) {
        AnioAcademico anio = new AnioAcademico();
        anio.setAnio(2091);
        anio.setFechaInicio(LocalDate.of(2091, 1, 1));
        anio.setFechaFin(LocalDate.of(2091, 12, 31));
        em.persist(anio);
        GradoAcademico grado = new GradoAcademico();
        grado.setAnioAcademico(anio);
        grado.setNumero(1);
        grado.setCapacidadDefault(30);
        em.persist(grado);
        return asignacion(seccion(2091, "A"), curso(grado), docente);
    }

    private Aula aula(String nombre) {
        Aula aula = new Aula();
        aula.setCodigo("AU" + key());
        aula.setNombre(nombre);
        aula.setCapacidad(30);
        aula.setTipoAula(TipoAula.AULA_REGULAR);
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
        curso.setHorasSemanales(4);
        curso.setGradoAcademico(grado);
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
}
