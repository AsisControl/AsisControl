package com.asiscontrol.entity;


import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CompromisoPago", uniqueConstraints = {
        @UniqueConstraint(name = "uk_compromiso_pago_codigo", columnNames = "codigo_compromiso"),
        @UniqueConstraint(name = "uk_compromiso_obligacion", columnNames = "clave_obligacion")
})
public class CompromisoPago extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_compromiso_pago")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula", nullable = false)
    private Matricula matricula;

    @Column(name = "codigo_compromiso", nullable = false, length = 50)
    private String codigoCompromiso;

    @Column(name = "concepto", nullable = false, length = 160)
    private String concepto;

    @Column(name = "concepto_normalizado", nullable = false, length = 160)
    private String conceptoNormalizado;

    @Column(name = "numero_cuota")
    private Integer numeroCuota;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_administrativo", nullable = false, length = 20)
    private EstadoAdministrativoCompromiso estadoAdministrativo = EstadoAdministrativoCompromiso.ACTIVO;

    @Column(name = "clave_obligacion", length = 64)
    private String claveObligacion;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private Instant fechaAnulacion;

    @Column(name = "descripcion", length = 600)
    private String descripcion;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_pagado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda = "PEN";

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_compromiso", nullable = false, length = 20)
    private EstadoCompromisoPago estado = EstadoCompromisoPago.PENDIENTE;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    private void normalizarConcepto() {
        if (concepto != null) {
            conceptoNormalizado = concepto.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        }
    }
}
