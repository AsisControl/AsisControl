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

    @Query("""
            SELECT c FROM Curso c
            WHERE c.activo = true
              AND (:busqueda IS NULL
                   OR LOWER(c.codigo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<Curso> buscar(@Param("busqueda") String busqueda, Pageable pageable);
}
