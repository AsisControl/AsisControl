package com.asiscontrol.entity;

import com.asiscontrol.entity.planificacion.AnioAcademico;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

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

    @Column(name = "plan_definido", nullable = false)
    private boolean planDefinido;

    @ElementCollection
    @CollectionTable(name = "PlanEstudiosCurso", joinColumns = @JoinColumn(name = "id_grado_academico"))
    @MapKeyJoinColumn(name = "id_curso", nullable = false)
    private Map<Curso, CursoEnPlan> cursosPlan = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "PlanEstudiosCursoCompetencia", joinColumns = @JoinColumn(name = "id_grado_academico"))
    private Set<CompetenciaEnPlan> competenciasPlan = new HashSet<>();

    public void incluirCurso(Curso curso) {
        cursosPlan.putIfAbsent(curso, CursoEnPlan.desdeCatalogo(curso));
    }

    public void definirCompetencias(Long cursoId, Collection<Competencia> competencias) {
        competenciasPlan.removeIf(item -> item.cursoId().equals(cursoId));
        competencias.forEach(competencia -> competenciasPlan.add(new CompetenciaEnPlan(cursoId, competencia)));
    }
}
