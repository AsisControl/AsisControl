package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @Entity
@Table(name="TarifaPension",uniqueConstraints=@UniqueConstraint(name="uk_tarifa_pension_anio_grado",columnNames={"id_concepto_cobro","anio_academico","grado"}))
public class TarifaPension extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_tarifa_pension") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_concepto_cobro",nullable=false) private ConceptoCobro concepto;
    @Column(name="anio_academico",nullable=false) private int anioAcademico;
    @Column(nullable=false) private int grado;
    @Column(name="monto_mensual",nullable=false,precision=12,scale=2) private BigDecimal montoMensual;
}
