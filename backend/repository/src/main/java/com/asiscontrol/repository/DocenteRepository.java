package com.asiscontrol.repository;

import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.enums.Especialidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocenteRepository extends JpaRepository<Docente, Long> {

    Optional<Docente> findByIdAndActivoTrue(Long id);

    long countByActivoTrue();

    boolean existsByPersonaId(Long personaId);

    Optional<Docente> findByCodigoDocenteIgnoreCase(String codigoDocente);

    Optional<Docente> findByPersonaIdAndActivoTrue(Long personaId);

    @Query("""
            select d from Docente d
            join d.persona p
            where d.activo = true and p.activo = true
              and (:especialidad is null or d.especialidad = :especialidad)
              and (:search is null or lower(d.codigoDocente) like lower(concat('%', :search, '%'))
                   or lower(p.nombres) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoPaterno) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoMaterno) like lower(concat('%', :search, '%')))
            """)
    Page<Docente> searchActive(
            @Param("search") String search,
            @Param("especialidad") Especialidad especialidad,
            Pageable pageable
    );
}
