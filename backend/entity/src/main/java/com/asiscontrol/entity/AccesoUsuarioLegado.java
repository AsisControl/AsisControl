package com.asiscontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "AccesoUsuarioLegado")
public class AccesoUsuarioLegado {

    @Id
    @Column(name = "id_acceso_legado")
    private Long id;

    @Column(name = "identificador_legado", nullable = false, length = 80)
    private String identificador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_acceso_usuario", nullable = false)
    private AccesoUsuario usuario;
}
