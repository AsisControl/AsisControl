package com.asiscontrol.repository;

import com.asiscontrol.entity.Aula;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    Optional<Aula> findByIdAndActivoTrue(Long id);

    Optional<Aula> findByCodigoIgnoreCaseAndActivoTrue(String codigo);

    Optional<Aula> findByCodigoIgnoreCase(String codigo);

    Page<Aula> findByActivoTrue(Pageable pageable);

    Page<Aula> findByActivoFalse(Pageable pageable);

    long countByActivoTrue();

    /** Serializa la asignación del aula a una sección con los cambios del aula. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Aula a WHERE a.id = :id")
    Optional<Aula> bloquearPorId(@Param("id") Long id);
}
