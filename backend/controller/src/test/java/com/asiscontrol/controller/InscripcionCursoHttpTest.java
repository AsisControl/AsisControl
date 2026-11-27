// inscripcion cursos
package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.InscripcionCurso;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.service.InscripcionCursoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Al dejar una matrícula activa el alumno queda inscrito exactamente en los cursos del plan de su
 * año y grado, igual que se generan sus cuotas, y reprocesarla no duplica las inscripciones.
 */
class InscripcionCursoHttpTest extends ApiIntegrationSupport {
    private static final String SOLICITUDES = "/api/solicitudes-matricula";

    @Autowired InscripcionCursoService inscripcionService;
    private boolean tarifa;

    private Curso cursoFueraDelPlan() {
        var curso = new Curso(); curso.setCodigo("TAL" + key()); curso.setNombre("Taller fuera del plan");
        curso.setHorasSemanales(2); curso.setGradoAcademico(grade); em.persist(curso);
        return curso;
    }

    private Curso cursoDelPlan(String nombre) {
        var curso = new Curso(); curso.setCodigo("PLN" + key()); curso.setNombre(nombre);
        curso.setHorasSemanales(3); curso.setGradoAcademico(grade); em.persist(curso);
        grade.incluirCurso(curso);
        return curso;
    }

    private Alumno alumnoNuevo() {
        var alumno = new Alumno(); alumno.setPersona(person()); alumno.setCodigoAlumno("INS" + key());
        em.persist(alumno); em.flush();
        return alumno;
    }

    private void cambiarEstado(String ruta, String estado) throws Exception {
        putOk(ruta, data("estado", estado, "version", getOk(ruta).path("version").asLong()));
    }

    /** Matricula por la API, como la secretaría: solicitud en revisión, sección y finalización. */
    private JsonNode matricular(Alumno alumno) throws Exception {
        if (!tarifa) tarifa = postOk("/api/configuracion-pagos/tarifas", data("idConcepto",
                em.createQuery("select c.id from ConceptoCobro c where c.codigo='PENSION'", Long.class).getSingleResult(),
                "anioAcademico", 2090, "grado", 1, "montoMensual", "300.00")) != null;
        var creada = postOk(SOLICITUDES, data("alumnoId", alumno.getId(), "anioAcademico", 2090, "grado", 1,
                "observaciones", "Documentos completos"));
        String ruta = SOLICITUDES + "/" + creada.path("id").asLong();
        cambiarEstado(ruta, "ENVIADA");
        cambiarEstado(ruta, "EN_REVISION");
        putOk(ruta, data("seccionId", section.getId(), "version", getOk(ruta).path("version").asLong()));
        var solicitud = getOk(ruta);
        var firma = getOk("/api/configuracion-pagos/planes/solicitudes/" + solicitud.path("id").asLong() + "/preview")
                .path("firmaPlan").asText();
        return postOk("/api/matriculas", data("solicitudMatriculaId", solicitud.path("id").asLong(),
                "versionSolicitud", solicitud.path("version").asLong(), "firmaPlan", firma));
    }

    private List<InscripcionCurso> inscripciones(long matriculaId) {
        em.flush(); em.clear();
        return em.createQuery("select i from InscripcionCurso i join fetch i.curso where i.matricula.id = :id",
                InscripcionCurso.class).setParameter("id", matriculaId).getResultList();
    }

    @Test void matricularInscribeExactamenteLosCursosDelPlan() throws Exception {
        var comunicacion = cursoDelPlan("Comunicacion");
        cursoFueraDelPlan();
        em.flush();

        long matriculaId = matricular(alumnoNuevo()).path("id").asLong();

        assertThat(inscripciones(matriculaId)).allMatch(InscripcionCurso::isActivo)
                .extracting(i -> i.getCurso().getId())
                .containsExactlyInAnyOrder(course.getId(), comunicacion.getId());
    }

    @Test void reprocesarNoDuplicaYReactivaLoInactivo() throws Exception {
        long matriculaId = matricular(alumnoNuevo()).path("id").asLong();
        var matricula = em.find(Matricula.class, matriculaId);
        inscripcionService.inscribir(matricula);
        inscripcionService.desactivar(matricula);
        inscripcionService.inscribir(matricula);

        var resultado = inscripciones(matriculaId);
        assertThat(resultado).hasSize(1).allMatch(InscripcionCurso::isActivo);
    }

    @Test void retirarDesactivaLasInscripcionesYFinalizarLasConserva() throws Exception {
        long retirada = matricular(alumnoNuevo()).path("id").asLong();
        long finalizada = matricular(alumnoNuevo()).path("id").asLong();

        putOk("/api/matriculas/" + retirada, data("estado", "RETIRADA", "motivo", "Traslado de colegio",
                "version", getOk("/api/matriculas/" + retirada).path("version").asLong()));
        putOk("/api/matriculas/" + finalizada, data("estado", "FINALIZADA", "motivo", "Concluyó el año",
                "version", getOk("/api/matriculas/" + finalizada).path("version").asLong()));

        assertThat(inscripciones(retirada)).isNotEmpty().noneMatch(InscripcionCurso::isActivo);
        assertThat(inscripciones(finalizada)).isNotEmpty().allMatch(InscripcionCurso::isActivo);
    }
}
