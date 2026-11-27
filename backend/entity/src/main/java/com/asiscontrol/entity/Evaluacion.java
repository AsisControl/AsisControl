package com.asiscontrol.entity;


import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Evaluacion")
public class Evaluacion extends AuditableEntity {
    @Column(name = "modelo_cualitativo", nullable = false)
    private boolean modeloCualitativo;

    @Column(name = "entrega_por_configurar", nullable = false)
    private boolean entregaPorConfigurar;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asignacion_curso", nullable = false)
    private AsignacionCurso asignacionCurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_escala")
    private EscalaInstitucional escalaInstitucional;

    @Column(name = "cuenta_resultado", nullable = false)
    private boolean cuentaResultado = true;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evaluacion", nullable = false, length = 40)
    private TipoEvaluacion tipoEvaluacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodo_evaluacion", nullable = false, length = 40)
    private PeriodoEvaluacion periodoEvaluacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_evaluacion", nullable = false, length = 30)
    private EstadoEvaluacion estado = EstadoEvaluacion.BORRADOR;

    @Column(name = "fecha_evaluacion", nullable = false)
    private LocalDate fechaEvaluacion;

    @Column(name = "ponderacion_porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal ponderacionPorcentaje;

    @Column(name = "fecha_publicacion")
    private Instant fechaPublicacion;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "EvaluacionCompetencia",
            joinColumns = @JoinColumn(name = "id_evaluacion"),
            inverseJoinColumns = @JoinColumn(name = "id_competencia")
    )
    private Set<Competencia> competencias = new LinkedHashSet<>();
}
