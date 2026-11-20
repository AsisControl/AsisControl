package com.asiscontrol.entity;


import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.TipoNotificacion;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Notificacion")
public class Notificacion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_acceso_usuario", nullable = false)
    private AccesoUsuario destinatario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_destinatario")
    private Rol rolDestinatario;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "mensaje", nullable = false, length = 1000)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_notificacion", nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(name = "url_destino", length = 300)
    private String urlDestino;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    @Column(name = "fecha_lectura")
    private Instant fechaLectura;
}
