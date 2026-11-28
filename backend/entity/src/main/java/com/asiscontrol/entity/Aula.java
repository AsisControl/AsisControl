package com.asiscontrol.entity;


import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.TipoAula;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Aula", uniqueConstraints = {
        @UniqueConstraint(name = "uk_aula_codigo", columnNames = "codigo")
})
public class Aula extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aula")
    private Long id;

    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_aula", nullable = false, length = 40)
    private TipoAula tipoAula;

    @Column(name = "capacidad", nullable = false)
    private int capacidad;

    @Column(name = "ubicacion", nullable = false, length = 180)
    private String ubicacion;

    /** Texto libre opcional; nulo significa sin equipamiento registrado. */
    @Column(name = "equipamiento", length = 500)
    private String equipamiento;
}
