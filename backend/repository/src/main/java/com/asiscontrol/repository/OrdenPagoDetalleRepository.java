package com.asiscontrol.repository;

import com.asiscontrol.entity.OrdenPagoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrdenPagoDetalleRepository extends JpaRepository<OrdenPagoDetalle, Long> {
    List<OrdenPagoDetalle> findByOrdenIdOrderByCompromisoIdAsc(Long ordenId);
    Optional<OrdenPagoDetalle> findByTransaccionId(Long transaccionId);

    @Query("""
            SELECT d FROM OrdenPagoDetalle d
            JOIN FETCH d.orden o
            JOIN FETCH d.transaccion t
            WHERE d.compromiso.id = :compromisoId AND o.activo = true
              AND o.expiraEn <= :ahora
              AND t.estado = com.asiscontrol.entity.enums.EstadoTransaccionPago.PENDIENTE
            """)
    List<OrdenPagoDetalle> buscarIntentosVencidos(@Param("compromisoId") Long compromisoId,
            @Param("ahora") Instant ahora);
}
