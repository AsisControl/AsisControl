package com.asiscontrol.repository;

import com.asiscontrol.entity.CodigoQRAsistencia;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CodigoQRAsistenciaRepository extends JpaRepository<CodigoQRAsistencia, Long> {

    Optional<CodigoQRAsistencia> findByIdAndActivoTrue(Long id);

    Optional<CodigoQRAsistencia> findByTokenHashAndActivoTrue(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoQRAsistencia> findLockedByTokenHashAndActivoTrue(String tokenHash);

    List<CodigoQRAsistencia> findAllByEstadoAndActivoTrue(EstadoCodigoQR estado);

    /**
     * Cierra de una vez los códigos vigentes. Es una sola sentencia y no carga cada código: dos
     * aperturas simultáneas del punto de marcación no se pisan por el control de versiones.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CodigoQRAsistencia c
            SET c.estado = :nuevoEstado, c.fechaHoraCancelacion = :ahora, c.version = c.version + 1
            WHERE c.activo = true AND c.estado = :vigente AND c.fechaHoraExpiracion > :ahora
            """)
    int invalidarVigentes(
            @Param("ahora") LocalDateTime ahora,
            @Param("vigente") EstadoCodigoQR vigente,
            @Param("nuevoEstado") EstadoCodigoQR nuevoEstado);

    /** Marca como expirados los códigos vigentes cuya vigencia ya terminó. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CodigoQRAsistencia c
            SET c.estado = :nuevoEstado, c.version = c.version + 1
            WHERE c.activo = true AND c.estado = :vigente AND c.fechaHoraExpiracion <= :ahora
            """)
    int expirarVencidos(
            @Param("ahora") LocalDateTime ahora,
            @Param("vigente") EstadoCodigoQR vigente,
            @Param("nuevoEstado") EstadoCodigoQR nuevoEstado);
}
