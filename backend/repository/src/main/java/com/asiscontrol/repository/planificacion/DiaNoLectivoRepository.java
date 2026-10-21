package com.asiscontrol.repository.planificacion;

import com.asiscontrol.entity.planificacion.DiaNoLectivo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaNoLectivoRepository extends JpaRepository<DiaNoLectivo, Long> {
    List<DiaNoLectivo> findByAnioAcademicoAnioOrderByFechaAsc(int anio);

    Optional<DiaNoLectivo> findByAnioAcademicoAnioAndFecha(int anio, LocalDate fecha);

    boolean existsByAnioAcademicoAnioAndFecha(int anio, LocalDate fecha);
}
