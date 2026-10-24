package com.asiscontrol.repository;

import com.asiscontrol.entity.AlumnoApoderado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlumnoApoderadoRepository extends JpaRepository<AlumnoApoderado, Long> {

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
}
