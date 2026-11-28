package com.asiscontrol.entity;

import com.asiscontrol.entity.enums.NivelLogro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "EscalaInstitucional")
public class EscalaInstitucional extends AuditableEntity {
    public enum Ranura { BORRADOR, VIGENTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_escala")
    private Long id;
    @Column(name = "numero_version", nullable = false, unique = true)
    private int numeroVersion;
    // Una ranura nula identifica una escala histórica. Las dos ranuras activas son únicas.
    @Enumerated(EnumType.STRING)
    @Column(length = 12, unique = true)
    private Ranura ranura = Ranura.BORRADOR;
    @Column(name = "fecha_aplicacion")
    private Instant fechaAplicacion;
    @Column(name = "actor_aplicacion", length = 180)
    private String actorAplicacion;
    @Column(name = "motivo_aplicacion", length = 300)
    private String motivoAplicacion;

    @ElementCollection
    @CollectionTable(name = "EscalaInstitucionalRango", joinColumns = @JoinColumn(name = "id_escala"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "nivel", length = 2)
    private Map<NivelLogro, Rango> rangos = new HashMap<>();

    @Embeddable
    public record Rango(int minimo, int maximo) {
        public boolean contiene(int nota) {
            return nota >= minimo && nota <= maximo;
        }
    }
}
