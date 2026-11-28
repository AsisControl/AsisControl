package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Una etapa confirmada del paquete demo. Nunca se usa para restaurar ni sobrescribir datos. */
@Entity
@Getter
@Setter
@Table(name = "CargaDemostracion", uniqueConstraints = @UniqueConstraint(
        name = "uk_carga_demo_etapa", columnNames = {"paquete", "etapa"}))
public class CargaDemostracion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_carga_demostracion")
    private Long id;
    @Column(name = "hash_archivo", length = 64) private String hashArchivo;
    @Column(name = "indicaciones", columnDefinition = "TEXT") private String indicaciones;
    @Column(nullable = false, length = 50) private String paquete;
    @Column(nullable = false, length = 30) private String etapa;
    @Column(name = "fecha_referencia", nullable = false) private LocalDate fechaReferencia;
    @Column(name = "fecha_ejecucion", nullable = false) private Instant fechaEjecucion;
    @ElementCollection
    @CollectionTable(name = "CargaDemostracionRecurso", joinColumns = @JoinColumn(name = "id_carga_demostracion"))
    @MapKeyColumn(name = "clave", length = 100)
    @Column(name = "id_recurso", nullable = false)
    private Map<String, Long> recursos = new LinkedHashMap<>();
}
