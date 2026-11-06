package com.asiscontrol.repository;

import com.asiscontrol.entity.EntregaTarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EntregaTareaRepository extends JpaRepository<EntregaTarea, Long> {

    java.util.List<EntregaTarea> findAllByAlumnoIdAndTareaIdInAndActivoTrue(
            Long alumnoId, java.util.Collection<Long> tareaIds);

    Optional<EntregaTarea> findByIdAndActivoTrue(Long id);

    Optional<EntregaTarea> findByTareaIdAndAlumnoIdAndActivoTrue(Long tareaId, Long alumnoId);

    boolean existsByTareaIdAndAlumnoIdAndActivoTrue(Long tareaId, Long alumnoId);

    Page<EntregaTarea> findAllByTareaIdAndActivoTrue(Long tareaId, Pageable pageable);

    /** US-22: entregas de la tarea para el padrón de seguimiento y el recálculo de estados. */
    @EntityGraph(attributePaths = {"alumno.persona"})
    List<EntregaTarea> findAllByTareaIdAndActivoTrue(Long tareaId);

    long countByTareaIdAndActivoTrue(Long tareaId);

    @Query("SELECT MAX(e.nota) FROM EntregaTarea e WHERE e.activo = true AND e.tarea.id = :tareaId")
    Integer maxNotaPorTarea(@Param("tareaId") Long tareaId);

    @Query("""
            SELECT e FROM EntregaTarea e
            WHERE e.activo = true
              AND e.tarea.id = :tareaId
              AND (:personaDocenteId IS NULL
                   OR e.tarea.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL OR e.alumno.persona.id = :personaAlumnoId)
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = e.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                  ))
            """)
    Page<EntregaTarea> buscarVisibles(
            @Param("tareaId") Long tareaId,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
