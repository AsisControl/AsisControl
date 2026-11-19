package com.asiscontrol.repository;

import com.asiscontrol.entity.TransaccionPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {

    boolean existsByCompromisoPagoIdAndEstado(
            Long idCompromisoPago, com.asiscontrol.entity.enums.EstadoTransaccionPago estado);

    Optional<TransaccionPago> findByIdAndActivoTrue(Long id);

    /** Solo las transacciones aprobadas se aplican al saldo; pendientes, rechazadas, expiradas y anuladas no. */
    @Query("""
            SELECT COALESCE(SUM(t.monto), 0) FROM TransaccionPago t
            WHERE t.activo = true AND t.compromisoPago.id = :idCompromisoPago
              AND t.estado = com.asiscontrol.entity.enums.EstadoTransaccionPago.APROBADA
            """)
    BigDecimal sumarAprobadas(@Param("idCompromisoPago") Long idCompromisoPago);

    /** Secuencia completa de un compromiso, en el orden en que ocurrieron las transacciones. */
    List<TransaccionPago> findAllByCompromisoPagoIdAndActivoTrueOrderByFechaTransaccionAscIdAsc(Long idCompromisoPago);

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
                      AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                      AND v.alumno.activo = true AND v.alumno.persona.activo = true
                  ))
            """)
    Page<TransaccionPago> buscarVisibles(
            @Param("idCompromisoPago") Long idCompromisoPago,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );

    /** Consulta de transacciones filtrada por estudiante, estado y fecha (US-44). */
    @Query("""
            SELECT t FROM TransaccionPago t
            JOIN t.compromisoPago c JOIN c.matricula m JOIN m.alumno a JOIN a.persona p
            WHERE t.activo = true AND c.activo = true
              AND (:idCompromisoPago IS NULL OR c.id = :idCompromisoPago)
              AND (:idAlumno IS NULL OR a.id = :idAlumno)
              AND (:estado IS NULL OR t.estado = :estado)
              AND (:desde IS NULL OR t.fechaTransaccion >= :desde)
              AND (:hasta IS NULL OR t.fechaTransaccion < :hasta)
              AND (:busqueda IS NULL
                   OR LOWER(t.numeroOperacion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.codigoCompromiso) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.codigoAlumno) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(p.nombres, ' ', p.apellidoPaterno, ' ', COALESCE(p.apellidoMaterno, '')))
                       LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true AND v.alumno.id = a.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                      AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
                      AND v.alumno.activo = true AND v.alumno.persona.activo = true))
            """)
    Page<TransaccionPago> buscarFiltradas(
            @Param("idCompromisoPago") Long idCompromisoPago,
            @Param("idAlumno") Long idAlumno,
            @Param("estado") com.asiscontrol.entity.enums.EstadoTransaccionPago estado,
            @Param("desde") java.time.Instant desde,
            @Param("hasta") java.time.Instant hasta,
            @Param("busqueda") String busqueda,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
