package com.asiscontrol.repository;

import com.asiscontrol.entity.CursoCompetencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CursoCompetenciaRepository extends JpaRepository<CursoCompetencia, Long> {

    Optional<CursoCompetencia> findByIdAndActivoTrue(Long id);

    Optional<CursoCompetencia> findByCursoIdAndCompetenciaIdAndActivoTrue(
            Long cursoId,
            Long competenciaId
    );

    Page<CursoCompetencia> findByActivoTrue(Pageable pageable);

    @Query("""
        SELECT cc FROM CursoCompetencia cc
        WHERE cc.activo = true
          AND cc.curso.activo = true
          AND cc.competencia.activo = true
        """)
    Page<CursoCompetencia> buscarDisponibles(Pageable pageable);

    @Query("""
        SELECT cc FROM CursoCompetencia cc
        WHERE cc.id = :id
          AND cc.activo = true
          AND cc.curso.activo = true
          AND cc.competencia.activo = true
        """)
    Optional<CursoCompetencia> buscarDisponiblePorId(@Param("id") Long id);
}
