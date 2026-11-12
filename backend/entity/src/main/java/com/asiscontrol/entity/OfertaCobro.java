package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.EstadoOfertaCobro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Una propuesta opcional para un estudiante; no constituye deuda antes de aceptarse. */
@Entity
@Table(name = "OfertaCobro", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oferta_cobro_clave", columnNames = "clave_oferta"),
        @UniqueConstraint(name = "uk_oferta_cobro_compromiso", columnNames = "id_compromiso_pago")
})
@Getter
@Setter
@NoArgsConstructor
public class OfertaCobro extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_oferta_cobro")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula", nullable = false)
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_concepto_cobro", nullable = false)
    private ConceptoCobro concepto;

    @Column(name = "concepto_nombre", nullable = false, length = 160)
    private String conceptoNombre;

    @Column(nullable = false, length = 80)
    private String referencia;

    @Column(name = "clave_oferta", nullable = false, length = 64)
    private String claveOferta;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(length = 600)
    private String descripcion;

    @Column(name = "monto_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoBase;

    @Column(name = "descuento_aceptado", precision = 12, scale = 2)
    private BigDecimal descuentoAceptado;

    @Column(name = "monto_aceptado", precision = 12, scale = 2)
    private BigDecimal montoAceptado;

    @Column(name = "beneficio_aceptado", length = 160)
    private String beneficioAceptado;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_limite_aceptacion", nullable = false)
    private LocalDate fechaLimiteAceptacion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoOfertaCobro estado = EstadoOfertaCobro.PROPUESTA;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compromiso_pago")
    private CompromisoPago compromiso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_apoderado_respuesta")
    private Apoderado apoderadoRespuesta;

    @Column(name = "respondida_en")
    private Instant respondidaEn;

    @Column(name = "motivo_respuesta", length = 500)
    private String motivoRespuesta;
}
