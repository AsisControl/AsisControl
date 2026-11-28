package com.asiscontrol.repository;

/**
 * Condiciones JPQL sobre {@code HistorialSeccionMatricula h}: qué sigue visible de una sección que el
 * estudiante dejó. Se ve lo publicado hasta el cambio y aquello en lo que ya tenía entrega o nota.
 */
public final class VisibilidadSeccionAnterior {

    /** El historial pertenece a una matrícula que sigue activa. */
    public static final String MATRICULA_VIGENTE = """
             h.activo = true AND h.matricula.activo = true
             AND h.matricula.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
            """;

    /** Usa el alias {@code t} para la tarea. */
    public static final String TAREA = """
             h.seccionAnterior.id = t.asignacionCurso.seccion.id
             AND ((t.fechaPublicacion IS NOT NULL AND t.fechaPublicacion <= h.fechaCambioLocal)
                  OR (t.fechaPublicacion IS NULL AND t.fechaCreacion <= h.fechaCambio)
                  OR EXISTS (SELECT en.id FROM EntregaTarea en WHERE en.activo = true
                             AND en.tarea.id = t.id AND en.alumno.id = h.matricula.alumno.id))
            """;

    /** Usa el alias {@code e} para la evaluación. */
    public static final String EVALUACION = """
             h.seccionAnterior.id = e.asignacionCurso.seccion.id
             AND ((e.fechaPublicacion IS NOT NULL AND e.fechaPublicacion <= h.fechaCambio)
                  OR (e.fechaPublicacion IS NULL AND e.fechaCreacion <= h.fechaCambio)
                  OR EXISTS (SELECT ca.id FROM Calificacion ca WHERE ca.activo = true
                             AND ca.evaluacion.id = e.id AND ca.alumno.id = h.matricula.alumno.id))
            """;

    private VisibilidadSeccionAnterior() {}
}
