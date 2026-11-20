package com.asiscontrol.repository;

import com.asiscontrol.entity.GradoAcademico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface GradoAcademicoRepository extends JpaRepository<GradoAcademico, Long> {
    List<GradoAcademico> findByAnioAcademicoAnioOrderByNumero(int anio);

    Optional<GradoAcademico> findByAnioAcademicoAnioAndNumero(int anio, int numero);

    boolean existsByCursosPlanId(Long cursoId);
}
