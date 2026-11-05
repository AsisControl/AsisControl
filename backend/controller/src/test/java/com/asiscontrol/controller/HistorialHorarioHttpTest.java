package com.asiscontrol.controller;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.TipoAula;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-27: historial de Horarios leido de la auditoria real, a traves de la API y la seguridad. */
class HistorialHorarioHttpTest extends ApiIntegrationSupport {

    private static final String HISTORIAL = "/api/horarios/historial";

    private Aula aulaA;
    private Aula aulaB;

    @BeforeEach
    void prepararAulas() {
        aulaA = aula("Aula A");
        aulaB = aula("Aula B");
        em.flush();
    }

    @Test
    void muestraQuienCreoEditoYEliminoElBloqueConSusValores() throws Exception {
        long bloque = crearBloque(assignment, aulaA, "LUNES", "08:00:00");
        String ruta = "/api/horarios/" + bloque;
        putOk(ruta, data("asignacionCursoId", assignment.getId(), "aulaId", aulaB.getId(),
                "diaSemana", "MARTES", "horaInicio", "08:00:00", "horaFin", "09:00:00",
                "version", getOk(ruta).path("version").asLong()));
        deleteOk(ruta);

        JsonNode pagina = historial("seccionId=" + section.getId());
        assertThat(pagina.path("totalElements").asInt()).isEqualTo(3);
        assertThat(pagina.path("content").findValuesAsText("accion")).containsExactly(
                "ELIMINAR_HORARIO", "ACTUALIZAR_HORARIO", "CREAR_HORARIO");

        JsonNode eliminacion = pagina.path("content").get(0);
        assertThat(campo(eliminacion, "aula").path("anterior").asText()).isEqualTo("Aula B");
        assertThat(campo(eliminacion, "aula").path("nuevo").isNull()).isTrue();

        JsonNode edicion = pagina.path("content").get(1);
        assertThat(edicion.path("actorIdentificador").asText()).isEqualTo(admin.getUsername());
        assertThat(edicion.path("actorNombre").asText())
                .isEqualTo(admin.getPersona().nombreCompleto());
        assertThat(edicion.path("rolActor").asText()).isEqualTo("ADMINISTRADOR");
        assertThat(edicion.path("fecha").asText()).isNotBlank();
        assertThat(edicion.path("registroId").asLong()).isEqualTo(bloque);
        assertThat(campo(edicion, "aula").path("anterior").asText()).isEqualTo("Aula A");
        assertThat(campo(edicion, "aula").path("nuevo").asText()).isEqualTo("Aula B");
        assertThat(campo(edicion, "dia").path("anterior").asText()).isEqualTo("LUNES");
        assertThat(campo(edicion, "dia").path("nuevo").asText()).isEqualTo("MARTES");
        assertThat(campo(edicion, "curso").path("nuevo").asText()).isEqualTo(course.getNombre());
        assertThat(campo(edicion, "seccion").path("nuevo").asText()).isEqualTo("1° A");
        assertThat(campo(edicion, "docente").path("nuevo").asText())
                .isEqualTo(teacher.getPersona().nombreCompleto());
        assertThat(edicion.findValuesAsText("campo")).doesNotContain("aulaId", "asignacionCursoId");

        JsonNode creacion = pagina.path("content").get(2);
        assertThat(campo(creacion, "horaInicio").path("anterior").isNull()).isTrue();
        assertThat(campo(creacion, "horaInicio").path("nuevo").asText()).isEqualTo("08:00");
    }

    @Test
    void incluyeLasAsignacionesDocentesConSuMotivoDeRetiro() throws Exception {
        Seccion otraSeccion = seccion("B");
        JsonNode asignacion = postOk("/api/asignaciones-cursos", data(
                "cursoId", course.getId(), "seccionId", otraSeccion.getId(),
                "docenteId", teacher.getId()));
        String ruta = "/api/asignaciones-cursos/" + asignacion.path("id").asLong();
        request("PATCH", ruta + "/retirar", data("motivo", "Licencia del docente",
                "version", getOk(ruta).path("version").asLong()), 200);

        JsonNode pagina = historial("seccionId=" + otraSeccion.getId());
        assertThat(pagina.path("content").findValuesAsText("accion"))
                .containsExactly("RETIRAR_ASIGNACION_CURSO", "CREAR_ASIGNACION_CURSO");
        JsonNode retiro = pagina.path("content").get(0);
        assertThat(retiro.path("entidad").asText()).isEqualTo("AsignacionCurso");
        assertThat(retiro.path("motivo").asText()).isEqualTo("Licencia del docente");
        assertThat(campo(retiro, "activo").path("anterior").asText()).isEqualTo("true");
        assertThat(campo(retiro, "activo").path("nuevo").asText()).isEqualTo("false");
        assertThat(campo(retiro, "seccion").path("nuevo").asText()).isEqualTo("1° B");
    }

    @Test
    void conservaElDocenteDeEseMomentoAunqueLaAsignacionCambieDespues() throws Exception {
        crearBloque(assignment, aulaA, "LUNES", "08:00:00");
        Docente anterior = teacher;
        String nombreAnterior = anterior.getPersona().nombreCompleto();
        Docente reemplazo = docente("Otra", "Docente");
        assignment.setDocente(reemplazo);
        em.flush();

        assertThat(historial("docenteId=" + anterior.getId()).path("totalElements").asInt())
                .isEqualTo(1);
        assertThat(historial("docenteId=" + reemplazo.getId()).path("totalElements").asInt()).isZero();
        JsonNode creacion = historial("seccionId=" + section.getId()).path("content").get(0);
        assertThat(campo(creacion, "docente").path("nuevo").asText()).isEqualTo(nombreAnterior);
    }

    @Test
    void separaBienLosCursosConComaEnElNombre() throws Exception {
        course.setNombre("Ciencia, Tecnologia y Ambiente");
        em.flush();
        crearBloque(assignment, aulaA, "LUNES", "08:00:00");

        JsonNode creacion = historial("seccionId=" + section.getId()).path("content").get(0);
        assertThat(campo(creacion, "curso").path("nuevo").asText())
                .isEqualTo("Ciencia, Tecnologia y Ambiente");
    }

    @Test
    void filtraPorCadaContextoYLosCombina() throws Exception {
        crearBloque(assignment, aulaA, "LUNES", "08:00:00");
        Seccion otraSeccion = seccion("B");
        Docente otroDocente = docente("Otra", "Docente");
        AsignacionCurso otraAsignacion = asignacion(otraSeccion, course, otroDocente);
        crearBloque(otraAsignacion, aulaB, "MARTES", "08:00:00");

        assertThat(contar("anioAcademico=2090&seccionId=" + section.getId())).isEqualTo(1);
        assertThat(contar("anioAcademico=2089&seccionId=" + section.getId())).isZero();
        assertThat(contar("docenteId=" + otroDocente.getId())).isEqualTo(1);
        assertThat(contar("aulaId=" + aulaA.getId())).isEqualTo(1);
        assertThat(contar("aulaId=" + aulaB.getId() + "&seccionId=" + section.getId())).isZero();
        assertThat(contar("aulaId=" + aulaB.getId() + "&seccionId=" + otraSeccion.getId())).isEqualTo(1);
    }

    @Test
    void encuentraElCambioDeAulaDesdeElAulaAnteriorYLaNueva() throws Exception {
        long bloque = crearBloque(assignment, aulaA, "LUNES", "08:00:00");
        String ruta = "/api/horarios/" + bloque;
        putOk(ruta, data("asignacionCursoId", assignment.getId(), "aulaId", aulaB.getId(),
                "diaSemana", "LUNES", "horaInicio", "08:00:00", "horaFin", "09:00:00",
                "version", getOk(ruta).path("version").asLong()));

        assertThat(historial("aulaId=" + aulaA.getId()).path("content").findValuesAsText("accion"))
                .containsExactly("ACTUALIZAR_HORARIO", "CREAR_HORARIO");
        assertThat(historial("aulaId=" + aulaB.getId()).path("content").findValuesAsText("accion"))
                .containsExactly("ACTUALIZAR_HORARIO");
    }

    @Test
    void completaYFiltraLosBloquesAuditadosConElFormatoAntiguo() throws Exception {
        String valorAntiguo = "asignacionCursoId=" + assignment.getId() + ",aulaId=" + aulaA.getId()
                + ",dia=LUNES,horaInicio=08:00,horaFin=09:00";
        auditar("Horario", "CREAR_HORARIO", ResultadoAuditoria.EXITOSO, Instant.now(), valorAntiguo);

        JsonNode pagina = historial("seccionId=" + section.getId());
        assertThat(pagina.path("totalElements").asInt()).isEqualTo(1);
        JsonNode creacion = pagina.path("content").get(0);
        assertThat(campo(creacion, "curso").path("nuevo").asText()).isEqualTo(course.getNombre());
        assertThat(campo(creacion, "seccion").path("nuevo").asText()).isEqualTo("1° A");
        assertThat(campo(creacion, "aula").path("nuevo").asText()).isEqualTo("Aula A");

        assertThat(contar("docenteId=" + teacher.getId() + "&aulaId=" + aulaA.getId())).isEqualTo(1);
        assertThat(contar("anioAcademico=2090&aulaId=" + aulaB.getId())).isZero();
        assertThat(contar("docenteId=" + docente("Sin", "Clases").getId())).isZero();
    }

    @Test
    void omiteOperacionesRechazadasYOtrasEntidades() throws Exception {
        String foto = "seccionId=" + section.getId() + ",seccion=1° A,activo=true";
        auditar("Horario", "ACTUALIZAR_HORARIO", ResultadoAuditoria.RECHAZADO, Instant.now(), foto);
        auditar("Evaluacion", "CREAR_EVALUACION", ResultadoAuditoria.EXITOSO, Instant.now(), foto);

        assertThat(contar("seccionId=" + section.getId())).isZero();
    }

    @Test
    void filtraPorFechaEnElCalendarioDeLima() throws Exception {
        String foto = "seccionId=" + section.getId() + ",seccion=1° A,activo=true";
        auditar("AsignacionCurso", "CREAR_ASIGNACION_CURSO", ResultadoAuditoria.EXITOSO,
                Instant.parse("2090-03-02T04:59:00Z"), foto);
        auditar("AsignacionCurso", "ACTUALIZAR_ASIGNACION_CURSO", ResultadoAuditoria.EXITOSO,
                Instant.parse("2090-03-02T05:00:00Z"), foto);
        String seccion = "seccionId=" + section.getId();

        assertThat(historial(seccion + "&desde=2090-03-02").path("content").findValuesAsText("accion"))
                .containsExactly("ACTUALIZAR_ASIGNACION_CURSO");
        assertThat(historial(seccion + "&hasta=2090-03-01").path("content").findValuesAsText("accion"))
                .containsExactly("CREAR_ASIGNACION_CURSO");
        request("GET", HISTORIAL + "?desde=2090-03-02&hasta=2090-03-01", null, 422);
    }

    @Test
    void paginaDeLoMasRecienteALoMasAntiguoYLimitaElTamanio() throws Exception {
        String foto = "seccionId=" + section.getId() + ",seccion=1° A,activo=true";
        List<String> acciones = new ArrayList<>();
        for (int indice = 0; indice < 3; indice++) {
            acciones.add("ACCION_" + indice);
            auditar("AsignacionCurso", acciones.get(indice), ResultadoAuditoria.EXITOSO,
                    Instant.parse("2090-03-02T10:00:00Z").plusSeconds(indice), foto);
        }

        JsonNode segunda = historial("seccionId=" + section.getId() + "&size=2&page=1");
        assertThat(segunda.path("totalElements").asInt()).isEqualTo(3);
        assertThat(segunda.path("totalPages").asInt()).isEqualTo(2);
        assertThat(segunda.path("content").findValuesAsText("accion")).containsExactly("ACCION_0");
        assertThat(historial("size=1000").path("size").asInt()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"anioAcademico=1999", "seccionId=0", "docenteId=-1", "aulaId=0",
            "desde=texto"})
    void rechazaFiltrosInvalidos(String filtro) throws Exception {
        request("GET", HISTORIAL + "?" + filtro, null, 400);
    }

    @Test
    void soloQuienPuedeLeerAuditoriaYAcademicoConsultaElHistorial() throws Exception {
        mvc.perform(get(HISTORIAL)).andExpect(status().isUnauthorized());
        consultarCon("ACADEMICO_LEER").andExpect(status().isForbidden());
        consultarCon("AUDITORIA_LEER").andExpect(status().isForbidden());
        consultarCon("ACADEMICO_LEER", "AUDITORIA_LEER").andExpect(status().isOk());
    }

    @Test
    void elHistorialNoSePuedeEditarNiEliminar() throws Exception {
        crearBloque(assignment, aulaA, "LUNES", "08:00:00");
        for (String metodo : List.of("POST", "PUT", "PATCH", "DELETE")) {
            int estado = mvc.perform(rutaEscritura(metodo)).andReturn().getResponse().getStatus();
            assertThat(estado).as(metodo).isBetween(400, 499);
        }
        assertThat(contar("seccionId=" + section.getId())).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rutaEscritura(
            String metodo) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(org.springframework.http.HttpMethod.valueOf(metodo), HISTORIAL)
                .with(asAdmin())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}");
    }

    private org.springframework.test.web.servlet.ResultActions consultarCon(String... permisos)
            throws Exception {
        org.springframework.security.core.GrantedAuthority[] autoridades = java.util.Arrays.stream(permisos)
                .map(SimpleGrantedAuthority::new)
                .toArray(org.springframework.security.core.GrantedAuthority[]::new);
        return mvc.perform(get(HISTORIAL).with(jwt().authorities(autoridades)));
    }

    private JsonNode historial(String filtros) throws Exception {
        return getOk(HISTORIAL + "?" + filtros);
    }

    private int contar(String filtros) throws Exception {
        return historial(filtros).path("totalElements").asInt();
    }

    private JsonNode campo(JsonNode cambio, String nombre) {
        for (JsonNode campo : cambio.path("campos")) {
            if (nombre.equals(campo.path("campo").asText())) {
                return campo;
            }
        }
        throw new AssertionError("El cambio no trae el campo " + nombre + ": " + cambio);
    }

    private long crearBloque(AsignacionCurso asignacion, Aula aula, String dia, String inicio)
            throws Exception {
        return postOk("/api/horarios", data(
                "asignacionCursoId", asignacion.getId(), "aulaId", aula.getId(), "diaSemana", dia,
                "horaInicio", inicio, "horaFin", "09:00:00")).path("id").asLong();
    }

    private void auditar(
            String entidad, String accion, ResultadoAuditoria resultado, Instant fecha, String nuevoValor) {
        RegistroAuditoria registro = new RegistroAuditoria();
        registro.setActorIdentificador(admin.getUsername());
        registro.setAccion(accion);
        registro.setModulo("HORARIOS");
        registro.setEntidad(entidad);
        registro.setRegistroId("1");
        registro.setFechaHora(fecha);
        registro.setResultado(resultado);
        registro.setNuevoValor(nuevoValor);
        registro.setCorrelacionId("prueba");
        em.persist(registro);
        em.flush();
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

    private Seccion seccion(String nombre) {
        Seccion seccion = new Seccion();
        seccion.setNombre(nombre);
        seccion.setGrado(1);
        seccion.setAnioAcademico(2090);
        seccion.setCapacidadMaxima(30);
        em.persist(seccion);
        em.flush();
        return seccion;
    }

    private Docente docente(String nombres, String apellido) {
        Docente docente = new Docente();
        docente.setPersona(person());
        docente.getPersona().setNombres(nombres);
        docente.getPersona().setApellidoPaterno(apellido);
        docente.setCodigoDocente("DOC" + key());
        docente.setEspecialidad(Especialidad.MATEMATICA);
        docente.setFechaIngreso(LocalDate.of(2025, 1, 1));
        em.persist(docente);
        em.flush();
        return docente;
    }

    private AsignacionCurso asignacion(Seccion seccion, Curso curso, Docente docente) {
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setSeccion(seccion);
        asignacion.setCurso(curso);
        asignacion.setDocente(docente);
        em.persist(asignacion);
        em.flush();
        return asignacion;
    }
}
