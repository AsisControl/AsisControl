package com.asiscontrol.repository;

import com.asiscontrol.entity.Aula;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    Optional<Aula> findByIdAndActivoTrue(Long id);

    Optional<Aula> findByCodigoIgnoreCaseAndActivoTrue(String codigo);

    Optional<Aula> findByCodigoIgnoreCase(String codigo);

    Page<Aula> findByActivoTrue(Pageable pageable);

    Page<Aula> findByActivoFalse(Pageable pageable);

    long countByActivoTrue();
}
