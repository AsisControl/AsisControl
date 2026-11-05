package com.asiscontrol.repository;

import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface JustificacionAsistenciaRepository extends JpaRepository<JustificacionAsistencia, Long> {

    Optional<JustificacionAsistencia> findByIdAndActivoTrue(Long id);

    Page<JustificacionAsistencia> findAllByActivoTrue(Pageable pageable);

    Optional<JustificacionAsistencia> findByAsistenciaAlumnoIdAndActivoTrue(Long asistenciaAlumnoId);

    // asistencia alumnos
    java.util.List<JustificacionAsistencia> findAllByAsistenciaAlumnoIdInAndActivoTrue(
            Collection<Long> asistenciaAlumnoIds);

    Page<JustificacionAsistencia> findAllByEstadoInAndActivoTrue(
            Collection<EstadoJustificacion> estados, Pageable pageable);

    @Query("""
            SELECT j FROM JustificacionAsistencia j
            WHERE j.activo = true
              AND (:estados IS NULL OR j.estado IN :estados)
              AND (:personaDocenteId IS NULL
                   OR j.asistenciaAlumno.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL
                   OR j.asistenciaAlumno.alumno.persona.id = :personaAlumnoId)
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = j.asistenciaAlumno.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                  ))
            """)
    Page<JustificacionAsistencia> buscarVisibles(
            @Param("estados") Collection<EstadoJustificacion> estados,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
