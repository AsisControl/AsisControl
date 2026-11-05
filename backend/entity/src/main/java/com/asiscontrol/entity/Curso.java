package com.asiscontrol.entity;


import com.asiscontrol.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Curso", uniqueConstraints = {
        @UniqueConstraint(name = "uk_curso_codigo", columnNames = "codigo")
})
public class Curso extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_curso")
    private Long id;

    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "horas_semanales", nullable = false)
    private int horasSemanales;

    /**
     * Contexto academico: el grado de un anio (1.er grado de 2027). Los cursos creados antes de
     * V6 no lo tienen; los nuevos lo exigen.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grado_academico")
    private GradoAcademico gradoAcademico;
}
