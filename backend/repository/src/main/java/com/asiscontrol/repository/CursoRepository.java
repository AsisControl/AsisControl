package com.asiscontrol.repository;

import com.asiscontrol.entity.Curso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CursoRepository extends JpaRepository<Curso, Long> {

    Optional<Curso> findByIdAndActivoTrue(Long id);

    Optional<Curso> findByCodigoIgnoreCaseAndActivoTrue(String codigo);

    Optional<Curso> findByCodigoIgnoreCase(String codigo);

    @Query("""
            SELECT c FROM Curso c
            LEFT JOIN c.gradoAcademico g
            LEFT JOIN g.anioAcademico a
            WHERE c.activo = true
              AND (:busqueda IS NULL
                   OR LOWER(c.codigo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:anio IS NULL OR a.anio = :anio)
              AND (:grado IS NULL OR g.numero = :grado)
            """)
    Page<Curso> buscar(
            @Param("busqueda") String busqueda,
            @Param("anio") Integer anio,
            @Param("grado") Integer grado,
            Pageable pageable);
}
