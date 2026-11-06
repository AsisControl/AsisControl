package com.asiscontrol.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** US-18 y US-50 por HTTP: borrador, publicación, protección y corrección con motivo. */
class EvaluacionCompetenciasHttpTest extends ApiIntegrationSupport {
    private static final String EVALUACIONES = "/api/evaluaciones";
    private static final String CALIFICACIONES = "/api/calificaciones";

    @Autowired RegistroAuditoriaRepository auditorias;

    private Map<String, Object> datosEvaluacion(String titulo) {
        return data("idAsignacionCurso", assignment.getId(), "titulo", titulo, "descripcion", null,
                "tipoEvaluacion", "EXAMEN", "periodoEvaluacion", "BIMESTRAL",
                "fechaEvaluacion", LocalDate.now(), "ponderacionPorcentaje", 100,
                "idsCompetencia", Set.of(competence.getId()));
    }

    private long nuevaEvaluacion() throws Exception {
        return postOk(EVALUACIONES, datosEvaluacion("Examen parcial")).path("id").asLong();
    }

    private JsonNode registrarNivel(long evaluacion, String nivel) throws Exception {
        return postOk(CALIFICACIONES, data("idEvaluacion", evaluacion, "idAlumno", student.getId(),
                "idCompetencia", competence.getId(), "nivelLogro", nivel, "observacion", null));
    }

    private JsonNode publicada(String nivel) throws Exception {
        long evaluacion = nuevaEvaluacion();
        JsonNode nota = registrarNivel(evaluacion, nivel);
        request("PUT", EVALUACIONES + "/" + evaluacion + "/estado", data("estado", "PUBLICADA"), 200);
        return getOk(CALIFICACIONES + "/" + nota.path("id").asLong());
    }

    private Map<String, Object> correccion(JsonNode nota, String nivel, String motivo) {
        return data("motivo", motivo, "correcciones", List.of(data(
                "idCalificacion", nota.path("id").asLong(), "nivelLogro", nivel,
                "version", nota.path("version").asLong())));
    }

    private List<RegistroAuditoria> auditoria(JsonNode nota, String accion) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc(
                        "Calificacion", nota.path("id").asText())
                .stream().filter(item -> item.getAccion().equals(accion)).toList();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            como(String rol, Long personaId, String... permisos) {
        var autoridades = new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
        for (String permiso : permisos) {
            autoridades.add(new SimpleGrantedAuthority(permiso));
        }
        return jwt().jwt(token -> token.subject(rol.toLowerCase())
                        .claim("personaId", personaId)
                        .claim("requiereCambioContrasena", false))
                .authorities(autoridades);
    }

    private Docente otroDocente() {
        Docente docente = new Docente();
        docente.setPersona(person());
        docente.setCodigoDocente("DOC" + key());
        docente.setFechaIngreso(LocalDate.now().minusYears(1));
        docente.setEspecialidad(Especialidad.COMUNICACION);
        em.persist(docente);
        em.flush();
        return docente;
    }

    @Test
    void laPlanillaListaAlumnosMatriculadosConSusNiveles() throws Exception {
        long evaluacion = nuevaEvaluacion();
        JsonNode nota = registrarNivel(evaluacion, "A");

        JsonNode planilla = getOk(EVALUACIONES + "/" + evaluacion + "/planilla");

        assertThat(planilla.path("evaluacion").path("curso").asText()).isEqualTo("Matematica");
        assertThat(planilla.path("evaluacion").path("seccion").asText()).isEqualTo("1° A");
        JsonNode fila = planilla.path("alumnos").get(0);
        assertThat(fila.path("idAlumno").asLong()).isEqualTo(student.getId());
        assertThat(fila.path("nombreCompleto").asText()).isEqualTo("Prueba Sistema, Persona");
        JsonNode celda = fila.path("notas").get(0);
        assertThat(celda.path("idCalificacion").asLong()).isEqualTo(nota.path("id").asLong());
        assertThat(celda.path("nivelLogro").asText()).isEqualTo("A");
        assertThat(celda.path("corregida").asBoolean()).isFalse();
    }

    @Test
    void soloPublicaUnaPlanillaCompletaYAuditaCadaNivel() throws Exception {
        long evaluacion = nuevaEvaluacion();
        String estado = EVALUACIONES + "/" + evaluacion + "/estado";

        JsonNode error = request("PUT", estado, data("estado", "PUBLICADA"), 422);
        assertThat(error.path("code").asText()).isEqualTo("EVALUACION_INCOMPLETA");

        JsonNode nota = registrarNivel(evaluacion, "AD");
        assertThat(getOk(CALIFICACIONES + "/consolidados?idAlumno=" + student.getId())).isEmpty();

        assertThat(request("PUT", estado, data("estado", "PUBLICADA"), 200).path("estado").asText())
                .isEqualTo("PUBLICADA");
        assertThat(getOk(CALIFICACIONES + "/consolidados?idAlumno=" + student.getId())).hasSize(1);
        String valor = auditoria(nota, "PUBLICAR_CALIFICACION").getFirst().getNuevoValor();
        assertThat(valor).contains("nivelLogro=AD", "alumnoId=" + student.getId(),
                "competencia=" + competence.getNombre(), "fechaPublicacion=");
    }

    @Test
    void lasCalificacionesPublicadasNoSeEditanNiEliminanDirectamente() throws Exception {
        JsonNode nota = publicada("A");
        JsonNode error = request("POST", CALIFICACIONES, data("idEvaluacion", nota.path("idEvaluacion").asLong(),
                "idAlumno", student.getId(), "idCompetencia", competence.getId(),
                "nivelLogro", "C", "observacion", null), 422);
        assertThat(error.path("code").asText()).isEqualTo("CALIFICACION_PUBLICADA");
        request("DELETE", CALIFICACIONES + "/" + nota.path("id").asLong(), null, 422);
        assertThat(getOk(CALIFICACIONES + "/" + nota.path("id").asLong()).path("nivelLogro").asText())
                .isEqualTo("A");
    }

    @Test
    void corregirExigeMotivoYRechazaLoQueNoCambia() throws Exception {
        JsonNode nota = publicada("A");
        String ruta = CALIFICACIONES + "/correcciones";

        request("POST", ruta, correccion(nota, "B", "   "), 400);
        request("POST", ruta, correccion(nota, "B", "x".repeat(501)), 400);
        request("POST", ruta, correccion(nota, "Z", "Error"), 400);
        assertThat(request("POST", ruta, correccion(nota, "A", "Error"), 422).path("code").asText())
                .isEqualTo("CORRECCION_SIN_CAMBIOS");
        assertThat(getOk(CALIFICACIONES + "/" + nota.path("id").asLong() + "/correcciones")).isEmpty();
    }

    @Test
    void corregirCreaNuevaVersionConservaHistorialYActualizaElConsolidado() throws Exception {
        JsonNode nota = publicada("C");
        String ruta = CALIFICACIONES + "/correcciones";
        long id = nota.path("id").asLong();

        JsonNode corregida = request("POST", ruta, correccion(nota, "A", " Error de digitación "), 200).get(0);
        assertThat(corregida.path("nivelLogro").asText()).isEqualTo("A");
        assertThat(getOk(CALIFICACIONES + "/consolidados?idAlumno=" + student.getId()).get(0)
                .path("nivelConsolidado").asText()).isEqualTo("A");

        // La versión anterior ya no sirve: otro usuario pudo cambiar el nivel.
        assertThat(request("POST", ruta, correccion(nota, "B", "Otra"), 409).path("code").asText())
                .isEqualTo("VERSION_DESACTUALIZADA");
        request("POST", ruta, correccion(corregida, "AD", "Revisión de la prueba"), 200);

        JsonNode historial = getOk(CALIFICACIONES + "/" + id + "/correcciones");
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).path("numeroVersion").asInt()).isEqualTo(1);
        assertThat(historial.get(0).path("nivelAnterior").asText()).isEqualTo("C");
        assertThat(historial.get(0).path("nivelNuevo").asText()).isEqualTo("A");
        assertThat(historial.get(0).path("motivo").asText()).isEqualTo("Error de digitación");
        assertThat(historial.get(0).path("autor").asText()).isEqualTo(admin.getPersona().nombreCompleto());
        assertThat(historial.get(0).path("fecha").asText()).isNotBlank();
        assertThat(historial.get(1).path("nivelAnterior").asText()).isEqualTo("A");

        RegistroAuditoria evento = auditoria(nota, "CORREGIR_CALIFICACION").getFirst();
        assertThat(evento.getMotivo()).isEqualTo("Error de digitación");
        assertThat(evento.getValorAnterior()).isEqualTo("nivelLogro=C");
        assertThat(evento.getNuevoValor()).contains("nivelLogro=A", "version=1");

        JsonNode planilla = getOk(EVALUACIONES + "/" + nota.path("idEvaluacion").asLong() + "/planilla");
        assertThat(planilla.path("alumnos").get(0).path("notas").get(0).path("corregida").asBoolean()).isTrue();
    }

    @Test
    void noSeCorrigeEnBorradorNiConLaEvaluacionCerrada() throws Exception {
        long evaluacion = nuevaEvaluacion();
        JsonNode borrador = registrarNivel(evaluacion, "A");
        String ruta = CALIFICACIONES + "/correcciones";
        assertThat(request("POST", ruta, correccion(borrador, "B", "Motivo"), 422).path("code").asText())
                .isEqualTo("CALIFICACION_NO_CORREGIBLE");

        request("PUT", EVALUACIONES + "/" + evaluacion + "/estado", data("estado", "PUBLICADA"), 200);
        request("PUT", EVALUACIONES + "/" + evaluacion + "/estado", data("estado", "CERRADA"), 200);
        JsonNode cerrada = getOk(CALIFICACIONES + "/" + borrador.path("id").asLong());
        assertThat(request("POST", ruta, correccion(cerrada, "B", "Motivo"), 422).path("code").asText())
                .isEqualTo("CALIFICACION_NO_CORREGIBLE");
    }

    @Test
    void soloElDocenteAsignadoCorrigeYConsultaLaPlanilla() throws Exception {
        JsonNode nota = publicada("A");
        long evaluacion = nota.path("idEvaluacion").asLong();
        Long personaAjena = otroDocente().getPersona().getId();
        byte[] cuerpo = json.writeValueAsBytes(correccion(nota, "B", "Motivo"));
        var ajeno = como("DOCENTE", personaAjena, "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR");

        mvc.perform(post(CALIFICACIONES + "/correcciones").with(ajeno)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(get(EVALUACIONES + "/" + evaluacion + "/planilla").with(ajeno))
                .andExpect(status().isForbidden());
        mvc.perform(get(EVALUACIONES + "/" + evaluacion + "/planilla")
                        .with(como("ALUMNO", student.getPersona().getId(), "CALIFICACIONES_LEER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(CALIFICACIONES + "/" + nota.path("id").asLong() + "/correcciones")
                        .with(como("ALUMNO", student.getPersona().getId(), "CALIFICACIONES_LEER")))
                .andExpect(status().isForbidden());

        var propio = como("DOCENTE", teacher.getPersona().getId(), "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR");
        mvc.perform(post(CALIFICACIONES + "/correcciones").with(propio)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
    }

    @Test
    void elListadoFiltraPorPeriodoCursoYSeccion() throws Exception {
        long evaluacion = nuevaEvaluacion();
        String base = EVALUACIONES + "?periodo=BIMESTRAL&idCurso=" + course.getId() + "&idSeccion=" + section.getId();
        assertThat(getOk(base).path("content").findValuesAsText("id")).contains(String.valueOf(evaluacion));
        assertThat(getOk(EVALUACIONES + "?periodo=ANUAL").path("content")).isEmpty();
        assertThat(getOk(EVALUACIONES + "?idCurso=" + Long.MAX_VALUE).path("content")).isEmpty();
    }
}
