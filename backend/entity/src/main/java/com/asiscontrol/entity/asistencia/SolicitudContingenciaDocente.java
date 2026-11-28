package com.asiscontrol.entity.asistencia;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AsistenciaDocente;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Docente;
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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@Table(name = "SolicitudContingenciaDocente",
        uniqueConstraints = @UniqueConstraint(name = "uk_contingencia_pendiente",
                columnNames = {"id_docente", "fecha_jornada", "clave_pendiente"}))
public class SolicitudContingenciaDocente extends AuditableEntity {
    public enum Estado { PENDIENTE, APROBADA, RECHAZADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_contingencia_docente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_docente", nullable = false)
    private Docente docente;
    @Column(name = "fecha_jornada", nullable = false)
    private LocalDate fechaJornada;
    @Column(name = "hora_reportada", nullable = false)
    private LocalTime horaReportada;
    @Column(nullable = false, length = 1000)
    private String motivo;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private Estado estado = Estado.PENDIENTE;
    @Column(name = "clave_pendiente")
    private Integer clavePendiente = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_acceso_usuario_resolutor")
    private AccesoUsuario responsable;
    @Column(name = "fecha_resolucion")
    private Instant fechaResolucion;
    @Column(name = "motivo_rechazo", length = 1000)
    private String motivoRechazo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_asistencia_docente")
    private AsistenciaDocente marcacion;

    public void resolver(AccesoUsuario actor, Instant fecha, AsistenciaDocente registro, String rechazo) {
        estado = registro == null ? Estado.RECHAZADA : Estado.APROBADA;
        clavePendiente = null;
        responsable = actor;
        fechaResolucion = fecha;
        marcacion = registro;
        motivoRechazo = rechazo;
    }
}
