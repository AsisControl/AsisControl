package com.asiscontrol.repository;

import com.asiscontrol.entity.RenovacionMatricula;
import com.asiscontrol.entity.enums.EstadoRenovacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RenovacionMatriculaRepository extends JpaRepository<RenovacionMatricula, Long> {
    Optional<RenovacionMatricula> findByIdAndActivoTrue(Long id);

    boolean existsByAlumnoIdAndAnioDestinoAndClaveVigenteAndActivoTrue(Long alumnoId, int anio, int clave);

    boolean existsByMatriculaAnteriorIdAndEstadoAndActivoTrue(Long id, EstadoRenovacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RenovacionMatricula r where r.id = :id and r.activo = true")
    Optional<RenovacionMatricula> bloquear(@Param("id") Long id);

    @Query("""
            select r from RenovacionMatricula r
            where r.activo = true and (:estado is null or r.estado = :estado)
            and (:persona is null or exists (
                select v.id from AlumnoApoderado v where v.alumno.id = r.alumno.id
                and v.activo = true and v.apoderado.activo = true
                and v.apoderado.persona.activo = true and v.alumno.activo = true
                and v.alumno.persona.activo = true and v.apoderado.persona.id = :persona))
            """)
    Page<RenovacionMatricula> buscarVisibles(@Param("persona") Long persona,
                                           @Param("estado") EstadoRenovacion estado, Pageable pagina);
}
