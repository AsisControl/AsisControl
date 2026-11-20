package com.asiscontrol.repository;

import com.asiscontrol.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    String DESTINATARIO = """
            n.destinatario.id = :usuarioId and n.activo = true
            and (n.rolDestinatario is null or n.rolDestinatario.id = :rolId)
            """;

    @Query("select n from Notificacion n where " + DESTINATARIO)
    Page<Notificacion> buscarVisibles(
            @Param("usuarioId") Long usuarioId, @Param("rolId") Long rolId, Pageable pageable);

    @Query("select n from Notificacion n where n.id = :id and " + DESTINATARIO)
    Optional<Notificacion> buscarVisible(
            @Param("id") Long id, @Param("usuarioId") Long usuarioId, @Param("rolId") Long rolId);

    @Query("select count(n) from Notificacion n where n.leida = false and " + DESTINATARIO)
    long contarNoLeidas(@Param("usuarioId") Long usuarioId, @Param("rolId") Long rolId);

    @Modifying
    @Query("""
            update Notificacion n
               set n.leida = true, n.fechaLectura = :fecha
             where n.leida = false and
            """ + DESTINATARIO)
    int markAllRead(@Param("usuarioId") Long usuarioId,
            @Param("rolId") Long rolId, @Param("fecha") Instant fecha);
}
