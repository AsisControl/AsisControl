package com.asiscontrol.repository;

import com.asiscontrol.entity.CorreccionCalificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CorreccionCalificacionRepository extends JpaRepository<CorreccionCalificacion, Long> {

    List<CorreccionCalificacion> findAllByCalificacionIdOrderByNumeroVersionAsc(Long idCalificacion);

    int countByCalificacionId(Long idCalificacion);

    @Query("""
            SELECT DISTINCT c.calificacion.id FROM CorreccionCalificacion c
            WHERE c.calificacion.id IN :idsCalificacion
            """)
    List<Long> buscarCalificacionesCorregidas(@Param("idsCalificacion") Collection<Long> idsCalificacion);
}
