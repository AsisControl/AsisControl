package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.EstadoSesion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "SesionAcceso",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_sesion_token_hash", columnNames = "token_hash")
        })
public class SesionAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion_acceso")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_acceso_usuario", nullable = false)
    private AccesoUsuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_activo")
    private Rol rolActivo;

    @Column(name = "version_contexto", nullable = false)
    private long versionContexto;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "ultima_actividad", nullable = false)
    private Instant ultimaActividad;

    @Column(name = "fecha_expiracion", nullable = false)
    private Instant fechaExpiracion;

    @Column(name = "fecha_revocacion")
    private Instant fechaRevocacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_sesion", nullable = false, length = 20)
    private EstadoSesion estado = EstadoSesion.VIGENTE;
}
