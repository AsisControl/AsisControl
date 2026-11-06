// asistencia alumnos
package com.asiscontrol.entity.asistencia;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Horario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Encuentro de clase programado de una asignación en una fecha. Las horas son una
 * copia del horario al generarse la sesión, para conservar cómo estaba programada.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "SesionClase",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sesion_clase_encuentro",
                columnNames = {"id_asignacion_curso", "fecha", "hora_inicio"}),
        indexes = @Index(name = "idx_sesion_clase_estado_fecha", columnList = "estado, fecha"))
public class SesionClase extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion_clase")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_asignacion_curso",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sesion_clase_asignacion"))
    private AsignacionCurso asignacionCurso;

    /** Primer bloque del horario que forma el encuentro. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_horario",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sesion_clase_horario"))
    private Horario horario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSesionClase estado = EstadoSesionClase.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_docente_registrador",
            foreignKey = @ForeignKey(name = "fk_sesion_clase_docente"))
    private Docente docenteRegistrador;

    @Column(name = "fecha_hora_finalizacion")
    private LocalDateTime fechaHoraFinalizacion;
}
