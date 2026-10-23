package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Historial de rotación. Nunca almacena el secreto que recibe el navegador. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TokenRenovacion", uniqueConstraints =
        @UniqueConstraint(name = "uk_renovacion_hash", columnNames = "token_hash"))
public class TokenRenovacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token_renovacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sesion_acceso", nullable = false)
    private SesionAcceso sesion;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_uso")
    private Instant fechaUso;
}
