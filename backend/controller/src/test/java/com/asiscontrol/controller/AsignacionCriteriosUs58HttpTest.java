// criterios US-58
package com.asiscontrol.controller;

import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.enums.Especialidad;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-58: un curso de una sección tiene un solo docente titular, y crear o retirar la asignación queda
 * auditado con quién, cuándo, el contexto y el motivo del retiro.
 */
class AsignacionCriteriosUs58HttpTest extends ApiIntegrationSupport {
    private static final String RUTA = "/api/asignaciones-cursos";

    private Docente otroDocente() {
        var docente = new Docente(); docente.setPersona(person()); docente.setCodigoDocente("DOC" + key());
        docente.setFechaIngreso(LocalDate.now().minusYears(1)); docente.setEspecialidad(Especialidad.MATEMATICA);
        em.persist(docente); em.flush();
        return docente;
    }

    private JsonNode registro(String accion, String entidadId) throws Exception {
        var registros = getOk("/api/auditoria/registros?modulo=ACADEMICO&size=100").path("content");
        return StreamSupport.stream(registros.spliterator(), false)
                .filter(r -> r.path("accion").asText().equals(accion) && r.path("registroId").asText().equals(entidadId))
                .findFirst().orElseThrow(() -> new AssertionError("Sin auditoría " + accion + " para " + entidadId));
    }

    @Test void unCursoDeUnaSeccionTieneUnSoloTitularAunqueSeaOtroDocente() throws Exception {
        var rechazo = request("POST", RUTA, data("cursoId", course.getId(), "seccionId", section.getId(),
                "docenteId", otroDocente().getId()), 409);
        assertThat(rechazo.path("code").asText()).isEqualTo("ASIGNACION_CURSO_DUPLICADA");
    }

    @Test void crearYRetirarQuedanAuditadosConElMotivoDelRetiro() throws Exception {
        String id = String.valueOf(assignment.getId());
        request("PATCH", RUTA + "/" + id + "/retirar", data("motivo", "Licencia del docente",
                "version", getOk(RUTA + "/" + id).path("version").asLong()), 200);
        var retiro = registro("RETIRAR_ASIGNACION_CURSO", id);
        assertThat(retiro.path("motivo").asText()).isEqualTo("Licencia del docente");
        assertThat(retiro.path("fechaHora").asText()).isNotBlank();

        var nueva = postOk(RUTA, data("cursoId", course.getId(), "seccionId", section.getId(),
                "docenteId", otroDocente().getId()));
        var alta = registro("REACTIVAR_ASIGNACION_CURSO", nueva.path("id").asText());
        assertThat(alta.path("nuevoValor").asText()).isNotBlank();
    }
}
