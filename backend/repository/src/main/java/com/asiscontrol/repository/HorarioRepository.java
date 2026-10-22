package com.asiscontrol.repository;

import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.enums.DiaSemana;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface HorarioRepository extends JpaRepository<Horario, Long> {

    Optional<Horario> findByIdAndActivoTrue(Long id);

    Page<Horario> findByActivoTrue(Pageable pageable);

    boolean existsByAulaId(Long aulaId);

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
              AND h.diaSemana = :diaSemana
              AND h.horaInicio < :horaFin
              AND h.horaFin > :horaInicio
              AND (:horarioExcluidoId IS NULL OR h.id <> :horarioExcluidoId)
              AND (h.aula.id = :aulaId
                   OR h.asignacionCurso.docente.id = :docenteId
                   OR h.asignacionCurso.seccion.id = :seccionId)
            """)
    List<Horario> buscarConflictos(
            @Param("diaSemana") DiaSemana diaSemana,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("aulaId") Long aulaId,
            @Param("docenteId") Long docenteId,
            @Param("seccionId") Long seccionId,
            @Param("horarioExcluidoId") Long horarioExcluidoId
    );
}
