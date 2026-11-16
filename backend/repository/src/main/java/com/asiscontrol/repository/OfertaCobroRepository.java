package com.asiscontrol.repository;

import com.asiscontrol.entity.OfertaCobro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OfertaCobroRepository extends JpaRepository<OfertaCobro, Long> {
    Optional<OfertaCobro> findByIdAndActivoTrue(Long id);
    Optional<OfertaCobro> findByClaveOferta(String clave);
    Page<OfertaCobro> findByActivoTrue(Pageable pageable);

    @Query("""
            select o from OfertaCobro o
            where o.activo=true and (:alumno is null or o.matricula.alumno.id=:alumno)
              and exists (select v.id from AlumnoApoderado v
                          where v.activo=true and v.apoderado.id=:apoderado
                            and v.apoderado.activo=true and v.apoderado.persona.activo=true
                            and v.alumno=o.matricula.alumno
                            and v.alumno.activo=true and v.alumno.persona.activo=true)
            """)
    Page<OfertaCobro> buscarFamilia(@Param("apoderado") Long apoderado,
            @Param("alumno") Long alumno, Pageable pageable);
}
