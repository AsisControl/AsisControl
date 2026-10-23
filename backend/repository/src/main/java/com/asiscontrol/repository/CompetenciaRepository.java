package com.asiscontrol.repository;

import com.asiscontrol.entity.Competencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompetenciaRepository extends JpaRepository<Competencia, Long> {

    Optional<Competencia> findByIdAndActivoTrue(Long id);

    Optional<Competencia> findByCodigoIgnoreCaseAndActivoTrue(String codigo);

    List<Competencia> findAllByIdInAndActivoTrue(Collection<Long> ids);

    @Query("""
            SELECT c FROM Competencia c
            WHERE c.activo = true
              AND (:busqueda IS NULL
                   OR LOWER(c.codigo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<Competencia> buscar(@Param("busqueda") String busqueda, Pageable pageable);
}
