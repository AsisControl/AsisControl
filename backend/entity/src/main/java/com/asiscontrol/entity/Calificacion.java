package com.asiscontrol.entity;


import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.NivelLogro;
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
@Table(name = "Calificacion", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_calificacion_evaluacion_alumno_competencia",
                columnNames = {"id_evaluacion", "id_alumno", "id_competencia"}
        )
})
public class Calificacion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calificacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evaluacion", nullable = false)
    private Evaluacion evaluacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_competencia", nullable = false)
    private Competencia competencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_logro", nullable = false, length = 2)
    private NivelLogro nivelLogro;

    @Column(name = "nota_numerica", precision = 12, scale = 10)
    private java.math.BigDecimal notaNumerica;

    @Column(name = "observacion", length = 1000)
    private String observacion;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;
}
