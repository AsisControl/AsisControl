package com.asiscontrol.entity;


import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.EstadoCodigoQR;
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

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CodigoQRAsistencia", uniqueConstraints = {
        @UniqueConstraint(name = "uk_codigo_qr_token_hash", columnNames = "token_hash")
})
public class CodigoQRAsistencia extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo_qr")
    private Long id;

    /** Vacío en el QR institucional del punto de marcación. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente")
    private Docente docente;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "fecha_hora_emision", nullable = false)
    private LocalDateTime fechaHoraEmision;

    @Column(name = "fecha_hora_expiracion", nullable = false)
    private LocalDateTime fechaHoraExpiracion;

    @Column(name = "fecha_hora_uso")
    private LocalDateTime fechaHoraUso;

    @Column(name = "fecha_hora_cancelacion")
    private LocalDateTime fechaHoraCancelacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_codigo_qr", nullable = false, length = 20)
    private EstadoCodigoQR estado = EstadoCodigoQR.VIGENTE;
}
