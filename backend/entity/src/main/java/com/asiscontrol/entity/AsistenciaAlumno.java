package com.asiscontrol.entity;


import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "AsistenciaAlumno", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asistencia_alumno_sesion", columnNames = {
                "id_alumno", "id_sesion_clase"
        })
})
public class AsistenciaAlumno extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asistencia_alumno")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asignacion_curso", nullable = false)
    private AsignacionCurso asignacionCurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente_registrador")
    private Docente docenteRegistrador;

    // asistencia alumnos
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_sesion_clase",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_asistencia_alumno_sesion"))
    private SesionClase sesionClase;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    @Column(name = "hora_registro", nullable = false)
    private LocalTime horaRegistro;

    @Column(name = "comentario", length = 1000)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_asistencia", nullable = false, length = 35)
    private EstadoAsistencia estadoAsistencia;
}
