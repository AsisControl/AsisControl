package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.EstadoOrdenPago;
import com.asiscontrol.entity.enums.MetodoPagoSimulado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Intento de pago del apoderado. No contiene datos de tarjeta ni secretos del QR. */
@Entity
@Table(name = "OrdenPago", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orden_pago_codigo", columnNames = "codigo"),
        @UniqueConstraint(name = "uk_orden_pago_idempotencia", columnNames = {"id_apoderado", "clave_idempotencia"}),
        @UniqueConstraint(name = "uk_orden_pago_intento", columnNames = "payment_intent_id")
})
@Getter
@Setter
@NoArgsConstructor
public class OrdenPago extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_pago")
    private Long id;

    @Column(nullable = false, length = 45)
    private String codigo;

    /** Referencia inmutable firmada; independiente del número de orden mostrado. */
    @Column(name = "qr_referencia", length = 45, unique = true)
    private String qrReferencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_apoderado", nullable = false)
    private Apoderado apoderado;

    @Column(name = "clave_idempotencia", nullable = false, length = 36)
    private String claveIdempotencia;

    @Column(name = "seleccion_hash", nullable = false, length = 64)
    private String seleccionHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private MetodoPagoSimulado metodo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private EstadoOrdenPago estado = EstadoOrdenPago.PENDIENTE;

    @Column(name = "monto_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoBase;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mora;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false, length = 3)
    private String moneda = "PEN";

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "pagada_en")
    private Instant pagadaEn;

    @Column(name = "payment_intent_id", length = 80)
    private String paymentIntentId;

    @Column(length = 300)
    private String mensaje;
}
