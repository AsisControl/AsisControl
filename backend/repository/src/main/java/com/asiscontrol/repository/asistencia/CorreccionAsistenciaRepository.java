// asistencia alumnos
package com.asiscontrol.repository.asistencia;

import com.asiscontrol.entity.asistencia.CorreccionAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorreccionAsistenciaRepository extends JpaRepository<CorreccionAsistencia, Long> {

    List<CorreccionAsistencia> findAllBySesionClaseIdOrderByFechaHoraDesc(Long sesionClaseId);
}
