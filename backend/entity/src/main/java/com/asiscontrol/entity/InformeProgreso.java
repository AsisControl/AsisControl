package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.EstadoInformeProgreso;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Una version de la libreta de un alumno, curso/seccion y periodo academico. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "InformeProgreso", uniqueConstraints = {
        @UniqueConstraint(name = "uk_informe_alumno_curso_periodo_numero",
                columnNames = {"id_alumno", "id_asignacion_curso", "id_periodo_academico", "numero_version"})
}, indexes = {
        @Index(name = "idx_informe_curso_periodo_estado",
                columnList = "id_asignacion_curso,id_periodo_academico,estado")
})
public class InformeProgreso extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_informe_progreso")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;

    // La asignacion identifica tanto el curso como la seccion.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asignacion_curso", nullable = false)
    private AsignacionCurso asignacionCurso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_periodo_academico", nullable = false)
    private PeriodoAcademico periodoAcademico;

    // Version funcional del informe; AuditableEntity.version es el bloqueo optimista JPA.
    @Column(name = "numero_version", nullable = false)
    private int numeroVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoInformeProgreso estado = EstadoInformeProgreso.BORRADOR;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_docente", nullable = false)
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_regla_calificacion")
    private ReglaCalificacion reglaCalificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_escala")
    private EscalaInstitucional escalaInstitucional;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARCHAR)
    @Column(name = "consolidado_regla")
    private String consolidadoRegla;

    @jakarta.persistence.Lob
    @Column(name = "conclusiones_descriptivas")
    private String conclusionesDescriptivas;

    @Column(name = "revisado", nullable = false)
    private boolean revisado;

    @Column(name = "fecha_publicacion")
    private Instant fechaPublicacion;
}
