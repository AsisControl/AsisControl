package com.asiscontrol.entity.planificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "DiaNoLectivo",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_dia_no_lectivo_anio_fecha",
                        columnNames = {"id_anio", "fecha"}),
        indexes = @Index(name = "idx_dia_no_lectivo_fecha", columnList = "fecha"))
@EntityListeners(AuditingEntityListener.class)
public class DiaNoLectivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dia_no_lectivo")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_anio",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_dia_no_lectivo_anio"))
    private AnioAcademico anioAcademico;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 255)
    private String descripcion;

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_actualizacion")
    private Instant fechaActualizacion;

    @Version
    @Column(nullable = false)
    private long version;
}
