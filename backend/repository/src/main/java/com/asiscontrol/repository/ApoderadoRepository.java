package com.asiscontrol.repository;

import com.asiscontrol.entity.Apoderado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApoderadoRepository extends JpaRepository<Apoderado, Long> {

    Optional<Apoderado> findByIdAndActivoTrue(Long id);

    boolean existsByPersonaId(Long personaId);

    boolean existsByCodigoApoderadoIgnoreCase(String codigo);

    Optional<Apoderado> findByPersonaIdAndActivoTrue(Long personaId);

    @Query("""
            select a from Apoderado a
            join a.persona p
            where a.activo = true and p.activo = true
              and (:search is null or lower(p.nombres) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoPaterno) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoMaterno) like lower(concat('%', :search, '%'))
                   or lower(p.numeroDocumento) like lower(concat('%', :search, '%')))
              and (:conEstudiantes is null or :conEstudiantes = case when exists (
                    select v.id from AlumnoApoderado v where v.apoderado = a and v.activo = true
                      and v.alumno.activo = true and v.alumno.persona.activo = true)
                    then true else false end)
            """)
    Page<Apoderado> searchActive(@Param("search") String search,
            @Param("conEstudiantes") Boolean conEstudiantes, Pageable pageable);
}
