package com.asiscontrol.repository;

import com.asiscontrol.entity.Comprobante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByIdAndActivoTrue(Long id);

    Optional<Comprobante> findByTransaccionPagoIdAndActivoTrue(Long idTransaccionPago);

    boolean existsBySerieIgnoreCaseAndNumeroIgnoreCaseAndActivoTrue(String serie, String numero);

    Page<Comprobante> findAllByActivoTrue(Pageable pageable);

    @Query("""
            SELECT c FROM Comprobante c JOIN c.transaccionPago t JOIN t.compromisoPago cp
            WHERE c.activo=true AND t.activo=true AND cp.activo=true
              AND t.estado=com.asiscontrol.entity.enums.EstadoTransaccionPago.APROBADA
              AND EXISTS (SELECT v.id FROM AlumnoApoderado v
                WHERE v.apoderado.id=:apoderadoId AND v.activo=true
                  AND v.apoderado.activo=true AND v.apoderado.persona.activo=true
                  AND v.alumno=cp.matricula.alumno AND v.alumno.activo=true
                  AND v.alumno.persona.activo=true)
            """)
    Page<Comprobante> buscarHistorialFamiliar(@Param("apoderadoId") Long apoderadoId, Pageable pageable);

    @Query("""
            SELECT c FROM Comprobante c
            WHERE c.activo = true
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = c.transaccionPago.compromisoPago.matricula.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                      AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                      AND v.alumno.activo = true AND v.alumno.persona.activo = true
                  ))
            """)
    Page<Comprobante> buscarVisibles(
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
