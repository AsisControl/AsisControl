package com.asiscontrol.repository;

import com.asiscontrol.entity.AsignacionCurso;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AsignacionCursoRepository extends JpaRepository<AsignacionCurso, Long> {
    boolean existsBySeccionId(Long id);

    boolean existsBySeccionIdAndActivoTrue(Long id);

    boolean existsByCursoIdAndActivoTrue(Long cursoId);

    Optional<AsignacionCurso> findByIdAndActivoTrue(Long id);

    Optional<AsignacionCurso> findByCursoIdAndSeccionIdAndActivoTrue(Long cursoId, Long seccionId);

    Page<AsignacionCurso> findByActivoTrue(Pageable pageable);
}
