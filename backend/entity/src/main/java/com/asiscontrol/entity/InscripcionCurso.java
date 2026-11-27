// inscripcion cursos
package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Curso del plan de estudios en el que quedó inscrita una matrícula. Se crea al activar la matrícula;
 * si se retira o anula, queda inactiva (nunca se borra). El docente y el horario no se copian: salen
 * de la asignación de la sección.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "InscripcionCurso", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inscripcion_matricula_curso", columnNames = {"id_matricula", "id_curso"})
})
public class InscripcionCurso extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inscripcion_curso")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curso", nullable = false)
    private Curso curso;

    public InscripcionCurso(Matricula matricula, Curso curso) {
        this.matricula = matricula;
        this.curso = curso;
    }
}
