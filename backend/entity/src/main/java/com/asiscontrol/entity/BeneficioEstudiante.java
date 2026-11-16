package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.TipoBeneficio;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

@Getter @Setter @Entity @Table(name="BeneficioEstudiante")
public class BeneficioEstudiante extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_beneficio_estudiante") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_alumno",nullable=false) private Alumno alumno;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_concepto_cobro",nullable=false) private ConceptoCobro concepto;
    @Column(name="anio_academico",nullable=false) private int anioAcademico;
    @Column(nullable=false,length=160) private String nombre;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=20) private TipoBeneficio tipo;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal valor;
    @Column(name="mes_inicio",nullable=false) private int mesInicio;
    @Column(name="mes_fin",nullable=false) private int mesFin;
    @Column(nullable=false,length=500) private String motivo;
}
