package com.asiscontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Último número emitido por prefijo y año: EST20260001, DOC20260001, APO20260001... */
@Entity
@Table(name = "ContadorCodigoInstitucional")
@IdClass(ContadorCodigoInstitucional.Clave.class)
@Getter
@Setter
@NoArgsConstructor
public class ContadorCodigoInstitucional {
    @Id
    @Column(name = "prefijo", length = 3)
    private String prefijo;

    @Id
    @Column(name = "anio")
    private Integer anio;

    @Column(name = "ultimo_valor", nullable = false)
    private long ultimoValor;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private String prefijo;
        private Integer anio;

        public Clave(String prefijo, Integer anio) {
            this.prefijo = prefijo;
            this.anio = anio;
        }
    }
}
