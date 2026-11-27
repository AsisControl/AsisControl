package com.asiscontrol.repository;

import com.asiscontrol.entity.Alumno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    Optional<Alumno> findByIdAndActivoTrue(Long id);

    boolean existsByPersonaId(Long personaId);

    Optional<Alumno> findByCodigoAlumnoIgnoreCase(String codigoAlumno);

    Optional<Alumno> findByPersonaIdAndActivoTrue(Long personaId);

    @Query("""
            select a from Alumno a
            join a.persona p
            where a.activo = true and p.activo = true
              and (:search is null or lower(a.codigoAlumno) like lower(concat('%', :search, '%'))
                   or lower(p.nombres) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoPaterno) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoMaterno) like lower(concat('%', :search, '%'))
                   or lower(p.numeroDocumento) like lower(concat('%', :search, '%')))
              and (:conMatricula is null or :conMatricula = case when exists (
                    select m.id from Matricula m where m.alumno = a and m.activo = true
                      and m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA)
                    then true else false end)
              and (:grado is null or exists (
                    select g.id from Matricula g where g.alumno = a and g.activo = true
                      and g.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                      and g.seccion.grado = :grado))
              and (:conApoderado is null or :conApoderado = case when exists (
                    select v.id from AlumnoApoderado v where v.alumno = a and v.activo = true
                      and v.apoderado.activo = true and v.apoderado.persona.activo = true)
                    then true else false end)
            """)
    Page<Alumno> searchActive(@Param("search") String search,
            @Param("conMatricula") Boolean conMatricula, @Param("grado") Integer grado,
            @Param("conApoderado") Boolean conApoderado, Pageable pageable);
}
