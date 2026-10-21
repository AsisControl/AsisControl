package com.asiscontrol.entity.planificacion;

import com.asiscontrol.entity.AuditableEntity;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "PeriodoAcademico",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_periodo_orden",
                    columnNames = {"id_anio", "orden"}),
            @UniqueConstraint(name = "uk_periodo_activo", columnNames = "clave_activa")
        })
public class PeriodoAcademico extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_periodo_academico")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_anio", nullable = false)
    private AnioAcademico anioAcademico;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private int orden;

    @Column(name = "fecha_inicio", nullable = false)
    private java.time.LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private java.time.LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.asiscontrol.entity.enums.EstadoPeriodoAcademico estado =
            com.asiscontrol.entity.enums.EstadoPeriodoAcademico.PLANIFICADO;

    @Column(name = "clave_activa")
    private Integer claveActiva;
}
