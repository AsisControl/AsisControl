package com.asiscontrol.repository;

import com.asiscontrol.entity.AlertaRiesgo;
import com.asiscontrol.entity.enums.EstadoAlerta;
import com.asiscontrol.entity.enums.NivelRiesgo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface AlertaRiesgoRepository extends JpaRepository<AlertaRiesgo, Long> {

    Optional<AlertaRiesgo> findByIdAndActivoTrue(Long id);

    Optional<AlertaRiesgo> findFirstByAlumnoIdAndCriterioIdAndEstadoInAndActivoTrue(
            Long idAlumno,
            Long idCriterio,
            Collection<EstadoAlerta> estados
    );

    boolean existsByCriterioIdAndEstadoInAndActivoTrue(
            Long idCriterio,
            Collection<EstadoAlerta> estados
    );

    @EntityGraph(attributePaths = {"alumno.persona", "criterio"})
    @Query("""
            SELECT a FROM AlertaRiesgo a
            WHERE a.activo = true
              AND (:idAlumno IS NULL OR a.alumno.id = :idAlumno)
              AND (:estado IS NULL OR a.estado = :estado)
              AND (:nivel IS NULL OR a.nivelRiesgo = :nivel)
              AND (:personaDocenteId IS NULL OR EXISTS (
                    SELECT ac.id FROM Matricula m, AsignacionCurso ac
                    WHERE m.activo = true
                      AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                      AND m.alumno.id = a.alumno.id
                      AND ac.activo = true
                      AND ac.seccion.id = m.seccion.id
                      AND ac.docente.persona.id = :personaDocenteId
                  ))
            """)
    Page<AlertaRiesgo> buscar(
            @Param("idAlumno") Long idAlumno,
            @Param("estado") EstadoAlerta estado,
            @Param("nivel") NivelRiesgo nivel,
            @Param("personaDocenteId") Long personaDocenteId,
            Pageable pageable
    );
}
