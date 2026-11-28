package com.asiscontrol.repository;

import com.asiscontrol.entity.AlumnoApoderado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlumnoApoderadoRepository extends JpaRepository<AlumnoApoderado, Long> {

    @EntityGraph(attributePaths = {"apoderado", "apoderado.persona"})
    List<AlumnoApoderado> findAllByAlumnoIdOrderByActivoDescPrincipalDescIdAsc(Long alumnoId);

    List<AlumnoApoderado> findAllByAlumnoIdAndActivoTrue(Long alumnoId);

    List<AlumnoApoderado> findAllByApoderadoIdAndActivoTrue(Long apoderadoId);

    Optional<AlumnoApoderado> findByAlumnoIdAndApoderadoIdAndActivoTrue(
            Long alumnoId,
            Long apoderadoId
    );

    Optional<AlumnoApoderado> findByAlumnoIdAndApoderadoId(
            Long alumnoId,
            Long apoderadoId
    );

    boolean existsByAlumnoIdAndPrincipalTrueAndActivoTrue(Long alumnoId);

    /** El estudiante tiene al menos un apoderado habilitado. */
    boolean existsByAlumnoIdAndActivoTrueAndApoderadoActivoTrueAndApoderadoPersonaActivoTrue(Long alumnoId);

    @Query("""
            SELECT COUNT(v) > 0 FROM AlumnoApoderado v
            WHERE v.activo = true AND v.alumno.id = :alumnoId AND v.apoderado.persona.id = :personaApoderadoId
              AND v.alumno.activo = true AND v.alumno.persona.activo = true
              AND v.apoderado.activo = true AND v.apoderado.persona.activo = true
            """)
    boolean existeVinculoActivo(@Param("alumnoId") Long alumnoId,
            @Param("personaApoderadoId") Long personaApoderadoId);
}
