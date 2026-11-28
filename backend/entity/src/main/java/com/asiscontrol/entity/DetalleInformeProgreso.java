package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.NivelLogro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Copia del resultado por evaluacion y competencia; no se recalcula desde la nota vigente. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DetalleInformeProgreso", uniqueConstraints = {
        @UniqueConstraint(name = "uk_detalle_informe_evaluacion_competencia",
                columnNames = {"id_informe_progreso", "id_evaluacion", "id_competencia"})
})
public class DetalleInformeProgreso extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_informe_progreso")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_informe_progreso", nullable = false)
    private InformeProgreso informe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_competencia", nullable = false)
    private Competencia competencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evaluacion", nullable = false)
    private Evaluacion evaluacion;

    // Referencia de origen, opcional mientras el informe sea un borrador incompleto.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_calificacion")
    private Calificacion calificacion;

    @Column(name = "nombre_competencia", nullable = false, length = 180)
    private String nombreCompetencia;

    @Column(name = "titulo_evaluacion", nullable = false, length = 160)
    private String tituloEvaluacion;

    @Column(name = "fecha_evaluacion", nullable = false)
    private LocalDate fechaEvaluacion;

    @Column(name = "ponderacion_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal ponderacionPorcentaje;

    @Column(name = "peso_resultado", length = 40)
    private String pesoResultado;

    @Column(name = "estado_evaluacion", length = 20)
    private String estadoEvaluacion;

    @Column(name = "nota_numerica", precision = 12, scale = 10)
    private BigDecimal notaNumerica;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_logro", length = 2)
    private NivelLogro nivelLogro;

    @Column(name = "observacion", length = 1000)
    private String observacion;
}
