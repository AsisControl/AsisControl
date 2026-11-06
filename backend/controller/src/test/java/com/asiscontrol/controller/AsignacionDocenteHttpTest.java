package com.asiscontrol.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.enums.Especialidad;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.List;

/** US-58 por HTTP: validaciones, regla PLA-06, permisos y visibilidad por rol. */
class AsignacionDocenteHttpTest extends ApiIntegrationSupport {
    private static final String RUTA = "/api/asignaciones-cursos";

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            comoDocente(Docente docente) {
        return jwt().jwt(j -> j.subject("docente" + docente.getId())
                        .claim("personaId", docente.getPersona().getId())
                        .claim("requiereCambioContrasena", false))
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("ACADEMICO_LEER")));
    }

    private Docente otroDocente() {
        Docente docente = new Docente();
        docente.setPersona(person());
        docente.setCodigoDocente("DOC" + key());
        docente.setFechaIngreso(LocalDate.now().minusYears(1));
        docente.setEspecialidad(Especialidad.COMUNICACION);
        em.persist(docente);
        return docente;
    }

    private AsignacionCurso asignacionDe(Docente docente) {
        Curso curso = new Curso();
        curso.setCodigo("COM" + key());
        curso.setNombre("Comunicacion");
        curso.setHorasSemanales(4);
        curso.setGradoAcademico(grade);
        em.persist(curso);
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setCurso(curso);
        asignacion.setSeccion(section);
        asignacion.setDocente(docente);
        em.persist(asignacion);
        em.flush();
        return asignacion;
    }

    private long version(long id) throws Exception {
        return getOk(RUTA + "/" + id).path("version").asLong();
    }

    @Test
    void elRetiroValidaLosDatosDeEntrada() throws Exception {
        String retirar = RUTA + "/" + assignment.getId() + "/retirar";
        request("PATCH", retirar, data(), 400);
        request("PATCH", retirar, data("motivo", "  ", "version", version(assignment.getId())), 400);
        request("PATCH", retirar, data("motivo", "x".repeat(501), "version", version(assignment.getId())), 400);
        request("PATCH", retirar, data("motivo", "Motivo", "version", 999), 409);
        request("PATCH", RUTA + "/" + Long.MAX_VALUE + "/retirar", data("motivo", "Motivo", "version", 0), 404);
    }

    @Test
    void conBloquesDeHorarioResponde409ConLaListaYSinEllosRetira() throws Exception {
        JsonNode aula = postOk("/api/aulas", data("codigo", "A-" + key(), "nombre", "Aula 301",
                "tipoAula", "AULA_REGULAR", "capacidad", 35, "ubicacion", "Pabellon B"));
        JsonNode horario = postOk("/api/horarios", data("asignacionCursoId", assignment.getId(),
                "aulaId", aula.path("id").asLong(), "diaSemana", "LUNES",
                "horaInicio", "08:00:00", "horaFin", "09:30:00"));
        assertThat(getOk(RUTA + "/" + assignment.getId()).path("horariosActivos").asLong()).isEqualTo(1);

        String retirar = RUTA + "/" + assignment.getId() + "/retirar";
        JsonNode rechazo = request("PATCH", retirar,
                data("motivo", "Reasignacion", "version", version(assignment.getId())), 409);
        assertThat(rechazo.path("code").asText()).isEqualTo("ASIGNACION_CON_HORARIOS_FUTUROS");
        assertThat(rechazo.path("details")).hasSize(1);
        JsonNode bloque = rechazo.path("details").get(0);
        assertThat(bloque.path("horarioId").asLong()).isEqualTo(horario.path("id").asLong());
        assertThat(bloque.path("diaSemana").asText()).isEqualTo("LUNES");
        assertThat(bloque.path("horaInicio").asText()).startsWith("08:00");
        assertThat(bloque.path("aula").asText()).isEqualTo("Aula 301");

        deleteOk("/api/horarios/" + horario.path("id").asLong());
        JsonNode retirada = request("PATCH", retirar,
                data("motivo", "Reasignacion", "version", version(assignment.getId())), 200);
        assertThat(retirada.path("activo").asBoolean()).isFalse();

        assertThat(getOk(RUTA + "?estado=RETIRADAS").path("content").findValuesAsText("id"))
                .contains(String.valueOf(assignment.getId()));
        assertThat(getOk(RUTA).path("content").findValuesAsText("id"))
                .doesNotContain(String.valueOf(assignment.getId()));
        // Ya retirada, no admite nuevos horarios.
        request("POST", "/api/horarios", data("asignacionCursoId", assignment.getId(),
                "aulaId", aula.path("id").asLong(), "diaSemana", "MARTES",
                "horaInicio", "08:00:00", "horaFin", "09:00:00"), 404);
    }

    @Test
    void unDocenteNoRetiraYSoloVeSusAsignaciones() throws Exception {
        AsignacionCurso ajena = asignacionDe(otroDocente());

        mvc.perform(patch(RUTA + "/" + assignment.getId() + "/retirar").with(comoDocente(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("motivo", "Intento", "version", 0))))
                .andExpect(status().isForbidden());

        String cuerpo = mvc.perform(get(RUTA + "?estado=TODAS&size=100").with(comoDocente(teacher)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = json.readTree(cuerpo).path("content").findValuesAsText("id");
        assertThat(ids).contains(String.valueOf(assignment.getId()))
                .doesNotContain(String.valueOf(ajena.getId()));

        mvc.perform(get(RUTA + "/" + ajena.getId()).with(comoDocente(teacher)))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA + "/" + assignment.getId()).with(comoDocente(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docenteId").value(teacher.getId()));
    }

    @Test
    void editarHaciaUnaCombinacionRetiradaExplicaComoRecuperarla() throws Exception {
        AsignacionCurso otra = asignacionDe(otroDocente());
        request("PATCH", RUTA + "/" + otra.getId() + "/retirar",
                data("motivo", "Fin de contrato", "version", version(otra.getId())), 200);

        JsonNode rechazo = request("PUT", RUTA + "/" + assignment.getId(), data(
                "cursoId", otra.getCurso().getId(), "seccionId", section.getId(),
                "docenteId", teacher.getId(), "version", version(assignment.getId())), 409);
        assertThat(rechazo.path("code").asText()).isEqualTo("ASIGNACION_RETIRADA_EXISTENTE");

        JsonNode reactivada = postOk(RUTA, data("cursoId", otra.getCurso().getId(),
                "seccionId", section.getId(), "docenteId", teacher.getId()));
        assertThat(reactivada.path("id").asLong()).isEqualTo(otra.getId());
        assertThat(reactivada.path("activo").asBoolean()).isTrue();
        assertThat(reactivada.path("docenteId").asLong()).isEqualTo(teacher.getId());
    }

    @Test
    void elBorradoAnteriorYaNoExiste() throws Exception {
        JsonNode respuesta = request("DELETE", RUTA + "/" + assignment.getId(), null, 405);
        assertThat(respuesta.path("status").asInt()).isEqualTo(405);
        assertThat(getOk(RUTA + "/" + assignment.getId()).path("activo").asBoolean()).isTrue();
    }
}
