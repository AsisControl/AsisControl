package com.asiscontrol.repository;

import com.asiscontrol.entity.HistorialSeccionMatricula;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialSeccionMatriculaRepository extends JpaRepository<HistorialSeccionMatricula, Long> {

    boolean existsBySeccionAnteriorId(Long seccionId);
}
