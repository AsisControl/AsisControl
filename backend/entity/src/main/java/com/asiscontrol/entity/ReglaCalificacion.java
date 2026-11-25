package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.TipoEvaluacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ReglaCalificacion", uniqueConstraints = @UniqueConstraint(
        name = "uk_regla_curso_grado_numero", columnNames = {"id_curso", "id_grado_academico", "numero_version"}))
public class ReglaCalificacion extends AuditableEntity {
    public enum Estado { BORRADOR, VIGENTE, HISTORICA }
    public enum Formula { PROMEDIO_PONDERADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regla_calificacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curso", nullable = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grado_academico", nullable = false)
    private GradoAcademico grado;

    @Column(name = "numero_version", nullable = false)
    private int numeroVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado = Estado.BORRADOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "formula", nullable = false, length = 30)
    private Formula formula;

    @ElementCollection
    @CollectionTable(name = "ReglaCalificacionComponente", joinColumns = @JoinColumn(name = "id_regla_calificacion"))
    @MapKeyColumn(name = "tipo_evaluacion", length = 32)
    @MapKeyEnumerated(EnumType.STRING)
    private Map<TipoEvaluacion, Componente> componentes = new HashMap<>();

    @Getter
    @NoArgsConstructor
    @Embeddable
    public static class Componente {
        @Column(name = "nombre", nullable = false, length = 90)
        private String nombre;
        @Column(name = "peso", nullable = false, precision = 5, scale = 2)
        private BigDecimal peso;

        public Componente(String nombre, BigDecimal peso) {
            this.nombre = nombre;
            this.peso = peso;
        }
    }
}
