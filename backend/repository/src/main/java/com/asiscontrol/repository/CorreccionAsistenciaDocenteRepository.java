// correccion marcacion docente
package com.asiscontrol.repository;

import com.asiscontrol.entity.CorreccionAsistenciaDocente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorreccionAsistenciaDocenteRepository extends JpaRepository<CorreccionAsistenciaDocente, Long> {

    List<CorreccionAsistenciaDocente> findAllByAsistenciaDocenteIdOrderByFechaHoraDescIdDesc(Long asistenciaDocenteId);
}
