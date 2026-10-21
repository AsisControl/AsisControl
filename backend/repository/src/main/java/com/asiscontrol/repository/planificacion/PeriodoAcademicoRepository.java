package com.asiscontrol.repository.planificacion;

import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.entity.enums.EstadoPeriodoAcademico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PeriodoAcademicoRepository extends JpaRepository<PeriodoAcademico, Long> {
    List<PeriodoAcademico> findByAnioAcademicoAnioOrderByOrden(int anio);

    boolean existsByEstado(EstadoPeriodoAcademico estado);
}
