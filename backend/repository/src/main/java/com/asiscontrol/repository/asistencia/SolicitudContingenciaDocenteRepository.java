package com.asiscontrol.repository.asistencia;

import com.asiscontrol.entity.asistencia.SolicitudContingenciaDocente;
import com.asiscontrol.entity.asistencia.SolicitudContingenciaDocente.Estado;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface SolicitudContingenciaDocenteRepository extends JpaRepository<SolicitudContingenciaDocente, Long> {
    boolean existsByDocenteIdAndFechaJornadaAndEstado(Long docenteId, LocalDate jornada, Estado estado);

    @Query("""
        select s from SolicitudContingenciaDocente s
        where s.activo = true and (:docenteId is null or s.docente.id = :docenteId)
            and (:estado is null or s.estado = :estado)
        """)
    Page<SolicitudContingenciaDocente> listar(Long docenteId, Estado estado, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudContingenciaDocente s where s.id = :id and s.activo = true")
    Optional<SolicitudContingenciaDocente> bloquear(Long id);
}
