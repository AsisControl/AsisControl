package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

/** Sección que una matrícula dejó, para seguir consultando lo publicado hasta el cambio. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "HistorialSeccionMatricula")
public class HistorialSeccionMatricula extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial_seccion_matricula")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_seccion_anterior", nullable = false)
    private Seccion seccionAnterior;

    @Column(name = "fecha_cambio", nullable = false)
    private Instant fechaCambio;

    /** El mismo instante en hora local, comparable con las fechas de las tareas. */
    @Column(name = "fecha_cambio_local", nullable = false)
    private LocalDateTime fechaCambioLocal;

    public HistorialSeccionMatricula(Matricula matricula, Seccion seccionAnterior,
            Instant fechaCambio, LocalDateTime fechaCambioLocal) {
        this.matricula = matricula;
        this.seccionAnterior = seccionAnterior;
        this.fechaCambio = fechaCambio;
        this.fechaCambioLocal = fechaCambioLocal;
    }
}
