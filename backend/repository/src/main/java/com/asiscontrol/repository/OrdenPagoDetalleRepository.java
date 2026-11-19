package com.asiscontrol.repository;

import com.asiscontrol.entity.OrdenPagoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdenPagoDetalleRepository extends JpaRepository<OrdenPagoDetalle, Long> {
    List<OrdenPagoDetalle> findByOrdenIdOrderByCompromisoIdAsc(Long ordenId);
    Optional<OrdenPagoDetalle> findByTransaccionId(Long transaccionId);
}
