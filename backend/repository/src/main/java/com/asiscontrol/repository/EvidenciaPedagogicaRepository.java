package com.asiscontrol.repository;

import com.asiscontrol.entity.EvidenciaPedagogica;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EvidenciaPedagogicaRepository extends JpaRepository<EvidenciaPedagogica, Long> {

    @EntityGraph(attributePaths = {"asignacion", "asignacion.curso", "archivo"})
    @Query("""
            SELECT e FROM EvidenciaPedagogica e
            WHERE e.activo = true AND e.asignacion.id = :asignacion AND e.alumno.id = :alumno
                AND (:competencia IS NULL OR e.competencia.id = :competencia)
                AND (:periodo IS NULL OR e.periodo.id = :periodo)
            """)
    Page<EvidenciaPedagogica> buscar(
            @Param("asignacion") Long asignacion, @Param("alumno") Long alumno,
            @Param("competencia") Long competencia, @Param("periodo") Long periodo, Pageable pagina);

    Optional<EvidenciaPedagogica> findByIdAndActivoTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EvidenciaPedagogica e WHERE e.id = :id AND e.activo = true")
    Optional<EvidenciaPedagogica> buscarParaCambio(@Param("id") Long id);

    boolean existsByArchivoIdAndActivoTrue(Long archivoId);
}
