package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.PeriodicidadCobro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Getter @Setter @Entity
@Table(name = "ConceptoCobro", uniqueConstraints = @UniqueConstraint(name="uk_concepto_cobro_codigo", columnNames="codigo"))
public class ConceptoCobro extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_concepto_cobro") private Long id;
    @Column(nullable=false,length=40) private String codigo;
    @Column(nullable=false,length=160) private String nombre;
    @Column(length=600) private String descripcion;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=12) private PeriodicidadCobro periodicidad;
    @Column(name="es_pension",nullable=false) private boolean esPension;
    @Column(name="monto_sugerido",precision=12,scale=2) private BigDecimal montoSugerido;
    @Column(name="requiere_aceptacion",nullable=false) private boolean requiereAceptacion;
}
