package com.asiscontrol.repository;

import com.asiscontrol.entity.Permiso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    Optional<Permiso> findByCodigoIgnoreCase(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Permiso> findByCodigo(String codigo);

    List<Permiso> findAllByActivoTrueOrderByCodigoAsc();
}
