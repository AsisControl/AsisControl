package com.asiscontrol.repository.planificacion;

import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnioAcademicoRepository extends JpaRepository<AnioAcademico, Long> {
    Optional<AnioAcademico> findByAnio(int anio);

    boolean existsByEstado(EstadoAnioAcademico estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnioAcademico a where a.anio = :anio")
    Optional<AnioAcademico> bloquear(@Param("anio") int anio);
}
