package com.asiscontrol.repository;

import com.asiscontrol.entity.SesionAcceso;
import com.asiscontrol.entity.enums.EstadoSesion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SesionAccesoRepository extends JpaRepository<SesionAcceso, Long> {

    @Modifying
    @Query("""
            update SesionAcceso s
            set s.estado = com.asiscontrol.entity.enums.EstadoSesion.REVOCADA,
                s.fechaRevocacion = :fecha
            where s.usuario.rol.id = :rolId
              and s.estado = com.asiscontrol.entity.enums.EstadoSesion.VIGENTE
            """)
    int revocarPorRol(@Param("rolId") Long rolId, @Param("fecha") Instant fecha);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionAcceso s where s.id = :id")
    Optional<SesionAcceso> bloquear(@Param("id") Long id);

    Optional<SesionAcceso> findByTokenHashAndEstado(String tokenHash, EstadoSesion estado);

    @Modifying
    @Query(
            """
update SesionAcceso s set s.ultimaActividad=:ahora
where s.tokenHash=:hash and s.estado=com.asiscontrol.entity.enums.EstadoSesion.VIGENTE
and s.fechaExpiracion>:ahora and s.ultimaActividad>:limite
and s.usuario.activo=true and s.usuario.estado=com.asiscontrol.entity.enums.EstadoAcceso.ACTIVO
and s.usuario.persona.activo=true and s.usuario.rol.activo=true
and s.usuario.rol.nombre in ('ADMINISTRADOR', 'DOCENTE', 'ALUMNO', 'APODERADO')
      and (s.usuario.requiereCambioContrasena=false or s.usuario.contrasenaTemporalExpira>:ahora)
""")
    int registrarActividad(
            @Param("hash") String hash,
            @Param("ahora") Instant ahora,
            @Param("limite") Instant limite);

    @Modifying
    @Query(
            """
update SesionAcceso s
   set s.estado = :estado, s.fechaRevocacion = :fecha
 where s.usuario.id = :usuarioId and s.estado = com.asiscontrol.entity.enums.EstadoSesion.VIGENTE
""")
    int revokeAllByUsuarioId(
            @Param("usuarioId") Long usuarioId,
            @Param("estado") EstadoSesion estado,
            @Param("fecha") Instant fecha);
}
