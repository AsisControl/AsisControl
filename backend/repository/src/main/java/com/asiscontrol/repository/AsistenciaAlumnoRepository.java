package com.asiscontrol.repository;

import com.asiscontrol.entity.AsistenciaAlumno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface AsistenciaAlumnoRepository extends JpaRepository<AsistenciaAlumno, Long> {

    Optional<AsistenciaAlumno> findByIdAndActivoTrue(Long id);

    @Query("""
            SELECT a FROM AsistenciaAlumno a
            WHERE a.activo = true
              AND (:alumnoId IS NULL OR a.alumno.id = :alumnoId)
              AND (:asignacionCursoId IS NULL OR a.asignacionCurso.id = :asignacionCursoId)
              AND (:fechaRegistro IS NULL OR a.fechaRegistro = :fechaRegistro)
              AND (:personaDocenteId IS NULL OR a.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL OR a.alumno.persona.id = :personaAlumnoId)
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = a.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                  ))
            """)
    Page<AsistenciaAlumno> buscarActivas(
            @Param("alumnoId") Long alumnoId,
            @Param("asignacionCursoId") Long asignacionCursoId,
            @Param("fechaRegistro") LocalDate fechaRegistro,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable);
}
