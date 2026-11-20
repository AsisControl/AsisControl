package com.asiscontrol.repository;

import com.asiscontrol.entity.AccesoUsuarioRol;
import com.asiscontrol.entity.Rol;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccesoUsuarioRolRepository extends JpaRepository<AccesoUsuarioRol, Long> {

    Optional<AccesoUsuarioRol> findByUsuarioIdAndRolId(Long usuarioId, Long rolId);

    List<AccesoUsuarioRol> findAllByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndRolNombreAndActivoTrue(Long usuarioId, String nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccesoUsuarioRol a where a.usuario.id = :usuarioId and a.rol.id = :rolId")
    Optional<AccesoUsuarioRol> bloquearPorUsuarioIdYRolId(
            @Param("usuarioId") Long usuarioId, @Param("rolId") Long rolId);

    @Query("""
            select r from AccesoUsuarioRol a join a.rol r
            where a.usuario.id = :usuarioId and a.activo = true and r.activo = true
              and (
                (r.nombre = 'ADMINISTRADOR' and exists
                  (select p.id from Administrador p
                   where p.persona.id = a.usuario.persona.id and p.activo = true))
                or (r.nombre = 'DOCENTE' and exists
                  (select p.id from Docente p
                   where p.persona.id = a.usuario.persona.id and p.activo = true))
                or (r.nombre = 'ALUMNO' and exists
                  (select p.id from Alumno p
                   where p.persona.id = a.usuario.persona.id and p.activo = true))
                or (r.nombre = 'APODERADO' and exists
                  (select p.id from Apoderado p
                   where p.persona.id = a.usuario.persona.id and p.activo = true))
              )
            order by r.nombre
            """)
    List<Rol> buscarDisponibles(@Param("usuarioId") Long usuarioId);
}
