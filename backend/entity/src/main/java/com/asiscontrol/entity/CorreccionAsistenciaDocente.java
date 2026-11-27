// correccion marcacion docente
package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.EstadoAsistencia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Corrección de una marcación docente hecha por el administrador: conserva el valor anterior. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CorreccionAsistenciaDocente")
public class CorreccionAsistenciaDocente extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correccion_asistencia_docente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asistencia_docente", nullable = false)
    private AsistenciaDocente asistenciaDocente;

    @Column(name = "hora_anterior", nullable = false)
    private LocalDateTime horaAnterior;

    @Column(name = "hora_nueva", nullable = false)
    private LocalDateTime horaNueva;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 35)
    private EstadoAsistencia estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 35)
    private EstadoAsistencia estadoNuevo;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "actor_identificador", nullable = false, length = 100)
    private String actorIdentificador;

    @Column(name = "actor_nombre", length = 300)
    private String actorNombre;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;
}
