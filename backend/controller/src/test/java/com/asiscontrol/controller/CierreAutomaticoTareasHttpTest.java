package com.asiscontrol.controller;

import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.service.CierreAutomaticoTareasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class CierreAutomaticoTareasHttpTest extends ApiIntegrationSupport {
    @Autowired CierreAutomaticoTareasService cierre;

    private Tarea task(EstadoPublicacionTarea estado, LocalDateTime limite) {
        var t = new Tarea(); t.setAsignacionCurso(assignment); t.setNombre("Cierre automático");
        t.setDescripcion("Prueba de vencimiento"); t.setEstadoPublicacion(estado);
        t.setFechaPublicacion(limite.minusDays(1)); t.setFechaLimite(limite);
        t.setPuntajeMaximo(20); t.setMaxArchivosEntrega(2); em.persist(t); em.flush();
        return t;
    }

    @Test void cierraSoloPublicadasActivasAlLlegarAlLimiteYNoDuplicaElCierre() {
        var ahora = LocalDateTime.now().withNano(0);
        var vencida = task(EstadoPublicacionTarea.PUBLICADA, ahora.minusSeconds(1));
        var exacta = task(EstadoPublicacionTarea.PUBLICADA, ahora);
        var vigente = task(EstadoPublicacionTarea.PUBLICADA, ahora.plusSeconds(1));
        var borrador = task(EstadoPublicacionTarea.BORRADOR, ahora.minusDays(1));
        var archivada = task(EstadoPublicacionTarea.ARCHIVADA, ahora.minusDays(1));
        var inactiva = task(EstadoPublicacionTarea.PUBLICADA, ahora.minusDays(1));
        inactiva.setActivo(false); em.flush();

        assertThat(cierre.cerrarVencidas(ahora)).isEqualTo(2);
        assertThat(cierre.cerrarVencidas(ahora)).isZero();
        em.clear();
        assertThat(em.find(Tarea.class, vencida.getId()).getEstadoPublicacion()).isEqualTo(EstadoPublicacionTarea.CERRADA);
        assertThat(em.find(Tarea.class, exacta.getId()).getEstadoPublicacion()).isEqualTo(EstadoPublicacionTarea.CERRADA);
        assertThat(em.find(Tarea.class, vencida.getId()).getVersion()).isEqualTo(1);
        assertThat(em.find(Tarea.class, vigente.getId()).getEstadoPublicacion()).isEqualTo(EstadoPublicacionTarea.PUBLICADA);
        assertThat(em.find(Tarea.class, borrador.getId()).getEstadoPublicacion()).isEqualTo(EstadoPublicacionTarea.BORRADOR);
        assertThat(em.find(Tarea.class, archivada.getId()).getEstadoPublicacion()).isEqualTo(EstadoPublicacionTarea.ARCHIVADA);
        assertThat(em.find(Tarea.class, inactiva.getId()).getEstadoPublicacion()).isEqualTo(EstadoPublicacionTarea.PUBLICADA);
        assertThat(em.createQuery("SELECT COUNT(a) FROM RegistroAuditoria a WHERE a.accion = 'CERRAR_TAREA_AUTOMATICAMENTE' AND a.registroId IN :ids", Long.class)
                .setParameter("ids", java.util.List.of(vencida.getId().toString(), exacta.getId().toString())).getSingleResult()).isEqualTo(2);
    }

    @Test void rechazaCambiosAntesDelBarridoConservaEntregasYPermiteCalificarTrasCerrar() throws Exception {
        var t = task(EstadoPublicacionTarea.PUBLICADA, LocalDateTime.now().plusDays(1));
        var entrega = postOk("/api/tareas/" + t.getId() + "/entregas",
                data("alumnoId", student.getId(), "archivoIds", Set.of()));
        long entregaId = entrega.path("id").asLong();
        t.setFechaLimite(LocalDateTime.now().minusSeconds(1)); em.flush();
        var error = request("PUT", "/api/entregas/" + entregaId, data("archivoIds", Set.of(), "version", 0), 422);
        assertThat(error.path("code").asText()).isEqualTo("TAREA_PLAZO_VENCIDO");
        assertThat(em.find(EntregaTarea.class, entregaId).getEstado()).isEqualTo(EstadoTarea.ENTREGADO);
        assertThat(em.find(EntregaTarea.class, entregaId).getVersion()).isZero();
        assertThat(cierre.cerrarVencidas(LocalDateTime.now())).isEqualTo(1);
        assertThat(getOk("/api/tareas/" + t.getId()).path("estado").asText()).isEqualTo("CERRADA");
        var calificada = request("POST", "/api/entregas/" + entregaId + "/calificar", data("nota", 18, "version", 0), 200);
        assertThat(calificada.path("estado").asText()).isEqualTo("CALIFICADO");
        assertThat(calificada.path("nota").asInt()).isEqualTo(18);
    }
}
