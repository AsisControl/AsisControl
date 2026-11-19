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
            """)
    Page<Apoderado> searchActive(@Param("search") String search, Pageable pageable);
}
