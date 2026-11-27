package com.asiscontrol.repository;

import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.enums.EstadoMatricula;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /** US-18: alumnos con matricula activa en la seccion, por apellidos. */
    @Query("""
            SELECT a FROM Matricula m
            JOIN m.alumno a
            JOIN FETCH a.persona p
            WHERE m.activo = true
              AND m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
              AND m.seccion.id = :seccionId
            ORDER BY p.apellidoPaterno, p.apellidoMaterno, p.nombres
            """)
    java.util.List<com.asiscontrol.entity.Alumno> buscarAlumnosActivosDeSeccion(@Param("seccionId") Long seccionId);

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

    @Query("""
            SELECT m FROM Matricula m
            JOIN m.alumno a JOIN a.persona p
            WHERE m.activo = true AND a.activo = true AND p.activo = true AND m.estado IN :estados
              AND (LOWER(a.codigoAlumno) LIKE :filtro
                   OR LOWER(CONCAT(p.nombres, ' ', p.apellidoPaterno, ' ', COALESCE(p.apellidoMaterno, '')))
                       LIKE :filtro)
            ORDER BY m.anioAcademico DESC, m.id DESC
            """)
    Page<Matricula> buscarParaRegistrarPromocion(@Param("estados") Collection<EstadoMatricula> estados,
            @Param("filtro") String filtro, Pageable pagina);

    @Query("""
            SELECT m FROM Matricula m
            JOIN m.alumno a JOIN a.persona p
            WHERE m.activo = true AND a.activo = true AND p.activo = true
              AND m.anioAcademico = :anio AND m.estado IN :estados
              AND NOT EXISTS (SELECT otra.id FROM Matricula otra
                  WHERE otra.activo = true AND otra.alumno.id = a.id AND otra.anioAcademico = :anio
                    AND otra.estado IN :estados AND (otra.fechaMatricula > m.fechaMatricula
                         OR (otra.fechaMatricula = m.fechaMatricula AND otra.id > m.id)))
              AND (:personaApoderadoId IS NULL OR EXISTS (SELECT v.id FROM AlumnoApoderado v
                  WHERE v.alumno.id = a.id AND v.activo = true AND v.apoderado.activo = true
                    AND v.apoderado.persona.activo = true AND v.apoderado.persona.id = :personaApoderadoId))
            ORDER BY p.apellidoPaterno, p.nombres, m.id
            """)
    List<Matricula> buscarUltimasValidasDelAnio(@Param("anio") int anio,
            @Param("estados") Collection<EstadoMatricula> estados,
            @Param("personaApoderadoId") Long personaApoderadoId);

    boolean existsBySeccionId(Long id);

    boolean existsByAnioAcademico(int anioAcademico);

    boolean existsBySeccionIdAndActivoTrueAndEstado(Long id, EstadoMatricula estado);

    Optional<Matricula> findByIdAndActivoTrue(Long id);
    Optional<Matricula> findFirstByAlumnoIdAndAnioAcademicoAndActivoTrueAndEstadoInOrderByFechaMatriculaDescIdDesc(
            Long alumnoId, int anio, Collection<EstadoMatricula> estados);
    boolean existsByAlumnoIdAndAnioAcademicoAndActivoTrueAndEstadoIn(
            Long alumnoId, int anio, Collection<EstadoMatricula> estados);
    boolean existsByAlumnoIdAndAnioAcademicoLessThanAndActivoTrueAndEstadoIn(
            Long alumnoId, int anio, Collection<EstadoMatricula> estados);

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
              AND (:alumnoId IS NULL OR m.alumno.id = :alumnoId)
              AND (:anioAcademico IS NULL OR m.anioAcademico = :anioAcademico)
              AND (:grado IS NULL OR m.seccion.grado = :grado)
              AND (:seccionId IS NULL OR m.seccion.id = :seccionId)
              AND (:vigentes IS NULL OR :vigentes = false OR m.estado IN (
                    com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                    com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA))
              AND (:busqueda IS NULL
                   OR LOWER(m.codigoMatricula) LIKE :busqueda
                   OR LOWER(m.alumno.codigoAlumno) LIKE :busqueda
                   OR LOWER(m.alumno.persona.numeroDocumento) LIKE :busqueda
                   OR LOWER(CONCAT(m.alumno.persona.nombres, ' ', m.alumno.persona.apellidoPaterno, ' ',
                                   COALESCE(m.alumno.persona.apellidoMaterno, ''))) LIKE :busqueda)
            """)
    Page<Matricula> buscarVisibles(
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            @Param("alumnoId") Long alumnoId,
            @Param("anioAcademico") Integer anioAcademico,
            @Param("grado") Integer grado,
            @Param("seccionId") Long seccionId,
            @Param("vigentes") Boolean vigentes,
            @Param("busqueda") String busqueda,
            Pageable pageable);

    /** El estudiante cursa o cursó en la sección con una matrícula activa o finalizada. */
    @Query("""
            SELECT COUNT(m) > 0 FROM Matricula m
            WHERE m.activo = true AND m.alumno.id = :alumnoId
              AND m.estado IN (com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA,
                               com.asiscontrol.entity.enums.EstadoMatricula.FINALIZADA)
              AND (m.seccion.id = :seccionId OR EXISTS (SELECT h.id FROM HistorialSeccionMatricula h
                    WHERE h.activo = true AND h.matricula = m AND h.seccionAnterior.id = :seccionId))
            """)
    boolean estuvoEnSeccion(@Param("alumnoId") Long alumnoId, @Param("seccionId") Long seccionId);

    long countBySeccionIdAndEstadoAndActivoTrue(Long seccionId, EstadoMatricula estado);
}
