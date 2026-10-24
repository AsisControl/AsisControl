package com.asiscontrol.repository;

import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface SolicitudMatriculaRepository extends JpaRepository<SolicitudMatricula, Long> {
    boolean existsBySeccionId(Long id);

    boolean existsBySeccionIdAndActivoTrueAndEstadoIn(
            Long id, Collection<EstadoSolicitudMatricula> estados);

    Optional<SolicitudMatricula> findByIdAndActivoTrue(Long id);

    Page<SolicitudMatricula> findByActivoTrue(Pageable pageable);

    @Query(
            """
SELECT s FROM SolicitudMatricula s
WHERE s.activo = true
  AND (:personaAlumnoId IS NULL OR s.alumno.persona.id = :personaAlumnoId)
  AND (:personaApoderadoId IS NULL OR EXISTS (
        SELECT v.id FROM AlumnoApoderado v
        WHERE v.activo = true
          AND v.alumno.id = s.alumno.id
          AND v.apoderado.activo = true AND v.apoderado.persona.activo = true AND v.alumno.activo = true AND v.alumno.persona.activo = true
          AND v.apoderado.persona.id = :personaApoderadoId
      ))
""")
    Page<SolicitudMatricula> buscarVisibles(
            @Param("personaAlumnoId") Long personaAlumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SolicitudMatricula s WHERE s.id = :id AND s.activo = true")
    Optional<SolicitudMatricula> buscarActivaParaActualizar(@Param("id") Long id);

    @Query(
            """
            SELECT COUNT(s) FROM SolicitudMatricula s
            WHERE s.activo = true
              AND s.alumno.id = :alumnoId
              AND s.anioAcademico = :anioAcademico
              AND s.estado IN :estados
            """)
    long contarSolicitudesEnEstados(
            @Param("alumnoId") Long alumnoId,
            @Param("anioAcademico") int anioAcademico,
            @Param("estados") Collection<EstadoSolicitudMatricula> estados);
}
