package com.asiscontrol.service;

import java.util.Map;

/**
 * Catalogo unico de modulos para RegistroAuditoria. Los nombres coinciden con los que el
 * frontend colorea en audit-format.ts; uno que no este alli se muestra en gris.
 */
public final class ModuloAuditoria {

    public static final String SEGURIDAD = "SEGURIDAD";
    public static final String PERSONAS = "PERSONAS";
    public static final String MATRICULA = "MATRICULA";
    public static final String CONFIGURACION_ACADEMICA = "CONFIGURACION_ACADEMICA";
    public static final String ACADEMICO = "ACADEMICO";
    public static final String ASISTENCIA = "ASISTENCIA";
    public static final String HORARIOS = "HORARIOS";
    public static final String FINANZAS = "FINANZAS";
    public static final String TAREAS = "TAREAS";
    public static final String RIESGO = "RIESGO";
    public static final String ARCHIVOS = "ARCHIVOS";
    public static final String NOTIFICACIONES = "NOTIFICACIONES";
    public static final String AUDITORIA = "AUDITORIA";
    public static final String GENERAL = "GENERAL";

    /** Recurso de la URL (/api/{recurso}/...) al modulo que lo gestiona. */
    private static final Map<String, String> POR_RECURSO = Map.ofEntries(
            Map.entry("auth", SEGURIDAD),
            Map.entry("usuarios", SEGURIDAD),
            Map.entry("roles", SEGURIDAD),
            Map.entry("alumnos", PERSONAS),
            Map.entry("docentes", PERSONAS),
            Map.entry("apoderados", PERSONAS),
            Map.entry("familia", PERSONAS),
            Map.entry("matriculas", MATRICULA),
            Map.entry("solicitudes-matricula", MATRICULA),
            Map.entry("configuracion-academica", CONFIGURACION_ACADEMICA),
            Map.entry("secciones", CONFIGURACION_ACADEMICA),
            Map.entry("cursos", ACADEMICO),
            Map.entry("competencias", ACADEMICO),
            Map.entry("cursos-competencias", ACADEMICO),
            Map.entry("asignaciones-cursos", ACADEMICO),
            Map.entry("aulas", ACADEMICO),
            Map.entry("evaluaciones", ACADEMICO),
            Map.entry("calificaciones", ACADEMICO),
            Map.entry("asistencias", ASISTENCIA),
            Map.entry("justificaciones-asistencia", ASISTENCIA),
            Map.entry("horarios", HORARIOS),
            Map.entry("compromisos-pago", FINANZAS),
            Map.entry("transacciones-pago", FINANZAS),
            Map.entry("comprobantes", FINANZAS),
            Map.entry("simulaciones-pago", FINANZAS),
            Map.entry("tareas", TAREAS),
            Map.entry("entregas", TAREAS),
            Map.entry("alertas-riesgo", RIESGO),
            Map.entry("criterios-riesgo", RIESGO),
            Map.entry("evaluaciones-riesgo", RIESGO),
            Map.entry("archivos", ARCHIVOS),
            Map.entry("notificaciones", NOTIFICACIONES),
            Map.entry("auditoria", AUDITORIA)
    );

    private ModuloAuditoria() {
    }

    public static String deRecurso(String recurso) {
        return POR_RECURSO.getOrDefault(recurso, GENERAL);
    }
}
