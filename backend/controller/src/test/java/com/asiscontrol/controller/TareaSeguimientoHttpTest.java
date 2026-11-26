package com.asiscontrol.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** US-22 por HTTP: edición de tareas publicadas, eliminación con motivo, padrón y asignaciones propias. */
class TareaSeguimientoHttpTest extends ApiIntegrationSupport {
    private static final String TAREAS = "/api/tareas";

    @Autowired RegistroAuditoriaRepository auditorias;

    private final LocalDateTime limite = LocalDateTime.now().plusDays(2).withNano(0);

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            comoDocente(Docente docente) {
        return jwt().jwt(j -> j.subject("docente" + docente.getId())
                        .claim("personaId", docente.getPersona().getId())
                        .claim("requiereCambioContrasena", false))
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("ACADEMICO_LEER"),
                        new SimpleGrantedAuthority("TAREAS_LEER"),
                        new SimpleGrantedAuthority("TAREAS_ESCRIBIR")));
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

    private AsignacionCurso asignacion(Docente docente, boolean activa) {
        var curso = new com.asiscontrol.entity.Curso();
        curso.setCodigo("COM" + key());
        curso.setNombre("Comunicacion");
        curso.setHorasSemanales(4);
        curso.setGradoAcademico(grade);
        em.persist(curso);
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setCurso(curso);
        asignacion.setSeccion(section);
        asignacion.setDocente(docente);
        asignacion.setActivo(activa);
        em.persist(asignacion);
        em.flush();
        return asignacion;
    }

    private Alumno matricularOtroAlumno() {
        Alumno alumno = new Alumno();
        alumno.setPersona(person());
        alumno.setCodigoAlumno("EST" + key());
        em.persist(alumno);
        var solicitud = new SolicitudMatricula();
        solicitud.setAlumno(alumno);
        solicitud.setSeccion(section);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        var matricula = new Matricula();
        matricula.setAlumno(alumno);
        matricula.setSeccion(section);
        matricula.setSolicitudMatricula(solicitud);
        matricula.setCodigoMatricula("MAT" + key());
        matricula.setAnioAcademico(2090);
        matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        em.persist(matricula);
        em.flush();
        return alumno;
    }

    private Map<String, Object> datosTarea(LocalDateTime fechaLimite) {
        return data("asignacionCursoId", assignment.getId(), "nombre", "Fracciones",
                "descripcion", "Resolver la ficha", "fechaPublicacion", null,
                "fechaLimite", fechaLimite.toString(), "puntajeMaximo", 20,
                "maxArchivosEntrega", 2, "archivoIds", List.of());
    }

    private Map<String, Object> edicion(JsonNode tarea, LocalDateTime fechaLimite, int puntaje, String motivo) {
        return data("nombre", "Fracciones II", "descripcion", tarea.path("descripcion").asText(),
                "fechaPublicacion", tarea.path("fechaPublicacion").asText(),
                "fechaLimite", fechaLimite.toString(), "puntajeMaximo", puntaje,
                "maxArchivosEntrega", 2, "archivoIds", List.of(),
                "version", tarea.path("version").asLong(), "motivo", motivo);
    }

    @Test
    void unaTareaPonderadaUsaLaEvaluacionUnaSolaVez() throws Exception {
        String reglas = "/api/reglas-calificacion";
        var regla = putOk(reglas + "/borrador", data("idCurso", course.getId(), "idGrado", grade.getId(),
                "formula", "PROMEDIO_PONDERADO", "componentes",
                List.of(data("tipoEvaluacion", "EXAMEN", "nombre", "Exámenes", "peso", 100))));
        request("POST", reglas + "/" + regla.path("id").asLong() + "/activar",
                data("version", regla.path("version").asLong(), "motivo", "Acuerdo académico"), 200);

        var datos = datosTarea(limite);
        datos.put("cuentaParaResultadoAcademico", true);
        datos.put("nuevaEvaluacion", data("tipoEvaluacion", "PROYECTO", "fechaEvaluacion", "2090-03-10",
                "idsCompetencia", List.of(competence.getId())));
        assertThat(request("POST", TAREAS, datos, 422).path("code").asText()).isEqualTo("COMPONENTE_NO_VIGENTE");

        datos.put("nuevaEvaluacion", data("tipoEvaluacion", "EXAMEN", "fechaEvaluacion", "2090-03-10",
                "idsCompetencia", List.of(competence.getId())));
        mvc.perform(post(TAREAS).with(comoDocente(teacher)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(datos))).andExpect(status().isForbidden());
        var evaluacion = postOk(TAREAS, datos).path("evaluacionAcademica");
        assertThat(evaluacion.path("cuentaParaResultadoAcademico").asBoolean()).isTrue();
        assertThat(evaluacion.path("pesoComponente").decimalValue()).isEqualByComparingTo("100");
        datos.remove("nuevaEvaluacion");
        datos.put("idEvaluacionAcademica", evaluacion.path("id").asLong());
        assertThat(request("POST", TAREAS, datos, 409).path("code").asText()).isEqualTo("EVALUACION_YA_VINCULADA");
    }

    @Test
    void registraCeroSoloSinEntregaYNuncaPublicaElResultadoDeLaTareaSinPeso() throws Exception {
        String escalas = "/api/escala-institucional";
        var escala = putOk(escalas + "/borrador", data("rangos", List.of(
                data("nivel", "C", "minimo", 0, "maximo", 8),
                data("nivel", "B", "minimo", 9, "maximo", 12),
                data("nivel", "A", "minimo", 13, "maximo", 17),
                data("nivel", "AD", "minimo", 18, "maximo", 20))));
        request("POST", escalas + "/" + escala.path("id").asLong() + "/activar",
                data("version", escala.path("version").asLong(), "motivo", "Escala de prueba"), 200);

        Alumno entrego = matricularOtroAlumno();
        var datos = datosTarea(limite);
        datos.put("competenciasSinPeso", data("fechaEvaluacion", "2090-03-10",
                "idsCompetencia", List.of(competence.getId())));
        var tarea = postOk(TAREAS, datos);
        var evaluacion = tarea.path("evaluacionAcademica");
        assertThat(evaluacion.path("cuentaParaResultadoAcademico").asBoolean(true)).isFalse();
        String path = TAREAS + "/" + tarea.path("id").asLong();
        var publicada = request("POST", path + "/publicar", null, 200);
        assertThat(publicada.path("evaluacionAcademica").path("estado").asText()).isEqualTo("BORRADOR");
        entregar(publicada, entrego);
        String masiva = path + "/calificaciones-sin-entrega";
        request("GET", masiva, null, 422);
        em.find(com.asiscontrol.entity.Tarea.class, tarea.path("id").asLong())
                .setEstadoPublicacion(com.asiscontrol.entity.enums.EstadoPublicacionTarea.CERRADA);
        em.flush();
        assertThat(getOk(masiva).path("alumnos")).hasSize(1);
        var seleccion = data("alumnos", List.of(student.getId()), "competencias", List.of(competence.getId()), "nota", 5.5);
        assertThat(request("POST", masiva, seleccion, 200).path("calificaciones").asInt()).isEqualTo(1);
        request("POST", masiva, seleccion, 409);
        assertThat(getOk(masiva).path("alumnos")).isEmpty();
        var seguimiento = getOk(path + "/seguimiento");
        assertThat(seguimiento.path("entregados").asInt()).isEqualTo(1);
        assertThat(seguimiento.path("calificados").asInt()).isEqualTo(1);
        var notas = em.createQuery("select c from Calificacion c where c.evaluacion.id = :id",
                        com.asiscontrol.entity.Calificacion.class)
                .setParameter("id", evaluacion.path("id").asLong()).getResultList();
        assertThat(notas).singleElement().satisfies(nota -> {
            assertThat(nota.getAlumno().getId()).isEqualTo(student.getId());
            assertThat(nota.getNotaNumerica()).isEqualByComparingTo("5.5");
        });
    }

    private JsonNode tareaPublicada() throws Exception {
        long id = postOk(TAREAS, datosTarea(limite)).path("id").asLong();
        return request("POST", TAREAS + "/" + id + "/publicar", null, 200);
    }

    private JsonNode entregar(JsonNode tarea, Alumno alumno) throws Exception {
        return postOk(TAREAS + "/" + tarea.path("id").asLong() + "/entregas",
                data("alumnoId", alumno.getId(), "archivoIds", List.of(), "comentario", "Listo"));
    }

    private List<RegistroAuditoria> auditoria(long tareaId, String accion) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("Tarea", String.valueOf(tareaId))
                .stream().filter(r -> r.getAccion().equals(accion)).toList();
    }

    @Test
    void editaUnaTareaPublicadaSinEntregasYConservaLaPublicacion() throws Exception {
        JsonNode tarea = tareaPublicada();
        long id = tarea.path("id").asLong();

        assertThat(request("PUT", TAREAS + "/" + id, edicion(tarea, limite.plusDays(1), 25, null), 422)
                .path("code").asText()).isEqualTo("TAREA_PUNTAJE_FIJO");
        JsonNode editada = putOk(TAREAS + "/" + id, edicion(tarea, limite.plusDays(1), 20, null));

        assertThat(editada.path("nombre").asText()).isEqualTo("Fracciones II");
        assertThat(editada.path("estado").asText()).isEqualTo("PUBLICADA");
        assertThat(editada.path("puntajeMaximo").asInt()).isEqualTo(20);
        assertThat(editada.path("fechaPublicacion").asText()).isEqualTo(tarea.path("fechaPublicacion").asText());

        Map<String, Object> reprogramada = edicion(editada, limite.plusDays(1), 20, null);
        reprogramada.put("fechaPublicacion", LocalDateTime.now().plusDays(1).withNano(0).toString());
        assertThat(request("PUT", TAREAS + "/" + id, reprogramada, 422).path("code").asText())
                .isEqualTo("TAREA_PUBLICACION_NO_EDITABLE");
        Map<String, Object> vencida = edicion(editada, LocalDateTime.now().minusMinutes(1).withNano(0), 20, null);
        assertThat(request("PUT", TAREAS + "/" + id, vencida, 422).path("code").asText())
                .isIn("TAREA_FECHA_LIMITE_VENCIDA", "TAREA_RANGO_FECHAS_INVALIDO");
    }

    @Test
    void conEntregasCambiarElVencimientoExigeMotivoYRecalculaLasEntregas() throws Exception {
        JsonNode tarea = tareaPublicada();
        long id = tarea.path("id").asLong();
        long entregaId = entregar(tarea, student).path("id").asLong();
        // Entrega fuera del plazo original: al ampliar el plazo pasa a ser a tiempo.
        EntregaTarea entrega = em.find(EntregaTarea.class, entregaId);
        entrega.setFechaHoraEntrega(limite.plusHours(1));
        entrega.setEstado(EstadoTarea.ENTREGADO_TARDE);
        em.flush();

        JsonNode sinMotivo = request("PUT", TAREAS + "/" + id, edicion(tarea, limite.plusDays(3), 20, "  "), 422);
        assertThat(sinMotivo.path("code").asText()).isEqualTo("TAREA_MOTIVO_REQUERIDO");

        // Sin cambiar la fecha límite no hace falta motivo.
        tarea = putOk(TAREAS + "/" + id, edicion(tarea, limite, 20, null));
        assertThat(auditoria(id, "ACTUALIZAR_TAREA").getLast().getMotivo()).isNull();

        putOk(TAREAS + "/" + id, edicion(tarea, limite.plusDays(3), 20, "Corte de luz en la zona"));

        RegistroAuditoria registro = auditoria(id, "ACTUALIZAR_TAREA").getLast();
        assertThat(registro.getMotivo()).isEqualTo("Corte de luz en la zona");
        assertThat(registro.getValorAnterior()).contains("fechaLimite=" + limite);
        assertThat(registro.getNuevoValor()).contains("fechaLimite=" + limite.plusDays(3), "entregasRecalculadas=1");
        assertThat(getOk("/api/entregas/" + entregaId).path("estado").asText()).isEqualTo("ENTREGADO");
    }

    @Test
    void noBajaElPuntajeDebajoDeUnaNotaHistoricaNiEditaTareasCerradas() throws Exception {
        JsonNode tarea = tareaPublicada();
        long id = tarea.path("id").asLong();
        em.find(com.asiscontrol.entity.Tarea.class, id).setPuntajeMaximoFijo(false);
        em.flush();
        JsonNode entrega = entregar(tarea, student);
        var calificada = request("POST", "/api/entregas/" + entrega.path("id").asLong() + "/calificar",
                data("nota", 17.75, "comentario", null, "version", entrega.path("version").asLong()), 200);
        assertThat(calificada.path("nota").decimalValue()).isEqualByComparingTo("17.75");

        JsonNode error = request("PUT", TAREAS + "/" + id, edicion(tarea, limite, 15, null), 422);
        assertThat(error.path("code").asText()).isEqualTo("TAREA_PUNTAJE_MENOR_A_NOTAS");
        putOk(TAREAS + "/" + id, edicion(tarea, limite, 18, null));

        em.createQuery("update Tarea t set t.estadoPublicacion = com.asiscontrol.entity.enums.EstadoPublicacionTarea.CERRADA where t.id = :id")
                .setParameter("id", id).executeUpdate();
        em.clear();
        JsonNode cerrada = getOk(TAREAS + "/" + id);
        assertThat(request("PUT", TAREAS + "/" + id, edicion(cerrada, limite, 20, null), 422).path("code").asText())
                .isEqualTo("TAREA_TRANSICION_INVALIDA");
    }

    @Test
    void eliminarConEntregasExigeMotivoYDejaAuditoria() throws Exception {
        JsonNode tarea = tareaPublicada();
        long id = tarea.path("id").asLong();
        entregar(tarea, student);

        assertThat(request("DELETE", TAREAS + "/" + id, null, 422).path("code").asText())
                .isEqualTo("TAREA_MOTIVO_REQUERIDO");
        request("DELETE", TAREAS + "/" + id, data("motivo", "x".repeat(501)), 400);
        request("DELETE", TAREAS + "/" + id, data("motivo", "Tarea duplicada por error"), 204);

        request("GET", TAREAS + "/" + id, null, 404);
        RegistroAuditoria registro = auditoria(id, "ELIMINAR_TAREA").getFirst();
        assertThat(registro.getMotivo()).isEqualTo("Tarea duplicada por error");
        assertThat(registro.getValorAnterior()).contains("entregas=1");

        // Sin entregas se elimina sin motivo, aunque esté publicada.
        long otra = tareaPublicada().path("id").asLong();
        deleteOk(TAREAS + "/" + otra);
        assertThat(auditoria(otra, "ELIMINAR_TAREA").getFirst().getMotivo()).isNull();
    }

    @Test
    void elSeguimientoMuestraElPadronCompletoConQuienNoEntrego() throws Exception {
        Alumno sinEntrega = matricularOtroAlumno();
        JsonNode tarea = tareaPublicada();
        long id = tarea.path("id").asLong();
        long entregaId = entregar(tarea, student).path("id").asLong();

        JsonNode seguimiento = getOk(TAREAS + "/" + id + "/seguimiento");

        assertThat(seguimiento.path("totalAlumnos").asInt()).isEqualTo(2);
        assertThat(seguimiento.path("entregados").asInt()).isEqualTo(1);
        assertThat(seguimiento.path("pendientes").asInt()).isEqualTo(1);
        assertThat(seguimiento.path("calificados").asInt()).isZero();
        JsonNode filas = seguimiento.path("alumnos");
        assertThat(filas).hasSize(2);
        for (JsonNode fila : filas) {
            assertThat(fila.path("matriculaActiva").asBoolean()).isTrue();
            if (fila.path("alumnoId").asLong() == sinEntrega.getId()) {
                assertThat(fila.path("estado").asText()).isEqualTo("PENDIENTE");
                assertThat(fila.path("entregaId").isNull()).isTrue();
            } else {
                assertThat(fila.path("entregaId").asLong()).isEqualTo(entregaId);
                assertThat(fila.path("estado").asText()).isEqualTo("ENTREGADO");
                assertThat(fila.path("codigoAlumno").asText()).isEqualTo(student.getCodigoAlumno());
            }
        }
    }

    @Test
    void elSeguimientoSoloLoConsultaElDocenteDeLaTarea() throws Exception {
        long id = tareaPublicada().path("id").asLong();
        String ruta = TAREAS + "/" + id + "/seguimiento";

        mvc.perform(get(ruta).with(comoDocente(teacher))).andExpect(status().isOk());
        mvc.perform(get(ruta).with(comoDocente(otroDocente()))).andExpect(status().isForbidden());
        mvc.perform(get(ruta).with(jwt().jwt(j -> j.subject("alumno").claim("personaId", student.getPersona().getId())
                        .claim("requiereCambioContrasena", false))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ALUMNO"), new SimpleGrantedAuthority("TAREAS_LEER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasAsignacionesPropiasSoloIncluyenLasActivasDelDocenteAutenticado() throws Exception {
        Docente otro = otroDocente();
        AsignacionCurso ajena = asignacion(otro, true);
        asignacion(teacher, false); // retirada: ya no habilita "Nueva tarea"

        String propias = mvc.perform(get("/api/asignaciones-cursos/mias").with(comoDocente(teacher)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode contenido = json.readTree(propias).path("content");
        assertThat(contenido).hasSize(1);
        assertThat(contenido.get(0).path("id").asLong()).isEqualTo(assignment.getId());

        String delOtro = mvc.perform(get("/api/asignaciones-cursos/mias?anioAcademico=2090").with(comoDocente(otro)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(delOtro).path("content")).extracting(n -> n.path("id").asLong())
                .containsExactly(ajena.getId());
        // El administrador sin perfil docente no recibe el catálogo global.
        assertThat(getOk("/api/asignaciones-cursos/mias").path("content")).isEmpty();
    }
}
