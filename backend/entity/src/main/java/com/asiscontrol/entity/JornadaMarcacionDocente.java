package com.asiscontrol.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Jornada observada mientras el sistema está operativo; nunca se reconstruyen días pasados. */
@Entity @Getter @Setter
@Table(name = "JornadaMarcacionDocente", uniqueConstraints = @UniqueConstraint(
        name = "uk_jornada_docente_fecha", columnNames = {"id_docente", "fecha"}))
public class JornadaMarcacionDocente extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_jornada_marcacion") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_docente", nullable = false) private Docente docente;
    @Column(nullable = false) private LocalDate fecha;
    @Column(nullable = false) private LocalDateTime apertura;
    @Column(name = "limite_puntualidad", nullable = false) private LocalDateTime limitePuntualidad;
    @Column(nullable = false) private LocalDateTime cierre;
}
