package com.asiscontrol.repository;

import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.enums.EstadoVerificacionClase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AsistenciaDocenteRepository extends JpaRepository<AsistenciaDocente, Long> {

    Optional<AsistenciaDocente> findByIdAndActivoTrue(Long id);

    Page<AsistenciaDocente> findAllByActivoTrue(Pageable pageable);

    Optional<AsistenciaDocente> findByDocenteIdAndFechaJornadaAndActivoTrue(Long docenteId, LocalDate fechaJornada);

    boolean existsByDocenteIdAndFechaJornadaAndActivoTrue(Long docenteId, LocalDate fechaJornada);

    Page<AsistenciaDocente> findAllByDocenteIdAndActivoTrue(Long docenteId, Pageable pageable);

    @Query("""
            SELECT a FROM AsistenciaDocente a
            WHERE a.activo = true
              AND (:docenteId IS NULL OR a.docente.id = :docenteId)
              AND (:personaDocenteId IS NULL OR a.docente.persona.id = :personaDocenteId)
              AND (:verificacion IS NULL OR a.verificacion = :verificacion)
              AND (:fechaJornada IS NULL OR a.fechaJornada = :fechaJornada)
            """)
    Page<AsistenciaDocente> buscarVisibles(
            @Param("docenteId") Long docenteId,
            @Param("personaDocenteId") Long personaDocenteId,
            @Param("verificacion") EstadoVerificacionClase verificacion,
            @Param("fechaJornada") LocalDate fechaJornada,
            Pageable pageable
    );

    List<AsistenciaDocente> findAllBySesionClaseIdAndActivoTrue(Long sesionClaseId);

    long countByFechaJornadaAndActivoTrue(LocalDate fechaJornada);

    long countByFechaJornadaAndVerificacionAndActivoTrue(LocalDate fechaJornada, EstadoVerificacionClase verificacion);
}
