// asistencia alumnos
package com.asiscontrol.entity.asistencia;

import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Docente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Corrección de una asistencia TOMADA: el motivo agrupa los cambios de cada alumno. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CorreccionAsistencia")
public class CorreccionAsistencia extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correccion_asistencia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_sesion_clase",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_correccion_asistencia_sesion"))
    private SesionClase sesionClase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_docente",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_correccion_asistencia_docente"))
    private Docente docente;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;
}
