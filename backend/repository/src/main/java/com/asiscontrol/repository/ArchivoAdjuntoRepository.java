package com.asiscontrol.repository;

import com.asiscontrol.entity.ArchivoAdjunto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArchivoAdjuntoRepository extends JpaRepository<ArchivoAdjunto, Long> {

    Optional<ArchivoAdjunto> findByIdAndActivoTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArchivoAdjunto a WHERE a.id = :id AND a.activo = true")
    Optional<ArchivoAdjunto> buscarActivoParaVincularOEliminar(@Param("id") Long id);

    List<ArchivoAdjunto> findAllByIdInAndActivoTrue(Collection<Long> ids);
}
