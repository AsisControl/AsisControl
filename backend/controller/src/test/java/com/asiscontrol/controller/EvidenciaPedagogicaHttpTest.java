package com.asiscontrol.controller;

import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EvidenciaPedagogicaHttpTest extends ApiIntegrationSupport {

    private static final String URL = "/api/evidencias-pedagogicas";
    private PeriodoAcademico periodo;
    private RequestPostProcessor titular;

    @BeforeEach
    void prepararPeriodo() {
        periodo = new PeriodoAcademico();
        periodo.setAnioAcademico(year);
        periodo.setNombre("Primer bimestre");
        periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090, 1, 1));
        periodo.setFechaFin(LocalDate.of(2090, 3, 31));
        em.persist(periodo);
        titular = docente(teacher.getPersona());
    }

    @Test
    void registraConsultaFiltraEditaEInactivaSinCopiarLaDescripcionALaAuditoria() throws Exception {
        JsonNode creada = crear(cuerpo("Trabajo privado de estudiante", null), 201);
        long id = creada.path("id").asLong();
        assertThat(creada.path("autorId").asLong()).isEqualTo(teacher.getPersona().getId());
        assertThat(creada.path("cursoId").asLong()).isEqualTo(course.getId());
        assertThat(creada.path("fechaCreacion").asText()).isNotBlank();
        assertThat(creada.path("archivo").isNull()).isTrue();
        mvc.perform(get(URL + "/" + id).with(titular)).andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Trabajo privado de estudiante"));
        mvc.perform(get(listado()).param("competenciaId", competence.getId().toString())
                        .param("periodoId", periodo.getId().toString()).with(titular))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(listado()).param("periodoId", "99").with(titular))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put(URL + "/" + id).with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(data("descripcion", "  Evidencia revisada  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Evidencia revisada"));
        mvc.perform(delete(URL + "/" + id).with(titular)).andExpect(status().isNoContent());
        mvc.perform(get(URL + "/" + id).with(titular)).andExpect(status().isNotFound());
        mvc.perform(get(listado()).with(titular)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        var eventos = em.createQuery("""
                        SELECT r FROM RegistroAuditoria r
                        WHERE r.entidad = 'EvidenciaPedagogica' AND r.registroId = :id
                        """, RegistroAuditoria.class)
                .setParameter("id", String.valueOf(id)).getResultList();
        assertThat(eventos).extracting(RegistroAuditoria::getAccion).containsExactlyInAnyOrder(
                "CREAR_EVIDENCIA_PEDAGOGICA", "ACTUALIZAR_EVIDENCIA_PEDAGOGICA", "INACTIVAR_EVIDENCIA_PEDAGOGICA");
        assertThat(eventos).allSatisfy(evento -> {
            assertThat(evento.getActorIdentificador()).isEqualTo("docente-evidencia-" + teacher.getPersona().getId());
            assertThat(evento.getFechaHora()).isNotNull();
            assertThat(evento.getValorAnterior()).isNull();
            assertThat(evento.getNuevoValor()).isNull();
        });
    }

    @Test
    void restringeCreacionConsultaYArchivoAlContextoDelDocente() throws Exception {
        var archivo = archivoDe(teacher.getPersona());
        long id = crear(cuerpo(null, archivo.getId()), 201).path("id").asLong();
        var ajeno = docente(person());
        mvc.perform(post(URL).with(ajeno).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(cuerpo("Registro ajeno", null))))
                .andExpect(status().isForbidden());
        mvc.perform(get(listado()).with(ajeno)).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/" + id).with(ajeno)).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/" + id).with(asAdmin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/archivos/" + archivo.getId()).with(ajeno)).andExpect(status().isForbidden());
        mvc.perform(get("/api/archivos/" + archivo.getId()).with(titular)).andExpect(status().isOk());
        assignment.setActivo(false);
        em.flush();
        crear(cuerpo("Asignación retirada", null), 403);
        mvc.perform(get(URL + "/" + id).with(titular)).andExpect(status().isForbidden());
        mvc.perform(get("/api/archivos/" + archivo.getId()).with(titular)).andExpect(status().isForbidden());
    }

    @Test
    void rechazaContextosInvalidosEvidenciaVaciaYAdjuntosAjenos() throws Exception {
        crear(cuerpo("  ", null), 422);
        var datos = cuerpo("Estudiante inexistente", null);
        datos.put("alumnoId", 999999);
        crear(datos, 404);
        var sinMatricula = new Alumno();
        sinMatricula.setPersona(person());
        sinMatricula.setCodigoAlumno("EST" + key());
        em.persist(sinMatricula);
        datos.put("alumnoId", sinMatricula.getId());
        assertThat(crear(datos, 422).path("code").asText()).isEqualTo("EVIDENCIA_ALUMNO_FUERA_DE_SECCION");

        var ajena = new Competencia();
        ajena.setCodigo("COMP" + key());
        ajena.setNombre("No pertenece a este curso");
        em.persist(ajena);
        datos = cuerpo("Competencia incorrecta", null);
        datos.put("competenciaId", ajena.getId());
        crear(datos, 422);

        datos = cuerpo("Período incorrecto", null);
        datos.put("periodoId", 999999);
        crear(datos, 422);
        crear(cuerpo(null, archivoDe(person()).getId()), 403);
        crear(cuerpo(null, 999999L), 404);

        var archivoInvalido = new MockMultipartFile("archivo", "invalido.pdf", "application/pdf",
                new byte[] {0x01, 0x02, 0x03, 0x04});
        mvc.perform(multipart("/api/archivos").file(archivoInvalido).with(titular))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(listado()).with(titular)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void conservaLaEvidenciaYElAdjuntoCuandoSustentaUnaEvaluacion() throws Exception {
        var adjunto = archivoDe(teacher.getPersona());
        long id = crear(cuerpo("Sustento del aprendizaje", adjunto.getId()), 201).path("id").asLong();
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(assignment);
        evaluacion.setTitulo("Evaluación de prueba");
        evaluacion.setTipoEvaluacion(TipoEvaluacion.EXAMEN);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setFechaEvaluacion(periodo.getFechaFin().plusDays(1));
        evaluacion.setPonderacionPorcentaje(BigDecimal.TEN);
        evaluacion.getCompetencias().add(competence);
        em.persist(evaluacion);
        String vinculo = URL + "/" + id + "/evaluaciones/" + evaluacion.getId();

        mvc.perform(put(vinculo).with(titular)).andExpect(status().isUnprocessableEntity());
        evaluacion.setFechaEvaluacion(periodo.getFechaFin());
        em.flush();
        mvc.perform(put(vinculo).with(titular)).andExpect(status().isNoContent());
        mvc.perform(put(vinculo).with(titular)).andExpect(status().isNoContent());
        mvc.perform(delete(URL + "/" + id).with(titular)).andExpect(status().isConflict());
        mvc.perform(put(URL + "/" + id).with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Intento de reemplazo\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/archivos/" + adjunto.getId()).with(titular))
                .andExpect(status().isConflict());
        mvc.perform(get(URL + "/" + id).with(titular)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivo.id").value(adjunto.getId()));
    }

    private JsonNode crear(Map<String, Object> datos, int codigo) throws Exception {
        var respuesta = mvc.perform(post(URL).with(titular).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(datos)))
                .andExpect(status().is(codigo)).andReturn().getResponse();
        return json.readTree(respuesta.getContentAsByteArray());
    }

    private Map<String, Object> cuerpo(String descripcion, Long archivoId) {
        return data("asignacionId", assignment.getId(), "alumnoId", student.getId(),
                "competenciaId", competence.getId(), "periodoId", periodo.getId(),
                "descripcion", descripcion, "archivoId", archivoId);
    }

    private String listado() {
        return URL + "?asignacionId=" + assignment.getId() + "&alumnoId=" + student.getId();
    }

    private RequestPostProcessor docente(Persona persona) {
        return jwt().jwt(j -> j.subject("docente-evidencia-" + persona.getId()).claim("personaId", persona.getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("CALIFICACIONES_LEER"),
                        new SimpleGrantedAuthority("CALIFICACIONES_ESCRIBIR"),
                        new SimpleGrantedAuthority("ARCHIVOS_ESCRIBIR"));
    }

    private ArchivoAdjunto archivoDe(Persona persona) {
        var archivo = new ArchivoAdjunto();
        archivo.setNombreOriginal("evidencia.pdf");
        archivo.setNombreAlmacenado(key());
        archivo.setRutaArchivo(key());
        archivo.setTipoMime("application/pdf");
        archivo.setTamanioBytes(10L);
        archivo.setSha256("0".repeat(64));
        archivo.setCargadoPor(persona);
        em.persist(archivo);
        return archivo;
    }
}
