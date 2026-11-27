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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evaluacion e where e.id = :id and e.activo = true")
    Optional<Evaluacion> buscarActivaParaEscritura(Long id);

    @Query("""
            SELECT DISTINCT e FROM Evaluacion e LEFT JOIN FETCH e.competencias
            WHERE e.activo = true AND e.cuentaResultado = true AND e.asignacionCurso.id = :asignacion
            AND e.fechaEvaluacion BETWEEN :inicio AND :fin
            AND e.estado IN (com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                            com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA)
            ORDER BY e.fechaEvaluacion, e.id
            """)
    java.util.List<Evaluacion> publicadasParaInforme(@Param("asignacion") Long asignacion,
            @Param("inicio") java.time.LocalDate inicio, @Param("fin") java.time.LocalDate fin);

    @Query("""
            SELECT DISTINCT alumno, e FROM Matricula m JOIN m.alumno alumno, Evaluacion e
            JOIN e.asignacionCurso a
            WHERE m.activo = true AND alumno.activo = true AND e.activo = true AND e.cuentaResultado = true AND a.activo = true
            AND (m.seccion = a.seccion OR EXISTS (SELECT h.id FROM HistorialSeccionMatricula h
                WHERE h.activo = true AND h.matricula = m AND """ + VisibilidadSeccionAnterior.EVALUACION + """
            ))
            AND m.estado IN (com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                             com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA)
            AND e.estado IN (com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                            com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA)
            AND (:docente IS NULL OR a.docente.persona.id = :docente)
            AND (:estudiante IS NULL OR alumno.persona.id = :estudiante)
            AND (:apoderado IS NULL OR EXISTS (SELECT v.id FROM AlumnoApoderado v
                WHERE v.activo = true AND v.apoderado.activo = true AND v.alumno = alumno
                AND v.apoderado.persona.id = :apoderado))
            ORDER BY alumno.id, e.id
            """)
    java.util.List<Object[]> informesPermitidos(@Param("docente") Long docente,
            @Param("estudiante") Long estudiante, @Param("apoderado") Long apoderado);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM Evaluacion e WHERE e.activo = true AND e.cuentaResultado = true
            AND e.asignacionCurso.id = :asignacion
            AND e.fechaEvaluacion BETWEEN :inicio AND :fin
            AND e.estado = com.asiscontrol.entity.enums.EstadoEvaluacion.BORRADOR
            ORDER BY e.id
            """)
    java.util.List<Evaluacion> borradoresParaPublicar(@Param("asignacion") Long asignacion,
            @Param("inicio") java.time.LocalDate inicio, @Param("fin") java.time.LocalDate fin);

    Optional<Evaluacion> findByIdAndActivoTrue(Long id);

    @Query("""
            SELECT e FROM Evaluacion e
            WHERE e.activo = true
              AND (:estado IS NULL OR e.estado = :estado)
              AND (:vinculables = false OR (e.cuentaResultado = true
                   AND e.modeloCualitativo = true
                   AND e.estado = com.asiscontrol.entity.enums.EstadoEvaluacion.BORRADOR
                   AND NOT EXISTS (SELECT c.id FROM Calificacion c WHERE c.evaluacion = e)
                   AND NOT EXISTS (SELECT t.id FROM Tarea t WHERE t.evaluacionAcademica = e
                                   AND (:tareaActual IS NULL OR t.id <> :tareaActual))))
              AND (:idAsignacionCurso IS NULL OR e.asignacionCurso.id = :idAsignacionCurso)
              AND (:periodo IS NULL OR e.periodoEvaluacion = :periodo)
              AND (:idCurso IS NULL OR e.asignacionCurso.curso.id = :idCurso)
              AND (:idSeccion IS NULL OR e.asignacionCurso.seccion.id = :idSeccion)
              AND (:personaDocenteId IS NULL
                   OR e.asignacionCurso.docente.persona.id = :personaDocenteId)
              AND (:personaAlumnoId IS NULL OR (
                    e.cuentaResultado = true AND e.estado IN (
                        com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                        com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
                    )
                    AND (EXISTS (
                        SELECT m.id FROM Matricula m
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = e.asignacionCurso.seccion.id
                          AND m.alumno.persona.id = :personaAlumnoId
                    ) OR EXISTS (
                        SELECT h.id FROM HistorialSeccionMatricula h
                        WHERE h.matricula.alumno.persona.id = :personaAlumnoId
                          AND """ + VisibilidadSeccionAnterior.MATRICULA_VIGENTE + " AND " + VisibilidadSeccionAnterior.EVALUACION + """
                    ))
                  ))
              AND (:personaApoderadoId IS NULL OR (
                    e.cuentaResultado = true AND e.estado IN (
                        com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                        com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA
                    )
                    AND (EXISTS (
                        SELECT m.id FROM Matricula m, AlumnoApoderado v
                        WHERE m.activo = true
                          AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                          AND m.seccion.id = e.asignacionCurso.seccion.id
                          AND v.activo = true
                          AND v.alumno.id = m.alumno.id
                          AND v.apoderado.persona.id = :personaApoderadoId
                    ) OR EXISTS (
                        SELECT h.id FROM HistorialSeccionMatricula h, AlumnoApoderado v
                        WHERE v.activo = true AND v.alumno.id = h.matricula.alumno.id
                          AND v.apoderado.persona.id = :personaApoderadoId
                          AND """ + VisibilidadSeccionAnterior.MATRICULA_VIGENTE + " AND " + VisibilidadSeccionAnterior.EVALUACION + """
                    ))
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
            @Param("vinculables") boolean vinculables,
            @Param("tareaActual") Long tareaActual,
            Pageable pageable
    );
}
