package com.asiscontrol.entity;


import com.asiscontrol.entity.Docente;
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
import lombok.AccessLevel;
import org.hibernate.annotations.Formula;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "AsignacionCurso", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_asignacion_curso_seccion",
                columnNames = {"id_curso", "id_seccion"}
        )
})
public class AsignacionCurso extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion_curso")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curso", nullable = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_seccion", nullable = false)
    private Seccion seccion;

    @Setter(AccessLevel.NONE)
    @Formula("""
            (select plan.nombre_plan
             from Seccion s
             join AnioAcademico anio on anio.anio = s.anio_academico
             join GradoAcademico grado on grado.id_anio = anio.id_anio_academico and grado.numero = s.grado
             join PlanEstudiosCurso plan on plan.id_grado_academico = grado.id_grado_academico
             where s.id_seccion = id_seccion and plan.id_curso = id_curso)
            """)
    private String nombreCursoPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_docente", nullable = false)
    private Docente docente;

    public String nombreCurso() {
        return nombreCursoPlan == null ? curso.getNombre() : nombreCursoPlan;
    }
}
