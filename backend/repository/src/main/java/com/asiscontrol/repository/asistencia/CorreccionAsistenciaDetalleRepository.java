// asistencia alumnos
package com.asiscontrol.repository.asistencia;

import com.asiscontrol.entity.asistencia.CorreccionAsistenciaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CorreccionAsistenciaDetalleRepository extends JpaRepository<CorreccionAsistenciaDetalle, Long> {

    List<CorreccionAsistenciaDetalle> findAllByCorreccionAsistenciaIdIn(Collection<Long> correccionIds);
}
