package com.asiscontrol.repository;

import com.asiscontrol.entity.ReglaCalificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReglaCalificacionRepository extends JpaRepository<ReglaCalificacion, Long> {
    List<ReglaCalificacion> findByCursoIdAndGradoIdOrderByNumeroVersionDesc(Long cursoId, Long gradoId);

    Optional<ReglaCalificacion> findByCursoIdAndGradoIdAndEstado(
            Long cursoId, Long gradoId, ReglaCalificacion.Estado estado);
}
