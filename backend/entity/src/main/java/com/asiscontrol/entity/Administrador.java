package com.asiscontrol.entity;


import com.asiscontrol.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Administrador", uniqueConstraints = {
        @UniqueConstraint(name = "uk_administrador_persona", columnNames = "id_persona"),
        @UniqueConstraint(name = "uk_administrador_codigo", columnNames = "codigo_administrador")
})
public class Administrador extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_administrador")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    /** Código institucional (ver CodigoInstitucionalService); se asigna al registrar el perfil. */
    @Column(name = "codigo_administrador", length = 30)
    private String codigoAdministrador;
}
