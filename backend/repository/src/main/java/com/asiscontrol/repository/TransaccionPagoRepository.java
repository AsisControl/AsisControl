package com.asiscontrol.repository;

import com.asiscontrol.entity.TransaccionPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {

    boolean existsByCompromisoPagoIdAndEstado(
            Long idCompromisoPago, com.asiscontrol.entity.enums.EstadoTransaccionPago estado);

    Optional<TransaccionPago> findByIdAndActivoTrue(Long id);

    Optional<TransaccionPago> findByNumeroOperacionIgnoreCaseAndActivoTrue(String numeroOperacion);

    Page<TransaccionPago> findAllByCompromisoPagoIdAndActivoTrue(Long idCompromisoPago, Pageable pageable);

    Page<TransaccionPago> findAllByActivoTrue(Pageable pageable);

    @Query("""
            SELECT t FROM TransaccionPago t
            WHERE t.activo = true
              AND (:idCompromisoPago IS NULL OR t.compromisoPago.id = :idCompromisoPago)
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = t.compromisoPago.matricula.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                  ))
            """)
    Page<TransaccionPago> buscarVisibles(
            @Param("idCompromisoPago") Long idCompromisoPago,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
