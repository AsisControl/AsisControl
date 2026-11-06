package com.asiscontrol.repository;

import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CompromisoPagoRepository extends JpaRepository<CompromisoPago, Long> {

    @Query("""
            SELECT COUNT(c) > 0 FROM CompromisoPago c
            WHERE c.activo = true
              AND c.estadoAdministrativo = com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso.ACTIVO
              AND c.matricula.alumno.id = :alumnoId AND c.matricula.anioAcademico = :anio
              AND c.conceptoNormalizado = :concepto
              AND (c.numeroCuota = :cuota OR c.numeroCuota IS NULL)
            """)
    boolean existeObligacionCoincidente(
            @Param("alumnoId") Long alumnoId,
            @Param("anio") int anio,
            @Param("concepto") String concepto,
            @Param("cuota") Integer cuota);

    @Query("""
            SELECT c FROM CompromisoPago c
            JOIN c.matricula m JOIN m.alumno a JOIN a.persona p
            WHERE c.activo = true
              AND (:estadoAdministrativo IS NULL OR c.estadoAdministrativo = :estadoAdministrativo)
              AND (:idMatricula IS NULL OR m.id = :idMatricula)
              AND (:idAlumno IS NULL OR a.id = :idAlumno)
              AND (:anioAcademico IS NULL OR m.anioAcademico = :anioAcademico)
              AND (:estado IS NULL
                   OR (:estado = 'ANULADO'
                       AND c.estadoAdministrativo = com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso.ANULADO)
                   OR (c.estadoAdministrativo = com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso.ACTIVO
                       AND ((:estado = 'PAGADO' AND c.montoPagado >= c.montoTotal)
                         OR (:estado = 'VENCIDO' AND c.montoPagado < c.montoTotal AND c.fechaVencimiento < :hoy)
                         OR (:estado = 'PARCIAL' AND c.montoPagado > 0 AND c.montoPagado < c.montoTotal AND c.fechaVencimiento >= :hoy)
                         OR (:estado = 'PENDIENTE' AND c.montoPagado = 0 AND c.fechaVencimiento >= :hoy))))
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true AND v.alumno.id = a.id
                      AND v.apoderado.persona.id = :personaApoderadoId))
              AND (:busqueda IS NULL
                   OR LOWER(c.codigoCompromiso) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.concepto) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.codigoAlumno) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(p.nombres, ' ', p.apellidoPaterno, ' ', COALESCE(p.apellidoMaterno, '')))
                       LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<CompromisoPago> buscarAdministrados(
            @Param("estado") String estado,
            @Param("estadoAdministrativo") com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso estadoAdministrativo,
            @Param("idMatricula") Long idMatricula,
            @Param("idAlumno") Long idAlumno,
            @Param("anioAcademico") Integer anioAcademico,
            @Param("busqueda") String busqueda,
            @Param("personaApoderadoId") Long personaApoderadoId,
            @Param("hoy") LocalDate hoy,
            Pageable pageable);

    Optional<CompromisoPago> findByIdAndActivoTrue(Long id);

    Optional<CompromisoPago> findByCodigoCompromisoIgnoreCase(String codigoCompromiso);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompromisoPago c WHERE c.id = :id AND c.activo = true")
    Optional<CompromisoPago> bloquearPorId(@Param("id") Long id);

    @Query("""
            SELECT c FROM CompromisoPago c
            WHERE c.activo = true
              AND (:estado IS NULL OR c.estado = :estado)
              AND (:idMatricula IS NULL OR c.matricula.id = :idMatricula)
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = c.matricula.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                  ))
              AND (:busqueda IS NULL
                   OR LOWER(c.codigoCompromiso) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.concepto) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<CompromisoPago> buscar(
            @Param("estado") EstadoCompromisoPago estado,
            @Param("idMatricula") Long idMatricula,
            @Param("busqueda") String busqueda,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
