package com.asiscontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** Competencia seleccionada para un curso del plan de un grado y año. */
@Embeddable
public record CompetenciaEnPlan(
        @Column(name = "id_curso", nullable = false) Long cursoId,
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "id_competencia", nullable = false) Competencia competencia) {
}
