package com.asiscontrol.service;

import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.ClaseHoyResponse;
import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.DashboardDocenteResponse;
import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.MomentoClase;
import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.PendienteResponse;
import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.Prioridad;
import com.asiscontrol.dto.dashboard.DashboardDocenteDtos.TipoPendiente;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inicio del docente autenticado. Solo considera cursos de años ACTIVOS: un año en BORRADOR
 * todavía no se dicta y uno CERRADO ya no genera trabajo pendiente. Usa el reloj del colegio,
 * así respeta también la fecha simulada de desarrollo.
 */
@Service
@Transactional(readOnly = true)
public class DashboardDocenteService {

    /** Pendientes que se muestran como máximo; el resto se ve en cada módulo. */
    static final int MAX_PENDIENTES = 8;

    private final EntityManager em;
    private final ConfiguracionAcademicaService configuracionService;
    private final ModuloAccessGuard accessGuard;
    private final Clock reloj;

    public DashboardDocenteService(
            EntityManager em,
            ConfiguracionAcademicaService configuracionService,
            ModuloAccessGuard accessGuard,
            Clock reloj) {
        this.em = em;
        this.configuracionService = configuracionService;
        this.accessGuard = accessGuard;
        this.reloj = reloj;
    }

    public DashboardDocenteResponse obtener() {
        Long personaId = accessGuard.personaIdActual();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDate hoy = ahora.toLocalDate();
        Map<Integer, Boolean> aniosActivos = new HashMap<>();

        List<ClaseHoyResponse> clases = clasesDeHoy(personaId, hoy, ahora.toLocalTime(), aniosActivos);
        long asistenciasPendientes = clases.stream()
                .filter(clase -> clase.estado() != EstadoSesionClase.TOMADA)
                .filter(clase -> clase.momento() != MomentoClase.PROXIMA)
                .count();

        List<PendienteResponse> tareas = tareasPorCalificar(personaId, ahora, aniosActivos);
        List<PendienteResponse> evaluaciones = evaluacionesPorRegistrar(personaId, hoy, aniosActivos);
        long porRevisar = tareas.stream().mapToLong(PendienteResponse::cantidad).sum() + evaluaciones.size();

        List<PendienteResponse> pendientes = new ArrayList<>();
        pendientes.addAll(evaluaciones);
        pendientes.addAll(tareas);
        justificacionesPendientes(personaId).ifPresent(pendientes::add);
        pendientes.sort(Comparator
                .comparing(PendienteResponse::prioridad)
                .thenComparing(Comparator.comparingLong(PendienteResponse::cantidad).reversed()));

        boolean anioEnCurso = anioActivo(hoy.getYear(), aniosActivos);
        return new DashboardDocenteResponse(
                hoy,
                anioEnCurso ? hoy.getYear() : null,
                anioEnCurso ? configuracionService.periodoActivo(hoy.getYear()) : null,
                clases,
                asistenciasPendientes,
                porRevisar,
                bloquesSemanales(personaId, aniosActivos),
                pendientes.stream().limit(MAX_PENDIENTES).toList());
    }

    static MomentoClase momento(LocalTime inicio, LocalTime fin, LocalTime ahora) {
        if (ahora.isBefore(inicio)) {
            return MomentoClase.PROXIMA;
        }
        return ahora.isBefore(fin) ? MomentoClase.EN_CURSO : MomentoClase.FINALIZADA;
    }

    private List<ClaseHoyResponse> clasesDeHoy(
            Long personaId, LocalDate hoy, LocalTime ahora, Map<Integer, Boolean> aniosActivos) {
        List<SesionClase> sesiones = em.createQuery("""
                        SELECT s FROM SesionClase s
                        JOIN FETCH s.asignacionCurso a
                        JOIN FETCH a.curso
                        JOIN FETCH a.seccion
                        JOIN FETCH s.horario h
                        JOIN FETCH h.aula
                        WHERE s.activo = true AND s.fecha = :hoy
                          AND a.activo = true AND a.docente.persona.id = :personaId
                        """, SesionClase.class)
                .setParameter("hoy", hoy)
                .setParameter("personaId", personaId)
                .getResultList();
        return sesiones.stream()
                .filter(sesion -> anioActivo(sesion.getAsignacionCurso().getSeccion(), aniosActivos))
                .map(sesion -> {
                    AsignacionCurso asignacion = sesion.getAsignacionCurso();
                    return new ClaseHoyResponse(
                            sesion.getId(),
                            asignacion.getId(),
                            asignacion.getCurso().getNombre(),
                            seccion(asignacion.getSeccion()),
                            sesion.getHorario().getAula().getNombre(),
                            sesion.getHoraInicio(),
                            sesion.getHoraFin(),
                            sesion.getEstado(),
                            momento(sesion.getHoraInicio(), sesion.getHoraFin(), ahora),
                            sesion.getEstado() != EstadoSesionClase.TOMADA);
                })
                // Se ordena aquí: la base puede ordenar la hora con otra zona horaria.
                .sorted(Comparator.comparing(ClaseHoyResponse::horaInicio)
                        .thenComparing(ClaseHoyResponse::sesionId))
                .toList();
    }

    private long bloquesSemanales(Long personaId, Map<Integer, Boolean> aniosActivos) {
        List<Object[]> filas = em.createQuery("""
                        SELECT a.seccion.anioAcademico, COUNT(h) FROM Horario h
                        JOIN h.asignacionCurso a
                        WHERE h.activo = true AND a.activo = true AND a.docente.persona.id = :personaId
                        GROUP BY a.seccion.anioAcademico
                        """, Object[].class)
                .setParameter("personaId", personaId)
                .getResultList();
        return filas.stream()
                .filter(fila -> anioActivo((Integer) fila[0], aniosActivos))
                .mapToLong(fila -> (Long) fila[1])
                .sum();
    }

    /** Tareas con entregas sin calificar; si ya pasó la fecha límite, calificarlas es urgente. */
    private List<PendienteResponse> tareasPorCalificar(
            Long personaId, LocalDateTime ahora, Map<Integer, Boolean> aniosActivos) {
        List<Object[]> filas = em.createQuery("""
                        SELECT t.id, t.nombre, t.fechaLimite, a.id, c.nombre, s.grado, s.nombre,
                               s.anioAcademico, COUNT(e)
                        FROM EntregaTarea e
                        JOIN e.tarea t JOIN t.asignacionCurso a JOIN a.curso c JOIN a.seccion s
                        WHERE e.activo = true AND t.activo = true AND a.activo = true
                          AND a.docente.persona.id = :personaId AND e.estado IN :estados
                        GROUP BY t.id, t.nombre, t.fechaLimite, a.id, c.nombre, s.grado, s.nombre,
                                 s.anioAcademico
                        """, Object[].class)
                .setParameter("personaId", personaId)
                .setParameter("estados", EnumSet.of(EstadoTarea.ENTREGADO, EstadoTarea.ENTREGADO_TARDE))
                .getResultList();
        List<PendienteResponse> result = new ArrayList<>();
        for (Object[] fila : filas) {
            if (!anioActivo((Integer) fila[7], aniosActivos)) {
                continue;
            }
            LocalDateTime limite = (LocalDateTime) fila[2];
            result.add(new PendienteResponse(
                    TipoPendiente.TAREA,
                    (Long) fila[0],
                    (Long) fila[3],
                    (String) fila[1],
                    fila[4] + " · " + fila[5] + "° Grado " + fila[6],
                    (Long) fila[8],
                    limite != null && limite.isBefore(ahora) ? Prioridad.ALTA : Prioridad.MEDIA));
        }
        return result;
    }

    /**
     * Evaluaciones en borrador cuya fecha ya llegó: hay que registrar sus niveles y publicarlas.
     * {@code cantidad} son los niveles que faltan (alumnos activos × competencias); 0 significa
     * que la planilla está completa y solo falta publicar. Una fecha pasada la vuelve urgente.
     */
    private List<PendienteResponse> evaluacionesPorRegistrar(
            Long personaId, LocalDate hoy, Map<Integer, Boolean> aniosActivos) {
        List<Evaluacion> evaluaciones = em.createQuery("""
                        SELECT e FROM Evaluacion e
                        JOIN FETCH e.asignacionCurso a
                        JOIN FETCH a.curso
                        JOIN FETCH a.seccion
                        WHERE e.activo = true AND a.activo = true
                          AND a.docente.persona.id = :personaId
                          AND e.estado = :estado AND e.fechaEvaluacion <= :hoy
                        ORDER BY e.fechaEvaluacion, e.id
                        """, Evaluacion.class)
                .setParameter("personaId", personaId)
                .setParameter("estado", EstadoEvaluacion.BORRADOR)
                .setParameter("hoy", hoy)
                .getResultList();
        Map<Long, Long> alumnosPorSeccion = new HashMap<>();
        List<PendienteResponse> result = new ArrayList<>();
        for (Evaluacion evaluacion : evaluaciones) {
            AsignacionCurso asignacion = evaluacion.getAsignacionCurso();
            if (!anioActivo(asignacion.getSeccion(), aniosActivos)) {
                continue;
            }
            long alumnos = alumnosPorSeccion.computeIfAbsent(
                    asignacion.getSeccion().getId(), this::alumnosActivos);
            long esperados = alumnos * evaluacion.getCompetencias().size();
            long faltantes = Math.max(0, esperados - nivelesRegistrados(evaluacion.getId()));
            result.add(new PendienteResponse(
                    TipoPendiente.EVALUACION,
                    evaluacion.getId(),
                    asignacion.getId(),
                    evaluacion.getTitulo(),
                    asignacion.getCurso().getNombre() + " · " + seccion(asignacion.getSeccion()),
                    faltantes,
                    evaluacion.getFechaEvaluacion().isBefore(hoy) ? Prioridad.ALTA : Prioridad.MEDIA));
        }
        return result;
    }

    /** Justificaciones de inasistencia que el docente aún no revisó, agrupadas en un pendiente. */
    private Optional<PendienteResponse> justificacionesPendientes(Long personaId) {
        long cantidad = em.createQuery("""
                        SELECT COUNT(j) FROM JustificacionAsistencia j
                        WHERE j.activo = true AND j.estado IN :estados
                          AND j.asistenciaAlumno.asignacionCurso.docente.persona.id = :personaId
                        """, Long.class)
                .setParameter("estados", EnumSet.of(EstadoJustificacion.ENVIADA, EstadoJustificacion.EN_REVISION))
                .setParameter("personaId", personaId)
                .getSingleResult();
        if (cantidad == 0) {
            return Optional.empty();
        }
        return Optional.of(new PendienteResponse(
                TipoPendiente.JUSTIFICACION, null, null,
                "Justificaciones de inasistencia", "Solicitudes enviadas por apoderados",
                cantidad, Prioridad.MEDIA));
    }

    private long alumnosActivos(Long seccionId) {
        return em.createQuery("""
                        SELECT COUNT(m) FROM Matricula m
                        WHERE m.activo = true AND m.estado = :estado AND m.seccion.id = :seccionId
                        """, Long.class)
                .setParameter("estado", EstadoMatricula.ACTIVA)
                .setParameter("seccionId", seccionId)
                .getSingleResult();
    }

    private long nivelesRegistrados(Long evaluacionId) {
        return em.createQuery("""
                        SELECT COUNT(c) FROM Calificacion c
                        WHERE c.activo = true AND c.evaluacion.id = :evaluacionId
                        """, Long.class)
                .setParameter("evaluacionId", evaluacionId)
                .getSingleResult();
    }

    private boolean anioActivo(Seccion seccion, Map<Integer, Boolean> cache) {
        return anioActivo(seccion.getAnioAcademico(), cache);
    }

    private boolean anioActivo(Integer anio, Map<Integer, Boolean> cache) {
        return cache.computeIfAbsent(anio, numero -> configuracionService.buscarOpcional(numero)
                .map(AnioAcademico::getEstado)
                .filter(estado -> estado == EstadoAnioAcademico.ACTIVO)
                .isPresent());
    }

    private static String seccion(Seccion seccion) {
        return seccion.getGrado() + "° Grado " + seccion.getNombre();
    }
}
