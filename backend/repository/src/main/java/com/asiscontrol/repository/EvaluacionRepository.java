package com.asiscontrol.repository;

import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {

    Optional<Evaluacion> findByIdAndActivoTrue(Long id);

    @Query("""
            SELECT e FROM Evaluacion e
            WHERE e.activo = true
              AND (:estado IS NULL OR e.estado = :estado)
              AND (:idAsignacionCurso IS NULL OR e.asignacionCurso.id = :idAsignacionCurso)
              AND (:periodo IS NULL OR e.periodoEvaluacion = :periodo)
              AND (:idCurso IS NULL OR e.asignacionCurso.curso.id = :idCurso)
              AND (:idSeccion IS NULL OR e.asignacionCurso.seccion.id = :idSeccion)
              AND (:personaDocenteId IS NULL
                   OR e.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL OR (
                    e.estado IN (
                        com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                        com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
                    )
                    AND EXISTS (
                        SELECT m.id FROM Matricula m
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = e.asignacionCurso.seccion.id
                          AND m.alumno.persona.id = :personaAlumnoId
                    )
                  ))
              AND (:personaApoderadoId IS NULL OR (
                    e.estado IN (
                        com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                        com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
                    )
                    AND EXISTS (
                        SELECT m.id FROM Matricula m, AlumnoApoderado v
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = e.asignacionCurso.seccion.id
                          AND v.activo = true
                          AND v.alumno.id = m.alumno.id
                          AND v.apoderado.persona.id = :personaApoderadoId
                    )
                  ))
              AND (:busqueda IS NULL
                   OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(COALESCE(e.descripcion, '')) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<Evaluacion> buscar(
            @Param("estado") EstadoEvaluacion estado,
            @Param("idAsignacionCurso") Long idAsignacionCurso,
            @Param("periodo") com.asiscontrol.entity.enums.PeriodoEvaluacion periodo,
            @Param("idCurso") Long idCurso,
            @Param("idSeccion") Long idSeccion,
            @Param("busqueda") String busqueda,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable
    );
}
