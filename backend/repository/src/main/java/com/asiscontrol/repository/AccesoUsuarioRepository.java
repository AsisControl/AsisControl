package com.asiscontrol.repository;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.enums.EstadoAcceso;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccesoUsuarioRepository extends JpaRepository<AccesoUsuario, Long> {

    Optional<AccesoUsuario> findByIdAndActivoTrue(Long id);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AccesoUsuario u where u.id=:id")
    Optional<AccesoUsuario> bloquear(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select u from AccesoUsuario u where u.activo=true and"
                + " (u.codigo=:identifier or lower(u.username)=lower(:identifier)"
                + " or lower(u.correo)=lower(:identifier))")
    Optional<AccesoUsuario> bloquearPorIdentificador(@Param("identifier") String identifier);

    Optional<AccesoUsuario> findByPersonaId(Long personaId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AccesoUsuario u where u.persona.id = :personaId")
    Optional<AccesoUsuario> bloquearPorPersonaId(@Param("personaId") Long personaId);

    java.util.List<AccesoUsuario> findAllByPersonaIdAndActivoTrue(Long personaId);

    @Query(
            """
            select u from AccesoUsuario u
            where u.activo = true and (u.codigo = :identifier
                or lower(u.username) = lower(:identifier)
                or lower(u.correo) = lower(:identifier))
            """)
    Optional<AccesoUsuario> findActiveByIdentifier(@Param("identifier") String identifier);

    @Query("""
            select l.usuario.persona from AccesoUsuarioLegado l
            where lower(l.identificador) = lower(:identifier)
            """)
    Optional<com.asiscontrol.entity.Persona> buscarPersonaPorIdentificadorLegado(
            @Param("identifier") String identifier);

    Optional<AccesoUsuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByCodigo(String codigo);

    Optional<AccesoUsuario> findByCodigo(String codigo);

    /** Cuentas migradas cuyo usuario todavía es su código y no un nombre.apellido generado. */
    @Query("select u from AccesoUsuario u where u.username = u.codigo order by u.id")
    java.util.List<AccesoUsuario> pendientesDeUsername();

    boolean existsByCorreoIgnoreCase(String correo);

    @Query(
            """
            select u from AccesoUsuario u
            join u.persona p
            where (:estado is null or u.estado = :estado)
              and (:roleId is null or exists (
                   select a.id from AccesoUsuarioRol a
                   where a.usuario.id = u.id and a.rol.id = :roleId and a.activo = true))
              and (:search is null or lower(u.username) like lower(concat('%', :search, '%'))
                   or u.codigo like concat('%', :search, '%')
                   or lower(u.correo) like lower(concat('%', :search, '%'))
                   or lower(p.nombres) like lower(concat('%', :search, '%'))
                   or lower(p.apellidoPaterno) like lower(concat('%', :search, '%')))
            """)
    Page<AccesoUsuario> searchActive(
            @Param("search") String search,
            @Param("estado") EstadoAcceso estado,
            @Param("roleId") Long roleId,
            Pageable pageable);
}
