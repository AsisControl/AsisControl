package com.asiscontrol.entity.planificacion;

import com.asiscontrol.entity.AuditableEntity;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "AnioAcademico",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_anio_numero", columnNames = "anio"),
            @UniqueConstraint(name = "uk_anio_activo", columnNames = "clave_activa")
        })
public class AnioAcademico extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_anio_academico")
    private Long id;

    @Column(nullable = false)
    private int anio;

    @Column(name = "fecha_inicio", nullable = false)
    private java.time.LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private java.time.LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.asiscontrol.entity.enums.EstadoAnioAcademico estado =
            com.asiscontrol.entity.enums.EstadoAnioAcademico.BORRADOR;

    @Column(name = "clave_activa")
    private Integer claveActiva;

    @Column(name = "admision_abierta", nullable = false)
    private boolean admisionAbierta;
}
