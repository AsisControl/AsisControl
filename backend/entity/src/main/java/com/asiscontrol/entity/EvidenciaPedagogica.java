package com.asiscontrol.entity;

import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "EvidenciaPedagogica")
public class EvidenciaPedagogica extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evidencia_pedagogica")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asignacion_curso", nullable = false)
    private AsignacionCurso asignacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_competencia", nullable = false)
    private Competencia competencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_periodo_academico", nullable = false)
    private PeriodoAcademico periodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_persona_autor", nullable = false)
    private Persona autor;

    @Column(length = 1000)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_archivo")
    private ArchivoAdjunto archivo;

    @ManyToMany
    @JoinTable(name = "EvidenciaPedagogicaEvaluacion",
            joinColumns = @JoinColumn(name = "id_evidencia_pedagogica"),
            inverseJoinColumns = @JoinColumn(name = "id_evaluacion"))
    private Set<Evaluacion> evaluaciones = new LinkedHashSet<>();
}
