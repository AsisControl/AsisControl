package com.asiscontrol.repository;

import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.enums.EstadoMatricula;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {
    boolean existsBySeccionId(Long id);

    boolean existsBySeccionIdAndActivoTrueAndEstado(Long id, EstadoMatricula estado);

    Optional<Matricula> findByIdAndActivoTrue(Long id);

    Optional<Matricula> findBySolicitudMatriculaIdAndActivoTrue(Long solicitudMatriculaId);

    boolean existsByAlumnoIdAndAnioAcademico(Long alumnoId, int anioAcademico);

    boolean existsByAlumnoIdAndAnioAcademicoAndEstadoAndActivoTrue(
            Long alumnoId, int anioAcademico, EstadoMatricula estado);

    boolean existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(
            Long alumnoId, Long seccionId, EstadoMatricula estado);

    Page<Matricula> findByActivoTrue(Pageable pageable);

    @Query(
            """
            SELECT m FROM Matricula m
            WHERE m.activo = true
              AND (:personaAlumnoId IS NULL OR m.alumno.persona.id = :personaAlumnoId)
              AND (:personaApoderadoId IS NULL OR EXISTS (
                    SELECT v.id FROM AlumnoApoderado v
                    WHERE v.activo = true
                      AND v.alumno.id = m.alumno.id
                      AND v.apoderado.persona.id = :personaApoderadoId
                  ))
            """)
    Page<Matricula> buscarVisibles(
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable);

    long countBySeccionIdAndEstadoAndActivoTrue(Long seccionId, EstadoMatricula estado);
}
