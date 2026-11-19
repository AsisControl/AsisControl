package com.asiscontrol.entity;


import com.asiscontrol.entity.Administrador;
import com.asiscontrol.entity.Apoderado;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.MedioPago;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TransaccionPago", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transaccion_numero_operacion", columnNames = "numero_operacion")
})
public class TransaccionPago extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transaccion_pago")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compromiso_pago", nullable = false)
    private CompromisoPago compromisoPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_apoderado")
    private Apoderado apoderado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_administrador")
    private Administrador administrador;

    @Column(name = "numero_operacion", nullable = false, length = 100)
    private String numeroOperacion;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "medio_pago", nullable = false, length = 40)
    private MedioPago medioPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_transaccion", nullable = false, length = 20)
    private EstadoTransaccionPago estado = EstadoTransaccionPago.APROBADA;

    @Column(name = "fecha_transaccion", nullable = false)
    private Instant fechaTransaccion;

    @Column(name = "observacion", length = 600)
    private String observacion;
}
