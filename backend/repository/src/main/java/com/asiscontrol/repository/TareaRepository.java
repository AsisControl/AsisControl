package com.asiscontrol.repository;

import com.asiscontrol.entity.Tarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    @Query("""
            SELECT t FROM Tarea t
            WHERE t.activo = true
              AND t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
              AND (t.fechaPublicacion IS NULL OR t.fechaPublicacion <= :ahora)
              AND EXISTS (SELECT m.id FROM Matricula m WHERE m.activo = true
                  AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                  AND m.alumno.id = :alumnoId AND m.seccion.id = t.asignacionCurso.seccion.id)
              AND (LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                  OR LOWER(t.asignacionCurso.curso.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:entregada IS NULL
                  OR (:entregada = true AND EXISTS (SELECT e.id FROM EntregaTarea e WHERE e.activo = true
                      AND e.tarea.id = t.id AND e.alumno.id = :alumnoId))
                  OR (:entregada = false AND NOT EXISTS (SELECT e.id FROM EntregaTarea e WHERE e.activo = true
                      AND e.tarea.id = t.id AND e.alumno.id = :alumnoId)))
            """)
    Page<Tarea> buscarParaAlumno(@Param("alumnoId") Long alumnoId,
            @Param("busqueda") String busqueda, @Param("entregada") Boolean entregada,
            @Param("ahora") java.time.LocalDateTime ahora, Pageable pageable);

    Optional<Tarea> findByIdAndActivoTrue(Long id);

    Page<Tarea> findAllByActivoTrue(Pageable pageable);

    Page<Tarea> findAllByAsignacionCursoIdAndActivoTrue(Long asignacionCursoId, Pageable pageable);

    @Query("""
            SELECT t FROM Tarea t
            WHERE t.activo = true
              AND (:asignacionCursoId IS NULL OR t.asignacionCurso.id = :asignacionCursoId)
              AND (:personaDocenteId IS NULL OR t.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL OR (
                    t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                    AND EXISTS (
                        SELECT m.id FROM Matricula m
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = t.asignacionCurso.seccion.id
                          AND m.alumno.persona.id = :personaAlumnoId
                    )
                  ))
              AND (:personaApoderadoId IS NULL OR (
                    t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                    AND EXISTS (
                        SELECT m.id FROM Matricula m, AlumnoApoderado v
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = t.asignacionCurso.seccion.id
                          AND v.activo = true
                          AND v.alumno.id = m.alumno.id
                          AND v.apoderado.persona.id = :personaApoderadoId
                    )
                  ))
            """)
    Page<Tarea> buscarVisibles(
            @Param("asignacionCursoId") Long asignacionCursoId,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
