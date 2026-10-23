package com.asiscontrol.repository;

import com.asiscontrol.entity.CursoCompetencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CursoCompetenciaRepository extends JpaRepository<CursoCompetencia, Long> {

    Optional<CursoCompetencia> findByIdAndActivoTrue(Long id);

    Optional<CursoCompetencia> findByCursoIdAndCompetenciaIdAndActivoTrue(
            Long cursoId,
            Long competenciaId
    );

    Page<CursoCompetencia> findByActivoTrue(Pageable pageable);
}
