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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Matricula m where m.id = :id and m.activo = true")
    Optional<Matricula> bloquearPorId(@Param("id") Long id);

    @Query("""
            SELECT m FROM Matricula m
            JOIN m.alumno a JOIN a.persona p
            WHERE m.activo = true AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
              AND a.activo = true AND p.activo = true
              AND EXISTS (SELECT y.id FROM AnioAcademico y
                          WHERE y.anio = m.anioAcademico AND y.activo = true
                            AND y.estado = com.asiscontrol.entity.enums.EstadoAnioAcademico.ACTIVO)
              AND (:busqueda IS NULL
                   OR LOWER(m.codigoMatricula) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.codigoAlumno) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(p.nombres, ' ', p.apellidoPaterno, ' ', COALESCE(p.apellidoMaterno, '')))
                       LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
    Page<Matricula> buscarParaCompromisos(@Param("busqueda") String busqueda, Pageable pageable);
    boolean existsBySeccionId(Long id);

    boolean existsByAnioAcademico(int anioAcademico);

    boolean existsBySeccionIdAndActivoTrueAndEstado(Long id, EstadoMatricula estado);

    Optional<Matricula> findByIdAndActivoTrue(Long id);

    Optional<Matricula> findBySolicitudMatriculaIdAndActivoTrue(Long solicitudMatriculaId);

    boolean existsByAlumnoIdAndAnioAcademico(Long alumnoId, int anioAcademico);

    boolean existsByAlumnoIdAndAnioAcademicoAndEstadoAndActivoTrue(
            Long alumnoId, int anioAcademico, EstadoMatricula estado);

    boolean existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(
            Long alumnoId, Long seccionId, EstadoMatricula estado);

    Page<Matricula> findByActivoTrue(Pageable pageable);

    // asistencia alumnos: estudiantes de la sección que deben figurar en su asistencia (R02)
    @Query("""
            SELECT m.alumno FROM Matricula m
            WHERE m.activo = true AND m.seccion.id = :seccionId AND m.estado = :estado
            ORDER BY m.alumno.persona.apellidoPaterno, m.alumno.persona.apellidoMaterno, m.alumno.persona.nombres
            """)
    java.util.List<com.asiscontrol.entity.Alumno> findAlumnosPorSeccionYEstado(
            @Param("seccionId") Long seccionId, @Param("estado") EstadoMatricula estado);

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
