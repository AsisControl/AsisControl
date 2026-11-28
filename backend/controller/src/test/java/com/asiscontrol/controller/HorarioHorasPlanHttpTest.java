package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.TipoAula;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** OBS-01: el horario de un curso en una sección no supera las horas semanales del plan (1 h = 60 min). */
class HorarioHorasPlanHttpTest extends ApiIntegrationSupport {
    private static final String RUTA = "/api/horarios";
    private static final String PLAN = "/api/planes-estudios/2090/1";

    private Aula aula;
    private Aula otraAula;

    @BeforeEach
    void prepararAulas() {
        aula = aula("Aula 501");
        otraAula = aula("Aula 502");
        em.flush();
    }

    @Test
    void aceptaLlegarJustoAlPlanYRechazaElExcesoIndicandoLasHoras() throws Exception {
        course.setHorasSemanales(6);
        em.flush();
        assertThat(getOk("/api/asignaciones-cursos/" + assignment.getId()).path("horasPlan").asInt()).isEqualTo(4);
        crear(assignment, aula, "LUNES", "08:00", "10:00");
        crear(assignment, aula, "MARTES", "08:00", "10:00");

        JsonNode error = request("POST", RUTA, bloque(assignment, aula, "MIERCOLES", "08:00", "09:00"), 422);
        assertThat(error.path("code").asText()).isEqualTo("HORARIO_EXCEDE_PLAN");
        assertThat(error.path("message").asText()).contains(
                "4 h semanales", "1° A", "hay 4 h programadas", "suma 1 h", "Disponibles: 0 h");
        assertThat(bloquesDelCurso()).isEqualTo(2);
    }

    @Test
    void cadaCursoDeLaMismaSeccionUsaSuPropioLimite() throws Exception {
        var otro = new Curso();
        otro.setCodigo("ART-" + key());
        otro.setNombre("Arte y Cultura");
        otro.setHorasSemanales(2);
        otro.setGradoAcademico(grade);
        em.persist(otro);
        grade.incluirCurso(otro);
        var asignacionArte = new AsignacionCurso();
        asignacionArte.setCurso(otro);
        asignacionArte.setDocente(teacher);
        asignacionArte.setSeccion(section);
        em.persist(asignacionArte);
        em.flush();

        var arte = crear(asignacionArte, aula, "LUNES", "08:00", "10:00");
        var cambioArte = edicion(arte, aula, "LUNES", "08:00", "10:30");
        cambioArte.put("asignacionCursoId", asignacionArte.getId());
        var error = request("PUT", RUTA + "/" + arte.path("id").asLong(), cambioArte, 422);
        assertThat(error.path("code").asText()).isEqualTo("HORARIO_EXCEDE_PLAN");
        assertThat(error.path("message").asText()).contains("Arte y Cultura", "2 h semanales");
        crear(assignment, aula, "MARTES", "08:00", "12:00");
    }

    @Test
    void sumaBloquesDeMediaHoraHastaElLimiteExacto() throws Exception {
        crear(assignment, aula, "LUNES", "08:00", "09:30");
        crear(assignment, aula, "MARTES", "08:00", "10:00");
        crear(assignment, aula, "MIERCOLES", "08:00", "08:30");

        JsonNode error = request("POST", RUTA, bloque(assignment, aula, "JUEVES", "08:00", "08:30"), 422);
        assertThat(error.path("message").asText()).contains("hay 4 h programadas", "suma 30 min");
        assertThat(bloquesDelCurso()).isEqualTo(3);
    }

    @Test
    void alEditarExcluyeElPropioBloqueYValidaSuNuevaDuracion() throws Exception {
        JsonNode primero = crear(assignment, aula, "LUNES", "08:00", "10:00");
        crear(assignment, aula, "MARTES", "08:00", "10:00");
        String ruta = RUTA + "/" + primero.path("id").asLong();

        JsonNode movido = putOk(ruta, edicion(primero, otraAula, "JUEVES", "08:00", "10:00"));
        JsonNode error = request("PUT", ruta, edicion(movido, otraAula, "JUEVES", "08:00", "10:30"), 422);
        assertThat(error.path("message").asText())
                .contains("hay 2 h programadas", "suma 2 h 30 min", "Disponibles: 2 h");
        assertThat(getOk(ruta).path("horaFin").asText()).startsWith("10:00");

        putOk(ruta, edicion(movido, otraAula, "JUEVES", "08:00", "09:30"));
        crear(assignment, aula, "VIERNES", "08:00", "08:30");
        assertThat(bloquesDelCurso()).isEqualTo(3);
    }

    @Test
    void noCuentaBloquesRetiradosNiLosDelMismoCursoEnOtraSeccion() throws Exception {
        JsonNode retirado = crear(assignment, aula, "LUNES", "08:00", "10:00");
        crear(assignment, aula, "MARTES", "08:00", "10:00");
        request("DELETE", RUTA + "/" + retirado.path("id").asLong(), data("motivo", "Cambio de malla"), 204);
        crear(assignment, aula, "MIERCOLES", "08:00", "10:00");

        AsignacionCurso otraSeccion = asignacion(seccion("B"), otroDocente());
        crear(otraSeccion, otraAula, "LUNES", "08:00", "10:00");
        crear(otraSeccion, otraAula, "MARTES", "10:00", "12:00");
        assertThat(request("POST", RUTA, bloque(otraSeccion, otraAula, "VIERNES", "08:00", "08:30"), 422)
                .path("message").asText()).contains("1° B");
    }

    @Test
    void unaSeccionQueExcedeUnPlanReducidoSoloMantieneOBajaSusHoras() throws Exception {
        JsonNode lunes = crear(assignment, aula, "LUNES", "08:00", "10:00");
        crear(assignment, aula, "MARTES", "08:00", "10:00");
        cambiarHorasDelPlan(3);
        String ruta = RUTA + "/" + lunes.path("id").asLong();

        JsonNode otraAulaIgualDuracion = putOk(ruta, edicion(lunes, otraAula, "LUNES", "08:00", "10:00"));
        JsonNode acortado = putOk(ruta, edicion(otraAulaIgualDuracion, otraAula, "LUNES", "08:00", "09:30"));
        assertThat(request("PUT", ruta, edicion(acortado, otraAula, "LUNES", "08:00", "10:00"), 422)
                .path("code").asText()).isEqualTo("HORARIO_EXCEDE_PLAN");
        assertThat(request("POST", RUTA, bloque(assignment, aula, "JUEVES", "08:00", "08:30"), 422)
                .path("message").asText()).contains("3 h semanales", "hay 3 h 30 min programadas", "Disponibles: 0 h");
    }

    @Test
    void alReducirElPlanInformaLasSeccionesQueQuedanExcedidas() throws Exception {
        JsonNode lunes = crear(assignment, aula, "LUNES", "08:00", "10:00");
        crear(assignment, aula, "MARTES", "08:00", "10:00");
        assertThat(getOk(PLAN).path("seccionesExcedidas")).isEmpty();

        JsonNode excedidas = cambiarHorasDelPlan(3).path("seccionesExcedidas");
        assertThat(excedidas).hasSize(1);
        JsonNode excedida = excedidas.get(0);
        assertThat(excedida.path("seccionId").asLong()).isEqualTo(section.getId());
        assertThat(excedida.path("seccion").asText()).isEqualTo("1° A");
        assertThat(excedida.path("cursoId").asLong()).isEqualTo(course.getId());
        assertThat(excedida.path("curso").asText()).isEqualTo(course.getNombre());
        assertThat(excedida.path("horasPlan").asInt()).isEqualTo(3);
        assertThat(excedida.path("minutosProgramados").asLong()).isEqualTo(240);
        assertThat(getOk(PLAN).path("seccionesExcedidas")).hasSize(1);

        request("DELETE", RUTA + "/" + lunes.path("id").asLong(), data("motivo", "Ajuste al plan"), 204);
        assertThat(getOk(PLAN).path("seccionesExcedidas")).isEmpty();
    }

    @Test
    void laAsignacionInformaLasHorasDelPlanParaElFormulario() throws Exception {
        String ruta = "/api/asignaciones-cursos/" + assignment.getId();
        assertThat(getOk(ruta).path("horasPlan").asInt()).isEqualTo(4);
        cambiarHorasDelPlan(3);
        assertThat(getOk(ruta).path("horasPlan").asInt()).isEqualTo(3);
    }

    @Test
    void enUnBorradorSinPlanDefinidoMandanLasHorasDelCatalogo() throws Exception {
        year.setEstado(EstadoAnioAcademico.BORRADOR);
        em.flush();
        assertThat(getOk("/api/asignaciones-cursos/" + assignment.getId()).path("horasPlan").asInt()).isEqualTo(4);

        crear(assignment, aula, "LUNES", "08:00", "12:00");
        assertThat(request("POST", RUTA, bloque(assignment, aula, "MARTES", "08:00", "08:30"), 422)
                .path("code").asText()).isEqualTo("HORARIO_EXCEDE_PLAN");
    }

    @Test
    void bloqueaCrearYEditarEnUnaAsignacionExistenteQueYaNoPerteneceAlPlan() throws Exception {
        JsonNode existente = crear(assignment, aula, "LUNES", "08:00", "10:00");
        grade.getCursosPlan().remove(course);
        em.flush();

        assertThat(request("POST", RUTA, bloque(assignment, aula, "MARTES", "08:00", "09:00"), 409)
                .path("code").asText()).isEqualTo("CURSO_FUERA_DEL_PLAN");
        assertThat(request("PUT", RUTA + "/" + existente.path("id").asLong(),
                edicion(existente, aula, "MIERCOLES", "08:00", "10:00"), 409)
                .path("code").asText()).isEqualTo("CURSO_FUERA_DEL_PLAN");
        assertThat(bloquesDelCurso()).isEqualTo(1);
    }

    private JsonNode cambiarHorasDelPlan(int horas) throws Exception {
        long version = getOk(PLAN).path("version").asLong();
        return putOk(PLAN, data("version", version, "cursos", List.of(
                data("id", course.getId(), "nombre", course.getNombre(), "horasSemanales", horas))));
    }

    private JsonNode crear(AsignacionCurso asignacion, Aula destino, String dia, String inicio, String fin)
            throws Exception {
        return postOk(RUTA, bloque(asignacion, destino, dia, inicio, fin));
    }

    private Map<String, Object> bloque(AsignacionCurso asignacion, Aula destino, String dia, String inicio,
                                       String fin) {
        return data("asignacionCursoId", asignacion.getId(), "aulaId", destino.getId(),
                "diaSemana", dia, "horaInicio", inicio, "horaFin", fin);
    }

    private Map<String, Object> edicion(JsonNode actual, Aula destino, String dia, String inicio, String fin) {
        Map<String, Object> cambios = bloque(assignment, destino, dia, inicio, fin);
        cambios.put("version", actual.path("version").asLong());
        cambios.put("motivo", "Ajuste de horario");
        return cambios;
    }

    private long bloquesDelCurso() {
        return em.createQuery("select count(h) from Horario h where h.activo = true and h.asignacionCurso.id = :id",
                Long.class).setParameter("id", assignment.getId()).getSingleResult();
    }

    private Aula aula(String nombre) {
        Aula nueva = new Aula();
        nueva.setCodigo("AU" + key());
        nueva.setNombre(nombre);
        nueva.setCapacidad(30);
        nueva.setTipoAula(TipoAula.AULA_REGULAR);
        nueva.setUbicacion("Pabellon de pruebas");
        em.persist(nueva);
        return nueva;
    }

    private Seccion seccion(String nombre) {
        Seccion nueva = new Seccion();
        nueva.setNombre(nombre);
        nueva.setGrado(1);
        nueva.setAnioAcademico(2090);
        nueva.setCapacidadMaxima(30);
        em.persist(nueva);
        return nueva;
    }

    private Docente otroDocente() {
        Docente docente = new Docente();
        docente.setPersona(person());
        docente.setCodigoDocente("DOC" + key());
        docente.setEspecialidad(Especialidad.MATEMATICA);
        docente.setFechaIngreso(LocalDate.of(2025, 1, 1));
        em.persist(docente);
        return docente;
    }

    private AsignacionCurso asignacion(Seccion seccion, Docente docente) {
        AsignacionCurso nueva = new AsignacionCurso();
        nueva.setCurso(course);
        nueva.setSeccion(seccion);
        nueva.setDocente(docente);
        em.persist(nueva);
        em.flush();
        return nueva;
    }
}