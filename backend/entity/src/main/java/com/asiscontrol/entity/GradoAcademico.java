package com.asiscontrol.entity;

import com.asiscontrol.entity.planificacion.AnioAcademico;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "GradoAcademico",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_grado_anio",
                    columnNames = {"id_anio", "numero"})
        })
public class GradoAcademico extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grado_academico")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_anio", nullable = false)
    private AnioAcademico anioAcademico;

    @Column(nullable = false)
    private int numero;

    @Column(name = "capacidad_default", nullable = false)
    private int capacidadDefault;
}
