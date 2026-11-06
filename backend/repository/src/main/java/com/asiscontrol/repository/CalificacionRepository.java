package com.asiscontrol.repository;

import com.asiscontrol.entity.Calificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {

    Optional<Calificacion> findByIdAndActivoTrue(Long id);

    List<Calificacion> findAllByEvaluacionIdAndActivoTrue(Long idEvaluacion);

    Optional<Calificacion> findByEvaluacionIdAndAlumnoIdAndCompetenciaIdAndActivoTrue(
            Long idEvaluacion,
            Long idAlumno,
            Long idCompetencia
    );

    Optional<Calificacion> findByEvaluacionIdAndAlumnoIdAndCompetenciaId(
            Long idEvaluacion,
            Long idAlumno,
            Long idCompetencia
    );

    @Query("""
            SELECT c FROM Calificacion c
            WHERE c.activo = true
              AND (:idEvaluacion IS NULL OR c.evaluacion.id = :idEvaluacion)
              AND (:idAlumno IS NULL OR c.alumno.id = :idAlumno)
              AND (:idCompetencia IS NULL OR c.competencia.id = :idCompetencia)
              AND (:personaDocenteId IS NULL
                   OR c.evaluacion.asignacionCurso.docente.persona.id = :personaDocenteId)
            """)
    Page<Calificacion> buscar(
            @Param("idEvaluacion") Long idEvaluacion,
            @Param("idAlumno") Long idAlumno,
            @Param("idCompetencia") Long idCompetencia,
            @Param("personaDocenteId") Long personaDocenteId,
            Pageable pageable
    );

    @Query("""
            SELECT c FROM Calificacion c
            JOIN FETCH c.evaluacion e
            JOIN FETCH c.competencia cp
            WHERE c.activo = true
              AND c.alumno.id = :idAlumno
              AND e.activo = true
              AND e.estado IN (
                  com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                  com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
              )
              AND (:idAsignacionCurso IS NULL OR e.asignacionCurso.id = :idAsignacionCurso)
            """)
    List<Calificacion> buscarParaConsolidado(
            @Param("idAlumno") Long idAlumno,
            @Param("idAsignacionCurso") Long idAsignacionCurso
    );
}
