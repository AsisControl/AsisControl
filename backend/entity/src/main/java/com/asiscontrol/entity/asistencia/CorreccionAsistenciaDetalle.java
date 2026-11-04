// asistencia alumnos
package com.asiscontrol.entity.asistencia;

import com.asiscontrol.entity.Alumno;
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

/** Cambio de estado de un alumno dentro de una corrección. Es inmutable una vez creado. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "CorreccionAsistenciaDetalle",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_correccion_detalle_alumno",
                columnNames = {"id_correccion_asistencia", "id_alumno"}))
public class CorreccionAsistenciaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correccion_asistencia_detalle")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_correccion_asistencia",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_correccion_detalle_correccion"))
    private CorreccionAsistencia correccionAsistencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_alumno",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_correccion_detalle_alumno"))
    private Alumno alumno;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 35)
    private EstadoAsistencia estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 35)
    private EstadoAsistencia estadoNuevo;
}
