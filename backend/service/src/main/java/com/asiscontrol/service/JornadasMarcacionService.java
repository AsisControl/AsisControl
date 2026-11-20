package com.asiscontrol.service;

import com.asiscontrol.entity.JornadaMarcacionDocente;
import com.asiscontrol.entity.asistencia.SesionClase;
import jakarta.persistence.EntityManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.asistencia.jornadas.enabled", havingValue = "true", matchIfMissing = true)
public class JornadasMarcacionService {
    private final EntityManager em;
    private final Clock reloj;
    public JornadasMarcacionService(EntityManager em, Clock reloj) { this.em = em; this.reloj = reloj; }

    /** Solo el día actual: una sesión histórica no demuestra que existiera control QR ese día. */
    @Scheduled(initialDelay = 10000, fixedDelay = 60000)
    @Transactional
    public void registrarJornadasDeHoy() {
        LocalDate hoy = LocalDate.now(reloj);
        var sesiones = em.createQuery("""
                select s from SesionClase s join fetch s.asignacionCurso a join fetch a.docente d
                where s.activo = true and a.activo = true and d.activo = true and s.fecha = :hoy
                """, SesionClase.class).setParameter("hoy", hoy).getResultList();
        for (var grupo : sesiones.stream().collect(Collectors.groupingBy(s -> s.getAsignacionCurso().getDocente().getId())).values()) {
            var docente = grupo.getFirst().getAsignacionCurso().getDocente();
            // El bloqueo evita dos fotografías de la misma jornada entre el job y una consulta.
            em.lock(docente, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            long existentes = em.createQuery("select count(j) from JornadaMarcacionDocente j where j.docente.id = :id and j.fecha = :fecha", Long.class)
                    .setParameter("id", docente.getId()).setParameter("fecha", hoy).getSingleResult();
            if (existentes != 0) continue;
            var ventana = VentanaMarcacionDocente.de(grupo);
            // Si el servidor se encendió tras el cierre, no inventar una omisión histórica.
            if (!java.time.LocalDateTime.now(reloj).isBefore(ventana.cierre())) continue;
            var jornada = new JornadaMarcacionDocente();
            jornada.setDocente(docente); jornada.setFecha(hoy); jornada.setApertura(ventana.apertura());
            jornada.setLimitePuntualidad(ventana.limitePuntualidad()); jornada.setCierre(ventana.cierre());
            em.persist(jornada);
        }
    }
}
