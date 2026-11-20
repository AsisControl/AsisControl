package com.asiscontrol.entity;


import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoVerificacionClase;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "AsistenciaDocente", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asistencia_docente_jornada", columnNames = {"id_docente", "fecha_jornada"})
})
public class AsistenciaDocente extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asistencia_docente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_docente", nullable = false)
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_codigo_qr")
    private CodigoQRAsistencia codigoQR;

    /** Sesión de clase que cruza esta marcación; vacía si el docente no tenía clase programada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sesion_clase")
    private SesionClase sesionClase;

    @Column(name = "fecha_jornada", nullable = false)
    private LocalDate fechaJornada;

    @Column(name = "fecha_hora_marcada", nullable = false)
    private LocalDateTime fechaHoraMarcada;

    /** Copia de la regla aplicada; null en marcaciones anteriores y contingencias manuales. */
    @Column(name = "ventana_apertura")
    private LocalDateTime ventanaApertura;

    @Column(name = "limite_puntualidad")
    private LocalDateTime limitePuntualidad;

    @Column(name = "ventana_cierre")
    private LocalDateTime ventanaCierre;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_asistencia", nullable = false, length = 35)
    private EstadoAsistencia estado;

    @Column(name = "es_contingencia", nullable = false)
    private boolean contingencia;

    @Column(name = "motivo_contingencia", length = 1000)
    private String motivoContingencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_verificacion", nullable = false, length = 20)
    private EstadoVerificacionClase verificacion = EstadoVerificacionClase.CONFIRMADA;
}
