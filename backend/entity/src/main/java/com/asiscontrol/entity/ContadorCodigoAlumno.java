package com.asiscontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ContadorCodigoAlumno")
@Getter
@Setter
@NoArgsConstructor
public class ContadorCodigoAlumno {
    @Id
    @Column(name = "id_contador")
    private Integer id;

    @Column(name = "ultimo_valor", nullable = false)
    private long ultimoValor;
}
