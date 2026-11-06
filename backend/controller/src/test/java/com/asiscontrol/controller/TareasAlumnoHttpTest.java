package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TareasAlumnoHttpTest extends ApiIntegrationSupport {
    @TempDir static Path storage;
    @DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.path", () -> storage.toString());
    }

    private RequestPostProcessor asStudent(Alumno alumno) {
        return jwt().jwt(j -> j.subject("alumno-test").claim("personaId", alumno.getPersona().getId())
                .claim("usuarioId", admin.getId()).claim("requiereCambioContrasena", false))
                .authorities(new SimpleGrantedAuthority("ROLE_ALUMNO"),
                        new SimpleGrantedAuthority("TAREAS_LEER"), new SimpleGrantedAuthority("TAREAS_ENTREGAR"),
                        new SimpleGrantedAuthority("ARCHIVOS_ESCRIBIR"));
    }

    private Tarea task(String name, EstadoPublicacionTarea state) {
        var t = new Tarea(); t.setAsignacionCurso(assignment); t.setNombre(name);
        t.setDescripcion("Resolver y adjuntar el trabajo"); t.setEstadoPublicacion(state);
        t.setFechaPublicacion(LocalDateTime.now().minusHours(1));
        t.setFechaLimite(LocalDateTime.now().plusDays(2)); t.setMaxArchivosEntrega(2);
        t.setPuntajeMaximo(20); em.persist(t); em.flush(); return t;
    }

    @Test void listaSoloTareasVisiblesDelAlumnoConFiltrosYPaginacion() throws Exception {
        task("Borrador", EstadoPublicacionTarea.BORRADOR);
        var published = task("Fracciones", EstadoPublicacionTarea.PUBLICADA);
        task("Ecuaciones", EstadoPublicacionTarea.PUBLICADA);
        var scheduled = task("Futura", EstadoPublicacionTarea.PUBLICADA);
        scheduled.setFechaPublicacion(LocalDateTime.now().plusDays(1)); em.flush();
        mvc.perform(get("/api/alumnos/me/tareas?size=1&sort=nombre").with(asStudent(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/alumnos/me/tareas?busqueda=Fracciones&entregada=false").with(asStudent(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].tarea.id").value(published.getId()))
                .andExpect(jsonPath("$.content[0].alumnoId").value(student.getId()))
                .andExpect(jsonPath("$.content[0].curso").value(course.getNombre()))
                .andExpect(jsonPath("$.content[0].docente").value("Persona Prueba Sistema"))
                .andExpect(jsonPath("$.content[0].entrega").isEmpty());
        var other = new Alumno(); other.setPersona(person()); other.setCodigoAlumno("OTRO" + key()); em.persist(other); em.flush();
        mvc.perform(get("/api/alumnos/me/tareas?alumnoId=" + student.getId()).with(asStudent(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/alumnos/me/tareas").with(asAdmin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/alumnos/me/tareas")).andExpect(status().isUnauthorized());
    }

    @Test void cargaArchivoEntregaPersisteYDescargaComoAlumno() throws Exception {
        var t = task("Trabajo individual", EstadoPublicacionTarea.PUBLICADA);
        var pdf = "%PDF-1.4\nTrabajo del alumno\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = mvc.perform(multipart("/api/archivos")
                        .file(new MockMultipartFile("archivo", "trabajo.pdf", "application/pdf", pdf)).with(asStudent(student)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long fileId = json.readTree(upload.getContentAsByteArray()).path("id").asLong();
        var response = mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", student.getId(), "archivoIds", Set.of(fileId), "comentario", "Mi trabajo"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andReturn().getResponse();
        long deliveryId = json.readTree(response.getContentAsByteArray()).path("id").asLong();
        em.flush(); em.clear();
        assertThat(em.find(EntregaTarea.class, deliveryId).getArchivos()).hasSize(1);
        mvc.perform(get("/api/alumnos/me/tareas?entregada=true").with(asStudent(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].entrega.id").value(deliveryId))
                .andExpect(jsonPath("$.content[0].entrega.archivos[0].nombreOriginal").value("trabajo.pdf"));
        mvc.perform(get("/api/alumnos/me/tareas?entregada=false").with(asStudent(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/archivos/" + fileId + "/descarga").with(asStudent(student)))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf));
        mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", student.getId(), "archivoIds", Set.of(fileId)))))
                .andExpect(status().isConflict());
    }

    @Test void rechazaEntregasDeOtroAlumnoSinMatriculaYEnTareasCerradas() throws Exception {
        var t = task("Publicada", EstadoPublicacionTarea.PUBLICADA);
        var other = new Alumno(); other.setPersona(person()); other.setCodigoAlumno("OTRO" + key()); em.persist(other); em.flush();
        mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", other.getId(), "archivoIds", Set.of()))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(other))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", other.getId(), "archivoIds", Set.of()))))
                .andExpect(status().isUnprocessableEntity());
        t.setEstadoPublicacion(EstadoPublicacionTarea.CERRADA); em.flush();
        mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", student.getId(), "archivoIds", Set.of()))))
                .andExpect(status().isUnprocessableEntity());
    }

    private long uploadPdf(String name) throws Exception {
        var response = mvc.perform(multipart("/api/archivos")
                        .file(new MockMultipartFile("archivo", name, "application/pdf", "%PDF-1.4\nTrabajo".getBytes()))
                        .with(asStudent(student)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).path("id").asLong();
    }

    @Test void editaArchivosIncrementaVersionUnaVezYConservaCalificacionYComentarios() throws Exception {
        var t = task("Edición de entrega", EstadoPublicacionTarea.PUBLICADA);
        long first = uploadPdf("primero.pdf"), second = uploadPdf("segundo.pdf");
        var created = postOk("/api/tareas/" + t.getId() + "/entregas",
                data("alumnoId", student.getId(), "archivoIds", Set.of(first), "comentario", "Comentario anterior"));
        long id = created.path("id").asLong();
        assertThat(created.path("version").asLong()).isZero();
        String path = "/api/entregas/" + id;
        mvc.perform(put(path).with(asStudent(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("archivoIds", Set.of(first, second), "version", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.archivos.length()").value(2))
                .andExpect(jsonPath("$.comentario").value("Comentario anterior"));
        em.flush(); em.clear();
        assertThat(em.find(EntregaTarea.class, id).getVersion()).isEqualTo(1);
        mvc.perform(put(path).with(asStudent(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("archivoIds", Set.of(second), "version", 0))))
                .andExpect(status().isConflict());
        mvc.perform(put(path).with(asStudent(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("archivoIds", Set.of(second), "version", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.archivos.length()").value(1))
                .andExpect(jsonPath("$.archivos[0].id").value(second));
        em.flush(); em.clear();
        assertThat(em.find(EntregaTarea.class, id).getVersion()).isEqualTo(2);
        assertThat(em.find(ArchivoAdjunto.class, first).isActivo()).isTrue();
        request("POST", path + "/calificar", data("nota", 21, "version", 2), 422);
        var graded = request("POST", path + "/calificar", data("nota", 0, "version", 2), 200);
        assertThat(graded.path("nota").asInt()).isZero();
        assertThat(graded.path("version").asLong()).isEqualTo(3);
        mvc.perform(put(path).with(asStudent(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("archivoIds", Set.of(first), "version", 3))))
                .andExpect(status().isUnprocessableEntity());
        em.flush(); em.clear();
        assertThat(em.find(EntregaTarea.class, id).getVersion()).isEqualTo(3);
        assertThat(em.find(EntregaTarea.class, id).getNota()).isZero();
    }

    @Test void rechazaArchivosAjenosYEntregasFueraDePlazo() throws Exception {
        var t = task("Por vencer", EstadoPublicacionTarea.PUBLICADA);
        var upload = mvc.perform(multipart("/api/archivos")
                        .file(new MockMultipartFile("archivo", "ajeno.pdf", "application/pdf", "%PDF-1.4\nAjeno".getBytes()))
                        .with(asAdmin()))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long fileId = json.readTree(upload.getContentAsByteArray()).path("id").asLong();
        mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", student.getId(), "archivoIds", Set.of(fileId)))))
                .andExpect(status().isForbidden());
        t.setFechaLimite(LocalDateTime.now().minusMinutes(1)); em.flush();
        mvc.perform(post("/api/tareas/" + t.getId() + "/entregas").with(asStudent(student))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                                data("alumnoId", student.getId(), "archivoIds", Set.of(), "comentario", "Entrega tardía"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TAREA_PLAZO_VENCIDO"));
    }
}
