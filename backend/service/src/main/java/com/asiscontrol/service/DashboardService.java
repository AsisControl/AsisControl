package com.asiscontrol.service;

import com.asiscontrol.dto.dashboard.DashboardDtos.DashboardResponse;
import com.asiscontrol.dto.dashboard.DashboardDtos.IndicadorResponse;
import com.asiscontrol.entity.enums.EstadoAlerta;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final String TONO_NEUTRO = "neutral";
    private static final String TONO_EXITO = "success";
    private static final String TONO_ADVERTENCIA = "warning";
    private static final String TONO_PELIGRO = "danger";
    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");

    private final EntityManager entityManager;
    private final Clock reloj;

    public DashboardService(EntityManager entityManager, Clock reloj) {
        this.entityManager = entityManager;
        this.reloj = reloj;
    }

    public DashboardResponse obtener(Authentication authentication) {
        Identidad identidad = identidad(authentication);
        List<IndicadorResponse> indicadores = switch (identidad.rol()) {
            case "ADMINISTRADOR", "DIRECTOR" -> indicadoresDireccion();
            case "SECRETARIA" -> indicadoresSecretaria();
            case "TESORERIA" -> indicadoresTesoreria();
            case "DOCENTE" -> indicadoresDocente(identidad.personaId());
            case "ALUMNO" -> indicadoresAlumno(identidad.personaId());
            case "APODERADO" -> indicadoresApoderado(identidad.personaId());
            default -> new ArrayList<>();
        };
        indicadores.add(indicador(
                "NOTIFICACIONES_NO_LEIDAS",
                "Notificaciones no leídas",
                contar("""
                        SELECT COUNT(n) FROM Notificacion n
                        WHERE n.activo = true
                          AND n.leida = false
                          AND n.destinatario.id = :usuarioId
                        """, Map.of("usuarioId", identidad.usuarioId())),
                TONO_NEUTRO,
                "/notificaciones"
        ));
        return new DashboardResponse(
                identidad.rol(),
                identidad.personaId(),
                Instant.now(),
                List.copyOf(indicadores)
        );
    }

    private List<IndicadorResponse> indicadoresDireccion() {
        List<IndicadorResponse> result = new ArrayList<>();
        result.add(indicador("ALUMNOS_ACTIVOS", "Alumnos activos",
                contar("SELECT COUNT(a) FROM Alumno a WHERE a.activo = true", Map.of()),
                TONO_NEUTRO, "/alumnos"));
        result.add(indicador("DOCENTES_ACTIVOS", "Docentes activos",
                contar("SELECT COUNT(d) FROM Docente d WHERE d.activo = true", Map.of()),
                TONO_NEUTRO, "/docentes"));
        result.add(indicador("MATRICULAS_ACTIVAS", "Matrículas activas",
                contar("""
                        SELECT COUNT(m) FROM Matricula m
                        WHERE m.activo = true AND m.estado = :estado
                        """, Map.of("estado", EstadoMatricula.ACTIVA)),
                TONO_EXITO, "/matriculas"));
        result.add(indicador("INASISTENCIAS_HOY", "Inasistencias de hoy",
                contar("""
                        SELECT COUNT(a) FROM AsistenciaAlumno a
                        WHERE a.activo = true
                          AND a.sesionClase.estado = com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA
                          AND a.fechaRegistro = :fecha
                          AND a.estadoAsistencia = :estado
                        """, Map.of("fecha", LocalDate.now(reloj), "estado", EstadoAsistencia.INASISTENCIA)),
                TONO_ADVERTENCIA, "/asistencias"));
        result.add(indicador("PAGOS_VENCIDOS", "Pagos vencidos",
                contar("""
                        SELECT COUNT(c) FROM CompromisoPago c
                        WHERE c.activo = true
                          AND c.estadoAdministrativo = :estadoAdministrativo
                          AND c.montoPagado < c.montoTotal
                          AND c.fechaVencimiento < :hoy
                        """, Map.of("estadoAdministrativo", EstadoAdministrativoCompromiso.ACTIVO,
                        "hoy", LocalDate.now(ZONA_NEGOCIO))),
                TONO_PELIGRO, "/pagos"));
        result.add(indicador("ALERTAS_ACTIVAS", "Alertas de riesgo activas",
                contar("""
                        SELECT COUNT(a) FROM AlertaRiesgo a
                        WHERE a.activo = true AND not exists (select r.id from RegistroAuditoria r
                            where r.entidad = 'AlertaRiesgo' and r.registroId = cast(a.id as string)
                            and r.modulo = 'RIESGO' and r.accion = 'EVALUAR_CRITERIO_RIESGO'
                            and r.motivo = 'DATOS_INSUFICIENTES'
                            and r.id = (select max(u.id) from RegistroAuditoria u
                                where u.entidad = r.entidad and u.registroId = r.registroId
                                and u.modulo = r.modulo and u.accion = r.accion)) AND a.estado IN :estados
                        """, Map.of("estados", EnumSet.of(EstadoAlerta.ACTIVA, EstadoAlerta.EN_SEGUIMIENTO))),
                TONO_PELIGRO, "/alertas-riesgo"));
        return result;
    }

    private List<IndicadorResponse> indicadoresSecretaria() {
        return new ArrayList<>(List.of(
                indicador("SOLICITUDES_PENDIENTES", "Solicitudes pendientes",
                        contar("""
                                SELECT COUNT(s) FROM SolicitudMatricula s
                                WHERE s.activo = true
                                  AND s.estado IN (
                                      com.asiscontrol.entity.enums.EstadoSolicitudMatricula.ENVIADA,
                                      com.asiscontrol.entity.enums.EstadoSolicitudMatricula.EN_REVISION,
                                      com.asiscontrol.entity.enums.EstadoSolicitudMatricula.OBSERVADA
                                  )
                                """, Map.of()),
                        TONO_ADVERTENCIA, "/solicitudes-matricula"),
                indicador("MATRICULAS_ACTIVAS", "Matrículas activas",
                        contar("""
                                SELECT COUNT(m) FROM Matricula m
                                WHERE m.activo = true AND m.estado = :estado
                                """, Map.of("estado", EstadoMatricula.ACTIVA)),
                        TONO_EXITO, "/matriculas"),
                indicador("SECCIONES_SIN_CUPO", "Secciones sin cupo",
                        contar("""
                                SELECT COUNT(s) FROM Seccion s
                                WHERE s.activo = true
                                  AND (SELECT COUNT(m) FROM Matricula m
                                       WHERE m.activo = true
                                         AND m.estado = :estado
                                         AND m.seccion.id = s.id) >= s.capacidadMaxima
                                """, Map.of("estado", EstadoMatricula.ACTIVA)),
                        TONO_ADVERTENCIA, "/secciones")
        ));
    }

    private List<IndicadorResponse> indicadoresTesoreria() {
        return new ArrayList<>(List.of(
                indicador("PAGOS_PENDIENTES", "Compromisos pendientes",
                        contar("""
                                SELECT COUNT(c) FROM CompromisoPago c
                                WHERE c.activo = true
                                  AND c.estadoAdministrativo = :estadoAdministrativo
                                  AND c.montoPagado < c.montoTotal
                                  AND c.fechaVencimiento >= :hoy
                                """, Map.of("estadoAdministrativo", EstadoAdministrativoCompromiso.ACTIVO,
                                "hoy", LocalDate.now(ZONA_NEGOCIO))),
                        TONO_ADVERTENCIA, "/pagos"),
                indicador("PAGOS_VENCIDOS", "Compromisos vencidos",
                        contar("""
                                SELECT COUNT(c) FROM CompromisoPago c
                                WHERE c.activo = true
                                  AND c.estadoAdministrativo = :estadoAdministrativo
                                  AND c.montoPagado < c.montoTotal
                                  AND c.fechaVencimiento < :hoy
                                """, Map.of("estadoAdministrativo", EstadoAdministrativoCompromiso.ACTIVO,
                                "hoy", LocalDate.now(ZONA_NEGOCIO))),
                        TONO_PELIGRO, "/pagos"),
                indicador("TRANSACCIONES_PENDIENTES", "Transacciones por confirmar",
                        contar("""
                                SELECT COUNT(t) FROM TransaccionPago t
                                WHERE t.activo = true
                                  AND t.estado = com.asiscontrol.entity.enums.EstadoTransaccionPago.PENDIENTE
                                """, Map.of()),
                        TONO_ADVERTENCIA, "/transacciones-pago")
        ));
    }

    private List<IndicadorResponse> indicadoresDocente(Long personaId) {
        Map<String, Object> persona = Map.of("personaId", personaId);
        List<IndicadorResponse> result = new ArrayList<>();
        result.add(indicador("CURSOS_ASIGNADOS", "Cursos asignados",
                contar("""
                        SELECT COUNT(a) FROM AsignacionCurso a
                        WHERE a.activo = true AND a.docente.persona.id = :personaId
                        """, persona),
                TONO_NEUTRO, "/mis-cursos"));
        result.add(indicador("TAREAS_PUBLICADAS", "Tareas publicadas",
                contar("""
                        SELECT COUNT(t) FROM Tarea t
                        WHERE t.activo = true
                          AND t.asignacionCurso.docente.persona.id = :personaId
                          AND t.estadoPublicacion = :estado
                        """, parametros(persona, "estado", EstadoPublicacionTarea.PUBLICADA)),
                TONO_NEUTRO, "/tareas"));
        result.add(indicador("ENTREGAS_POR_CALIFICAR", "Entregas por calificar",
                contar("""
                        SELECT COUNT(e) FROM EntregaTarea e
                        WHERE e.activo = true
                          AND e.tarea.asignacionCurso.docente.persona.id = :personaId
                          AND e.estado IN :estados
                        """, parametros(persona, "estados", EnumSet.of(
                        EstadoTarea.ENTREGADO,
                        EstadoTarea.ENTREGADO_TARDE))),
                TONO_ADVERTENCIA, "/tareas/entregas"));
        result.add(indicador("EVALUACIONES_BORRADOR", "Evaluaciones en borrador",
                contar("""
                        SELECT COUNT(e) FROM Evaluacion e
                        WHERE e.activo = true
                          AND e.asignacionCurso.docente.persona.id = :personaId
                          AND e.estado = :estado
                        """, parametros(persona, "estado", EstadoEvaluacion.BORRADOR)),
                TONO_NEUTRO, "/evaluaciones"));
        return result;
    }

    private List<IndicadorResponse> indicadoresAlumno(Long personaId) {
        Map<String, Object> persona = Map.of("personaId", personaId);
        List<IndicadorResponse> result = new ArrayList<>();
        result.add(indicador("TAREAS_PENDIENTES", "Tareas pendientes",
                contar("""
                        SELECT COUNT(t) FROM Tarea t, Matricula m
                        WHERE m.activo = true
                          AND m.estado = :matriculaActiva
                          AND m.alumno.persona.id = :personaId
                          AND t.activo = true
                          AND t.estadoPublicacion = :tareaPublicada
                          AND t.asignacionCurso.seccion.id = m.seccion.id
                          AND NOT EXISTS (
                              SELECT e.id FROM EntregaTarea e
                              WHERE e.activo = true
                                AND e.tarea.id = t.id
                                AND e.alumno.id = m.alumno.id
                          )
                        """, parametros(persona,
                        "matriculaActiva", EstadoMatricula.ACTIVA,
                        "tareaPublicada", EstadoPublicacionTarea.PUBLICADA)),
                TONO_ADVERTENCIA, "/tareas"));
        result.add(indicador("INASISTENCIAS", "Inasistencias del año",
                contar("""
                        SELECT COUNT(a) FROM AsistenciaAlumno a
                        WHERE a.activo = true
                          AND a.sesionClase.estado = com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA
                          AND a.alumno.persona.id = :personaId
                          AND a.fechaRegistro BETWEEN :inicio AND :fin
                          AND a.estadoAsistencia = :estado
                        """, parametros(persona,
                        "inicio", Year.now(reloj).atDay(1),
                        "fin", LocalDate.now(reloj),
                        "estado", EstadoAsistencia.INASISTENCIA)),
                TONO_ADVERTENCIA, "/asistencias"));
        result.add(indicador("PAGOS_PENDIENTES", "Pagos pendientes",
                contar("""
                        SELECT COUNT(c) FROM CompromisoPago c
                        WHERE c.activo = true
                          AND c.matricula.alumno.persona.id = :personaId
                          AND c.estadoAdministrativo = :estadoAdministrativo
                          AND c.montoPagado < c.montoTotal
                        """, parametros(persona,
                        "estadoAdministrativo", EstadoAdministrativoCompromiso.ACTIVO)),
                TONO_ADVERTENCIA, "/pagos"));
        result.add(indicador("ALERTAS_ACTIVAS", "Alertas activas",
                contar("""
                        SELECT COUNT(a) FROM AlertaRiesgo a
                        WHERE a.activo = true AND not exists (select r.id from RegistroAuditoria r
                            where r.entidad = 'AlertaRiesgo' and r.registroId = cast(a.id as string)
                            and r.modulo = 'RIESGO' and r.accion = 'EVALUAR_CRITERIO_RIESGO'
                            and r.motivo = 'DATOS_INSUFICIENTES'
                            and r.id = (select max(u.id) from RegistroAuditoria u
                                where u.entidad = r.entidad and u.registroId = r.registroId
                                and u.modulo = r.modulo and u.accion = r.accion))
                          AND a.alumno.persona.id = :personaId
                          AND a.estado IN :estados
                        """, parametros(persona, "estados", EnumSet.of(
                        EstadoAlerta.ACTIVA,
                        EstadoAlerta.EN_SEGUIMIENTO))),
                TONO_PELIGRO, "/alertas-riesgo"));
        return result;
    }

    private List<IndicadorResponse> indicadoresApoderado(Long personaId) {
        Map<String, Object> persona = Map.of("personaId", personaId);
        List<IndicadorResponse> result = new ArrayList<>();
        result.add(indicador("ESTUDIANTES_VINCULADOS", "Estudiantes vinculados",
                contar("""
                        SELECT COUNT(v) FROM AlumnoApoderado v
                        WHERE v.activo = true AND v.apoderado.persona.id = :personaId
                        """, persona),
                TONO_NEUTRO, "/familia"));
        result.add(indicador("TAREAS_PENDIENTES", "Tareas pendientes",
                contar("""
                        SELECT COUNT(t) FROM Tarea t, Matricula m, AlumnoApoderado v
                        WHERE v.activo = true
                          AND v.apoderado.persona.id = :personaId
                          AND m.activo = true
                          AND m.estado = :matriculaActiva
                          AND m.alumno.id = v.alumno.id
                          AND t.activo = true
                          AND t.estadoPublicacion = :tareaPublicada
                          AND t.asignacionCurso.seccion.id = m.seccion.id
                          AND NOT EXISTS (
                              SELECT e.id FROM EntregaTarea e
                              WHERE e.activo = true
                                AND e.tarea.id = t.id
                                AND e.alumno.id = v.alumno.id
                          )
                        """, parametros(persona,
                        "matriculaActiva", EstadoMatricula.ACTIVA,
                        "tareaPublicada", EstadoPublicacionTarea.PUBLICADA)),
                TONO_ADVERTENCIA, "/tareas"));
        result.add(indicador("PAGOS_PENDIENTES", "Pagos pendientes",
                contar("""
                        SELECT COUNT(c) FROM CompromisoPago c, AlumnoApoderado v
                        WHERE v.activo = true
                          AND v.apoderado.persona.id = :personaId
                          AND c.activo = true
                          AND c.matricula.alumno.id = v.alumno.id
                          AND c.estadoAdministrativo = :estadoAdministrativo
                          AND c.montoPagado < c.montoTotal
                        """, parametros(persona,
                        "estadoAdministrativo", EstadoAdministrativoCompromiso.ACTIVO)),
                TONO_ADVERTENCIA, "/pagos"));
        result.add(indicador("ALERTAS_ACTIVAS", "Alertas activas",
                contar("""
                        SELECT COUNT(a) FROM AlertaRiesgo a, AlumnoApoderado v
                        WHERE v.activo = true AND not exists (select r.id from RegistroAuditoria r
                            where r.entidad = 'AlertaRiesgo' and r.registroId = cast(a.id as string)
                            and r.modulo = 'RIESGO' and r.accion = 'EVALUAR_CRITERIO_RIESGO'
                            and r.motivo = 'DATOS_INSUFICIENTES'
                            and r.id = (select max(u.id) from RegistroAuditoria u
                                where u.entidad = r.entidad and u.registroId = r.registroId
                                and u.modulo = r.modulo and u.accion = r.accion))
                          AND v.apoderado.persona.id = :personaId
                          AND a.activo = true
                          AND a.alumno.id = v.alumno.id
                          AND a.estado IN :estados
                        """, parametros(persona, "estados", EnumSet.of(
                        EstadoAlerta.ACTIVA,
                        EstadoAlerta.EN_SEGUIMIENTO))),
                TONO_PELIGRO, "/alertas-riesgo"));
        return result;
    }

    private long contar(String jpql, Map<String, Object> parametros) {
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        parametros.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private Map<String, Object> parametros(Map<String, Object> base, Object... pares) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (int index = 0; index < pares.length; index += 2) {
            result.put((String) pares[index], pares[index + 1]);
        }
        return result;
    }

    private IndicadorResponse indicador(
            String codigo,
            String etiqueta,
            long valor,
            String tono,
            String rutaDestino
    ) {
        return new IndicadorResponse(codigo, etiqueta, valor, tono, rutaDestino);
    }

    private Identidad identidad(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere una sesión autenticada");
        }
        Long usuarioId;
        Long personaId;
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            usuarioId = numeroClaim(jwt, "usuarioId");
            personaId = numeroClaim(jwt, "personaId");
        } else if (principal instanceof AuthenticatedUser user) {
            usuarioId = user.getUsuario().getId();
            personaId = user.getUsuario().getPersona().getId();
        } else {
            throw new AccessDeniedException("La sesión no contiene una identidad válida");
        }
        String rol = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("La sesión no contiene un rol válido"));
        return new Identidad(usuarioId, personaId, rol);
    }

    private Long numeroClaim(Jwt jwt, String nombre) {
        Object value = jwt.getClaim(nombre);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("El token no contiene el claim requerido: " + nombre);
    }

    private record Identidad(Long usuarioId, Long personaId, String rol) {
    }
}
