package com.asiscontrol.repository;

import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.enums.DiaSemana;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface HorarioRepository extends JpaRepository<Horario, Long> {

    Optional<Horario> findByIdAndActivoTrue(Long id);

    // asistencia alumnos
    List<Horario> findAllByAsignacionCursoIdAndActivoTrue(Long asignacionCursoId);

    // asistencia alumnos
    @Query("""
            SELECT DISTINCT h.asignacionCurso.id FROM Horario h
            WHERE h.activo = true AND h.asignacionCurso.seccion.anioAcademico = :anioAcademico
            """)
    List<Long> findAsignacionIdsConHorarioEnAnio(@Param("anioAcademico") int anioAcademico);

    Page<Horario> findByActivoTrue(Pageable pageable);

    @EntityGraph(attributePaths = {
            "aula", "asignacionCurso.curso", "asignacionCurso.seccion",
            "asignacionCurso.docente.persona"
    })
    @Query("""
            SELECT h FROM Horario h
            JOIN h.asignacionCurso a
            WHERE h.activo = true AND a.activo = true
              AND (:anioAcademico IS NULL OR a.seccion.anioAcademico = :anioAcademico)
              AND (:seccionId IS NULL OR a.seccion.id = :seccionId)
              AND (:docenteId IS NULL OR a.docente.id = :docenteId)
              AND (:aulaId IS NULL OR h.aula.id = :aulaId)
              AND (:cursoId IS NULL OR a.curso.id = :cursoId)
            """)
    Page<Horario> buscarConFiltros(
            @Param("anioAcademico") Integer anioAcademico,
            @Param("seccionId") Long seccionId,
            @Param("docenteId") Long docenteId,
            @Param("aulaId") Long aulaId,
            @Param("cursoId") Long cursoId,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "aula", "asignacionCurso.curso", "asignacionCurso.seccion",
            "asignacionCurso.docente.persona"
    })
    @Query("""
            SELECT h FROM Horario h
            JOIN h.asignacionCurso a
            JOIN Matricula m ON m.seccion.id = a.seccion.id
            WHERE h.activo = true AND a.activo = true
              AND m.activo = true
              AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
              AND m.alumno.activo = true AND m.alumno.persona.activo = true
              AND m.alumno.persona.id = :personaId
              AND (:anioAcademico IS NULL OR m.anioAcademico = :anioAcademico)
            ORDER BY a.seccion.anioAcademico, h.diaSemana, h.horaInicio, h.id
            """)
    List<Horario> buscarDelAlumno(
            @Param("personaId") Long personaId,
            @Param("anioAcademico") Integer anioAcademico);

    boolean existsByAulaId(Long aulaId);

    long countByAsignacionCursoIdAndActivoTrue(Long asignacionCursoId);

    /**
     * Bloques activos de una asignacion. Horario no tiene fecha: un bloque activo se repite cada
     * semana, asi que cuenta como programado a futuro (regla PLA-06).
     */
    @Query("""
            SELECT h FROM Horario h
            JOIN FETCH h.aula
            WHERE h.asignacionCurso.id = :asignacionCursoId
              AND h.activo = true
            ORDER BY h.diaSemana, h.horaInicio
            """)
    List<Horario> findActivosPorAsignacion(@Param("asignacionCursoId") Long asignacionCursoId);

    @Query("""
            SELECT h FROM Horario h
            JOIN FETCH h.asignacionCurso a
            JOIN FETCH a.seccion
            JOIN FETCH a.curso
            WHERE h.aula.id = :aulaId
              AND h.activo = true
            ORDER BY h.id
            """)
    List<Horario> findActivosPorAulaConSeccion(@Param("aulaId") Long aulaId);

    @Query("""
            SELECT h FROM Horario h
            JOIN FETCH h.aula
            JOIN FETCH h.asignacionCurso a
            JOIN FETCH a.curso
            JOIN FETCH a.seccion
            JOIN FETCH a.docente d
            JOIN FETCH d.persona
            WHERE h.aula.id = :aulaId
            ORDER BY h.activo DESC, h.id
            """)
    List<Horario> findHistorialPorAula(@Param("aulaId") Long aulaId);

    @Query("""
            SELECT h FROM Horario h
            WHERE h.activo = true
              AND h.asignacionCurso.activo = true
              AND h.asignacionCurso.seccion.anioAcademico = :anioAcademico
              AND h.diaSemana = :diaSemana
              AND h.horaInicio < :horaFin
              AND h.horaFin > :horaInicio
              AND (:horarioExcluidoId IS NULL OR h.id <> :horarioExcluidoId)
              AND (h.aula.id = :aulaId
                   OR h.asignacionCurso.docente.id = :docenteId
                   OR h.asignacionCurso.seccion.id = :seccionId)
            ORDER BY h.id
            """)
    List<Horario> buscarConflictos(
            @Param("anioAcademico") int anioAcademico,
            @Param("diaSemana") DiaSemana diaSemana,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("aulaId") Long aulaId,
            @Param("docenteId") Long docenteId,
            @Param("seccionId") Long seccionId,
            @Param("horarioExcluidoId") Long horarioExcluidoId
    );
}
