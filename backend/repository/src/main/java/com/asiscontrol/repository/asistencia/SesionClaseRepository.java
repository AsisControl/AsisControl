// asistencia alumnos
package com.asiscontrol.repository.asistencia;

import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface SesionClaseRepository extends JpaRepository<SesionClase, Long> {

    Optional<SesionClase> findByIdAndActivoTrue(Long id);

    List<SesionClase> findAllByAsignacionCursoIdAndActivoTrueOrderByFechaAscHoraInicioAsc(Long asignacionCursoId);

    /** Incluye las inactivas: la unicidad del encuentro también las abarca y se reactivan. */
    List<SesionClase> findAllByAsignacionCursoIdAndFechaGreaterThanEqual(Long asignacionCursoId, LocalDate fecha);

    Optional<SesionClase> findByAsignacionCursoIdAndFechaAndHoraInicio(
            Long asignacionCursoId, LocalDate fecha, LocalTime horaInicio);

    /** Bloquea la sesión para escritura al finalizar o corregir su asistencia. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionClase s where s.id = :id and s.activo = true")
    Optional<SesionClase> bloquear(@Param("id") Long id);

    /** Borradores de días anteriores que el job devuelve a PENDIENTE. */
    List<SesionClase> findAllByEstadoAndFechaBefore(EstadoSesionClase estado, LocalDate fecha);
}
