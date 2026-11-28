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
            SELECT n FROM NotaTareaSinEntrega n
            WHERE n.alumno.id = :alumno AND n.tarea.id IN :tareas
              AND n.tarea.evaluacionAcademica IS NULL AND n.nivelLogro IS NOT NULL
            """)
    java.util.List<com.asiscontrol.entity.NotaTareaSinEntrega> buscarNivelesSinEntrega(
            @Param("alumno") Long alumno, @Param("tareas") java.util.List<Long> tareas);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tarea t WHERE t.id = :id AND t.activo = true")
    Optional<Tarea> buscarActivaParaPublicar(@Param("id") Long id);

    @Query("""
            SELECT t FROM Tarea t
            WHERE t.activo = true
              AND t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
              AND (t.fechaPublicacion IS NULL OR t.fechaPublicacion <= :ahora)
              AND (EXISTS (SELECT m.id FROM Matricula m WHERE m.activo = true
                  AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                  AND m.alumno.id = :alumnoId AND m.seccion.id = t.asignacionCurso.seccion.id)
                  OR EXISTS (SELECT h.id FROM HistorialSeccionMatricula h
                      WHERE h.matricula.alumno.id = :alumnoId AND """ + VisibilidadSeccionAnterior.MATRICULA_VIGENTE
                      + " AND " + VisibilidadSeccionAnterior.TAREA + """
                  ))
              AND (LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                  OR LOWER(coalesce(t.asignacionCurso.nombreCursoPlan, t.asignacionCurso.curso.nombre))
                      LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:entregada IS NULL
                  OR (:entregada = true AND EXISTS (SELECT e.id FROM EntregaTarea e WHERE e.activo = true
                      AND e.tarea.id = t.id AND e.alumno.id = :alumnoId))
                  OR (:entregada = false AND NOT EXISTS (SELECT e.id FROM EntregaTarea e WHERE e.activo = true
                      AND e.tarea.id = t.id AND e.alumno.id = :alumnoId)
                      AND EXISTS (SELECT actual.id FROM Matricula actual WHERE actual.activo = true
                          AND actual.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND actual.alumno.id = :alumnoId
                          AND actual.seccion.id = t.asignacionCurso.seccion.id)))
            """)
    Page<Tarea> buscarParaAlumno(@Param("alumnoId") Long alumnoId,
            @Param("busqueda") String busqueda, @Param("entregada") Boolean entregada,
            @Param("ahora") java.time.LocalDateTime ahora, Pageable pageable);

    Optional<Tarea> findByIdAndActivoTrue(Long id);

    Optional<Tarea> findByEvaluacionAcademicaId(Long id);

    Page<Tarea> findAllByActivoTrue(Pageable pageable);

    Page<Tarea> findAllByAsignacionCursoIdAndActivoTrue(Long asignacionCursoId, Pageable pageable);

    @Query("""
            SELECT t FROM Tarea t LEFT JOIN t.evaluacionAcademica evaluacion
            WHERE t.activo = true
              AND (:asignacionCursoId IS NULL OR t.asignacionCurso.id = :asignacionCursoId)
              AND (:cursoId IS NULL OR t.asignacionCurso.curso.id = :cursoId)
              AND (:estado IS NULL OR t.estadoPublicacion = :estado)
              AND (:search = '' OR locate(:search, lower(t.nombre)) > 0
                   OR locate(:search, lower(t.asignacionCurso.curso.nombre)) > 0
                   OR locate(:search, lower(evaluacion.titulo)) > 0)
              AND (:personaDocenteId IS NULL OR t.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL OR (
                    t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                    AND (EXISTS (
                        SELECT m.id FROM Matricula m
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = t.asignacionCurso.seccion.id
                          AND m.alumno.persona.id = :personaAlumnoId
                    ) OR EXISTS (
                        SELECT h.id FROM HistorialSeccionMatricula h
                        WHERE h.matricula.alumno.persona.id = :personaAlumnoId
                          AND """ + VisibilidadSeccionAnterior.MATRICULA_VIGENTE + " AND " + VisibilidadSeccionAnterior.TAREA + """
                    ))
                  ))
              AND (:personaApoderadoId IS NULL OR (
                    t.estadoPublicacion <> com.asiscontrol.entity.enums.EstadoPublicacionTarea.BORRADOR
                    AND (EXISTS (
                        SELECT m.id FROM Matricula m, AlumnoApoderado v
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = t.asignacionCurso.seccion.id
                          AND v.activo = true
                          AND v.alumno.id = m.alumno.id
                          AND v.apoderado.persona.id = :personaApoderadoId
                    ) OR EXISTS (
                        SELECT h.id FROM HistorialSeccionMatricula h, AlumnoApoderado v
                        WHERE v.activo = true AND v.alumno.id = h.matricula.alumno.id
                          AND v.apoderado.persona.id = :personaApoderadoId
                          AND """ + VisibilidadSeccionAnterior.MATRICULA_VIGENTE + " AND " + VisibilidadSeccionAnterior.TAREA + """
                    ))
                  ))
            """)
    Page<Tarea> buscarVisibles(
            @Param("asignacionCursoId") Long asignacionCursoId,
            @Param("cursoId") Long cursoId,
            @Param("estado") com.asiscontrol.entity.enums.EstadoPublicacionTarea estado,
            @Param("search") String search,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
