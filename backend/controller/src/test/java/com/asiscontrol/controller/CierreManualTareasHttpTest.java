package com.asiscontrol.controller;

import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoTarea;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class CierreManualTareasHttpTest extends ApiIntegrationSupport {

    private Tarea task(EstadoPublicacionTarea estado, LocalDateTime limite) {
        var t = new Tarea(); t.setAsignacionCurso(assignment); t.setNombre("Cierre manual");
        t.setDescripcion("Prueba de vencimiento"); t.setEstadoPublicacion(estado);
        t.setFechaPublicacion(limite.minusDays(1)); t.setFechaLimite(limite);
        t.setPuntajeMaximo(20); t.setMaxArchivosEntrega(2); em.persist(t); em.flush();
        return t;
    }

    @Test void unaTareaVencidaPermanecePublicadaHastaQueElDocenteLaCierra() throws Exception {
        var vencida = task(EstadoPublicacionTarea.PUBLICADA, LocalDateTime.now().minusDays(1));
        String path = "/api/tareas/" + vencida.getId();
        assertThat(getOk(path).path("estado").asText()).isEqualTo("PUBLICADA");
        var error = request("POST", path + "/entregas",
                data("alumnoId", student.getId(), "archivoIds", Set.of(), "respuesta", "Trabajo fuera de plazo"), 422);
        assertThat(error.path("code").asText()).isEqualTo("TAREA_PLAZO_VENCIDO");
        assertThat(getOk(path).path("estado").asText()).isEqualTo("PUBLICADA");
        assertThat(request("POST", path + "/cerrar", null, 200).path("estado").asText()).isEqualTo("CERRADA");
    }
    @Test void rechazaCambiosTrasVencerYPermiteCalificarTrasElCierreManual() throws Exception {
        var t = task(EstadoPublicacionTarea.PUBLICADA, LocalDateTime.now().plusDays(1));
        var entrega = postOk("/api/tareas/" + t.getId() + "/entregas",
                data("alumnoId", student.getId(), "archivoIds", Set.of(), "respuesta", "Trabajo previo al cierre"));
        long entregaId = entrega.path("id").asLong();
        t.setFechaLimite(LocalDateTime.now().minusSeconds(1)); em.flush();
        var error = request("PUT", "/api/entregas/" + entregaId, data("archivoIds", Set.of(), "version", 0), 422);
        assertThat(error.path("code").asText()).isEqualTo("TAREA_PLAZO_VENCIDO");
        assertThat(em.find(EntregaTarea.class, entregaId).getEstado()).isEqualTo(EstadoTarea.ENTREGADO);
        assertThat(em.find(EntregaTarea.class, entregaId).getVersion()).isZero();
        assertThat(getOk("/api/tareas/" + t.getId()).path("estado").asText()).isEqualTo("PUBLICADA");
        var cierreManual = request("POST", "/api/tareas/" + t.getId() + "/cerrar", null, 200);
        assertThat(cierreManual.path("estado").asText()).isEqualTo("CERRADA");
        var calificada = request("POST", "/api/entregas/" + entregaId + "/calificar", data("nota", 18, "version", 0), 200);
        assertThat(calificada.path("estado").asText()).isEqualTo("CALIFICADO");
        assertThat(calificada.path("nota").asInt()).isEqualTo(18);
    }
}
