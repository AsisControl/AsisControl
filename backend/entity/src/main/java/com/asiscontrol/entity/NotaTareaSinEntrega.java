package com.asiscontrol.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;

@Entity
@Getter
@Table(name = "NotaTareaSinEntrega", uniqueConstraints =
        @UniqueConstraint(name = "uk_nota_tarea_sin_entrega", columnNames = {"id_tarea", "id_alumno"}))
public class NotaTareaSinEntrega {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota_tarea_sin_entrega")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tarea", nullable = false)
    private Tarea tarea;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;
    @Column(precision = 14, scale = 10)
    private BigDecimal nota;
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_logro", length = 2)
    private com.asiscontrol.entity.enums.NivelLogro nivelLogro;
    @Column(length = 1000)
    private String observacion;

    protected NotaTareaSinEntrega() {}

    public NotaTareaSinEntrega(Tarea tarea, Alumno alumno, BigDecimal nota,
                               com.asiscontrol.entity.enums.NivelLogro nivelLogro, String observacion) {
        this.tarea = tarea;
        this.alumno = alumno;
        actualizar(nota, nivelLogro, observacion);
    }

    public void actualizar(BigDecimal nota, com.asiscontrol.entity.enums.NivelLogro nivelLogro, String observacion) {
        this.nota = nota;
        this.nivelLogro = nivelLogro;
        this.observacion = observacion;
    }
}
