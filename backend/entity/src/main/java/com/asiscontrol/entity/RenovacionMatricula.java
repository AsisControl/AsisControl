package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.EstadoRenovacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "RenovacionMatricula", uniqueConstraints = {
    @UniqueConstraint(name = "uk_renovacion_vigente", columnNames = {"id_alumno", "anio_destino", "clave_vigente"}),
    @UniqueConstraint(name = "uk_renovacion_matricula", columnNames = "id_matricula_resultante")
})
public class RenovacionMatricula extends AuditableEntity {
    public static final int CLAVE_VIGENTE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_renovacion_matricula")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula_anterior", nullable = false)
    private Matricula matriculaAnterior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_seccion_destino", nullable = false)
    private Seccion seccion;

    @Column(name = "anio_destino", nullable = false)
    private int anioDestino;

    @Column(name = "grado_destino", nullable = false)
    private int gradoDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private EstadoRenovacion estado = EstadoRenovacion.EN_REVISION;

    @Column(name = "clave_vigente")
    private Integer claveVigente = CLAVE_VIGENTE;

    @Column(name = "codigo_motivo", length = 40)
    private String codigoMotivo;

    @Column(name = "motivo", length = 700)
    private String motivo;

    @Column(name = "fecha_decision")
    private Instant fechaDecision;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_matricula_resultante")
    private Matricula matricula;

    public void rechazar(String codigo, String detalle, Instant fecha) {
        estado = EstadoRenovacion.RECHAZADA;
        claveVigente = null;
        codigoMotivo = codigo;
        motivo = detalle;
        fechaDecision = fecha;
    }

    public void aprobar(Matricula resultante, Instant fecha) {
        estado = EstadoRenovacion.APROBADA;
        matricula = resultante;
        fechaDecision = fecha;
    }
}
