package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Instantánea del saldo seleccionado; las obligaciones originales mantienen su identidad. */
@Entity
@Table(name = "OrdenPagoDetalle", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orden_detalle_compromiso", columnNames = {"id_orden_pago", "id_compromiso_pago"}),
        @UniqueConstraint(name = "uk_orden_detalle_transaccion", columnNames = "id_transaccion_pago")
})
@Getter
@Setter
@NoArgsConstructor
public class OrdenPagoDetalle extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_pago_detalle")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden_pago", nullable = false)
    private OrdenPago orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compromiso_pago", nullable = false)
    private CompromisoPago compromiso;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_transaccion_pago", nullable = false)
    private TransaccionPago transaccion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "codigo_compromiso", nullable = false, length = 50)
    private String codigoCompromiso;

    @Column(nullable = false, length = 300)
    private String alumno;

    @Column(nullable = false, length = 160)
    private String concepto;

    @Column(name = "numero_cuota")
    private Integer numeroCuota;
}
