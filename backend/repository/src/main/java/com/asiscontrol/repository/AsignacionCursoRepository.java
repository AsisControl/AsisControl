package com.asiscontrol.repository;

import com.asiscontrol.entity.AsignacionCurso;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsignacionCursoRepository extends JpaRepository<AsignacionCurso, Long> {
    boolean existsBySeccionId(Long id);

    boolean existsBySeccionIdAndActivoTrue(Long id);

    boolean existsByCursoIdAndActivoTrue(Long cursoId);

    Optional<AsignacionCurso> findByIdAndActivoTrue(Long id);

    Optional<AsignacionCurso> findByCursoIdAndSeccionIdAndActivoTrue(Long cursoId, Long seccionId);

    /** Activa o retirada: la restriccion unica (curso, seccion) no distingue el estado. */
    Optional<AsignacionCurso> findByCursoIdAndSeccionId(Long cursoId, Long seccionId);

    Page<AsignacionCurso> findByActivoTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"curso", "seccion", "docente.persona"})
    @Query("""
            SELECT a FROM AsignacionCurso a
            WHERE (:activo IS NULL OR a.activo = :activo)
              AND (:docenteId IS NULL OR a.docente.id = :docenteId)
              AND (:cursoId IS NULL OR a.curso.id = :cursoId)
              AND (:seccionId IS NULL OR a.seccion.id = :seccionId)
              AND (:anioAcademico IS NULL OR a.seccion.anioAcademico = :anioAcademico)
              AND (:personaDocenteId IS NULL OR a.docente.persona.id = :personaDocenteId)
            """)
    Page<AsignacionCurso> buscar(
            @Param("activo") Boolean activo,
            @Param("docenteId") Long docenteId,
            @Param("cursoId") Long cursoId,
            @Param("seccionId") Long seccionId,
            @Param("anioAcademico") Integer anioAcademico,
            @Param("personaDocenteId") Long personaDocenteId,
            Pageable pageable);

    /** Asignaciones de cualquier estado con su contexto, sin una consulta por fila. */
    @EntityGraph(attributePaths = {"curso", "seccion", "docente.persona"})
    List<AsignacionCurso> findAllByIdIn(Collection<Long> ids);

    @Query("""
            SELECT a.id FROM AsignacionCurso a
            WHERE (:anioAcademico IS NULL OR a.seccion.anioAcademico = :anioAcademico)
              AND (:seccionId IS NULL OR a.seccion.id = :seccionId)
              AND (:docenteId IS NULL OR a.docente.id = :docenteId)
            """)
    List<Long> buscarIdsPorContexto(
            @Param("anioAcademico") Integer anioAcademico,
            @Param("seccionId") Long seccionId,
            @Param("docenteId") Long docenteId);
}
