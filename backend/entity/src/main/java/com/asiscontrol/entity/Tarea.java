package com.asiscontrol.entity;


import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Tarea")
public class Tarea extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarea")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 3000)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asignacion_curso", nullable = false)
    private AsignacionCurso asignacionCurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evaluacion_academica", unique = true)
    private Evaluacion evaluacionAcademica;

    @Column(name = "fecha_publicacion")
    private LocalDateTime fechaPublicacion;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDateTime fechaLimite;

    @Column(name = "puntaje_maximo", nullable = false)
    private int puntajeMaximo;

    @Column(name = "puntaje_maximo_fijo", nullable = false)
    private boolean puntajeMaximoFijo;

    @Column(name = "max_archivos_entrega", nullable = false)
    private int maxArchivosEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_publicacion", nullable = false, length = 20)
    private EstadoPublicacionTarea estadoPublicacion = EstadoPublicacionTarea.BORRADOR;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "TareaArchivo",
            joinColumns = @JoinColumn(name = "id_tarea"),
            inverseJoinColumns = @JoinColumn(name = "id_archivo"))
    private Set<ArchivoAdjunto> archivos = new LinkedHashSet<>();
}
