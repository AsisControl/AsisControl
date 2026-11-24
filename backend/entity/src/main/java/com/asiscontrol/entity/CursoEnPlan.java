package com.asiscontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Nombre y carga horaria aprobados para el plan de un grado y año. */
@Embeddable
public record CursoEnPlan(
        @Column(name = "nombre_plan", length = 120, nullable = false) String nombre,
        @Column(name = "horas_semanales_plan", nullable = false) int horasSemanales) {

    public static CursoEnPlan desdeCatalogo(Curso curso) {
        return new CursoEnPlan(curso.getNombre(), curso.getHorasSemanales());
    }
}
