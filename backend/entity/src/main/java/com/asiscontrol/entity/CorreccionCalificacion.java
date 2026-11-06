package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.NivelLogro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

import java.time.Instant;

/** US-50: version anterior de una calificacion publicada; el registro es inmutable. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CorreccionCalificacion", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_correccion_calificacion_version",
                columnNames = {"id_calificacion", "numero_version"}
        )
})
public class CorreccionCalificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correccion_calificacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_calificacion", nullable = false)
    private Calificacion calificacion;

    @Column(name = "numero_version", nullable = false)
    private int numeroVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_anterior", nullable = false, length = 2)
    private NivelLogro nivelAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_nuevo", nullable = false, length = 2)
    private NivelLogro nivelNuevo;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "autor", nullable = false, length = 250)
    private String autor;

    @Column(name = "fecha_correccion", nullable = false)
    private Instant fechaCorreccion;
}
