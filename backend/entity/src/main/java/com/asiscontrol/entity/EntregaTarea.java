package com.asiscontrol.entity;


import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.EstadoTarea;
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
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "EntregaTarea", uniqueConstraints = {
        @UniqueConstraint(name = "uk_entrega_tarea_alumno", columnNames = {"id_tarea", "id_alumno"})
})
public class EntregaTarea extends AuditableEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_logro", length = 2)
    private com.asiscontrol.entity.enums.NivelLogro nivelLogro;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entrega_tarea")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tarea", nullable = false)
    private Tarea tarea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;

    @Column(name = "fecha_hora_entrega", nullable = false)
    private LocalDateTime fechaHoraEntrega;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "EntregaTareaArchivo",
            joinColumns = @JoinColumn(name = "id_entrega_tarea"),
            inverseJoinColumns = @JoinColumn(name = "id_archivo"))
    private Set<ArchivoAdjunto> archivos = new LinkedHashSet<>();

    @Column(name = "nota", precision = 14, scale = 10)
    private java.math.BigDecimal nota;

    @Column(name = "comentario", length = 2000)
    private String comentario;

    @Column(name = "respuesta", length = 2000)
    private String respuesta;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_entrega", nullable = false, length = 25)
    private EstadoTarea estado = EstadoTarea.ENTREGADO;
}
