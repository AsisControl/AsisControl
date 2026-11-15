package com.asiscontrol.repository;

import com.asiscontrol.entity.OrdenPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface OrdenPagoRepository extends JpaRepository<OrdenPago, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrdenPago> findByApoderadoIdAndClaveIdempotencia(Long apoderadoId, String claveIdempotencia);
    Optional<OrdenPago> findByCodigo(String codigo);
    Optional<OrdenPago> findByQrReferenciaOrCodigo(String qrReferencia, String codigo);

    @Query("""
            select count(d)>0 from OrdenPagoDetalle d
            where d.activo=true and d.orden.activo=true and d.compromiso.id in :compromisos
              and d.orden.estado in (com.asiscontrol.entity.enums.EstadoOrdenPago.PENDIENTE,
                                    com.asiscontrol.entity.enums.EstadoOrdenPago.REQUIERE_AUTENTICACION)
              and d.orden.expiraEn>:ahora
            """)
    boolean existePagoVigente(@Param("compromisos") List<Long> compromisos, @Param("ahora") Instant ahora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenPago o where o.id=:id and o.activo=true")
    Optional<OrdenPago> bloquearPorId(@Param("id") Long id);

    @Query("""
            select o from OrdenPago o
            where o.activo=true and o.apoderado.id=:apoderadoId
              and not exists (
                select d.id from OrdenPagoDetalle d where d.orden=o
                  and not exists (
                    select v.id from AlumnoApoderado v
                    where v.activo=true and v.apoderado=o.apoderado
                      and v.apoderado.activo=true and v.apoderado.persona.activo=true
                      and v.alumno=d.compromiso.matricula.alumno
                      and v.alumno.activo=true and v.alumno.persona.activo=true))
            """)
    Page<OrdenPago> buscarPropias(@Param("apoderadoId") Long apoderadoId, Pageable pageable);
}
