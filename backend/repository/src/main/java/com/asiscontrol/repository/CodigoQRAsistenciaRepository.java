package com.asiscontrol.repository;

import com.asiscontrol.entity.CodigoQRAsistencia;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CodigoQRAsistenciaRepository extends JpaRepository<CodigoQRAsistencia, Long> {

    Optional<CodigoQRAsistencia> findByIdAndActivoTrue(Long id);

    Optional<CodigoQRAsistencia> findByTokenHashAndActivoTrue(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoQRAsistencia> findLockedByTokenHashAndActivoTrue(String tokenHash);

    List<CodigoQRAsistencia> findAllByEstadoAndActivoTrue(EstadoCodigoQR estado);
}
