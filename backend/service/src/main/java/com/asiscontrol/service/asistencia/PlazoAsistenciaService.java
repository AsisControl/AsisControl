// asistencia alumnos
package com.asiscontrol.service.asistencia;

import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import com.asiscontrol.service.AuditoriaService;
import com.asiscontrol.service.MatriculaService;
import com.asiscontrol.service.ModuloAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plazo para editar la asistencia de una sesión: hasta que empiece la siguiente clase del mismo
 * curso y sección ese día, o hasta que termine el día, lo que ocurra antes. Dentro del plazo el
 * docente toma, guarda, finaliza y corrige; después la asistencia queda cerrada para todos.
 * Al vencer, un borrador se finaliza solo y quien quedó sin marca recibe SIN_MARCACION.
 */
@Service
@Transactional
public class PlazoAsistenciaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final SesionClaseRepository sesionRepository;
    private final AsistenciaAlumnoRepository asistenciaRepository;
    private final MatriculaService matriculaService;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public PlazoAsistenciaService(
            SesionClaseRepository sesionRepository,
            AsistenciaAlumnoRepository asistenciaRepository,
            MatriculaService matriculaService,
            AuditoriaService auditoria,
            Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.matriculaService = matriculaService;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    @Transactional(readOnly = true)
    public LocalDateTime limite(SesionClase sesion) {
        return sesionRepository
                .findFirstByAsignacionCursoIdAndFechaAndHoraInicioGreaterThanAndActivoTrueOrderByHoraInicioAsc(
                        sesion.getAsignacionCurso().getId(), sesion.getFecha(), sesion.getHoraInicio())
                .map(siguiente -> sesion.getFecha().atTime(siguiente.getHoraInicio()))
                .orElse(finDelDia(sesion));
    }

    /** Variante para un listado del mismo curso: la siguiente clase del día ya está en la lista. */
    public static LocalDateTime limite(SesionClase sesion, List<SesionClase> delCurso) {
        return delCurso.stream()
                .filter(otra -> otra.getFecha().isEqual(sesion.getFecha())
                        && otra.getHoraInicio().isAfter(sesion.getHoraInicio()))
                .map(otra -> otra.getFecha().atTime(otra.getHoraInicio()))
                .min(Comparator.naturalOrder())
                .orElse(finDelDia(sesion));
    }

    public boolean abierta(LocalDateTime limite) {
        return ahora().isBefore(limite);
    }

    /** Rechaza tomar, guardar, finalizar o corregir fuera del plazo. */
    @Transactional(readOnly = true)
    public void exigirPlazo(SesionClase sesion) {
        LocalDateTime limite = limite(sesion);
        if (!abierta(limite)) {
            throw new BusinessRuleException(
                    "ASISTENCIA_PLAZO_VENCIDO",
                    "El plazo para editar esta asistencia venció " + describir(sesion, limite) + ".");
        }
    }

    /**
     * Finaliza los borradores cuyo plazo venció. Lo llama el job cada minuto y también cada lectura
     * de asistencias, para que nadie vea un borrador ya cerrado aunque el job no haya corrido.
     */
    public int cerrarVencidas() {
        LocalDateTime ahora = ahora();
        int cerradas = 0;
        for (Long id : sesionRepository.idsPorEstadoHasta(EstadoSesionClase.BORRADOR, ahora.toLocalDate())) {
            SesionClase sesion = sesionRepository.bloquear(id).orElse(null);
            if (sesion == null || sesion.getEstado() != EstadoSesionClase.BORRADOR) {
                continue;
            }
            LocalDateTime limite = limite(sesion);
            if (ahora.isBefore(limite)) {
                continue;
            }
            cerrar(sesion, limite);
            cerradas++;
        }
        return cerradas;
    }

    private void cerrar(SesionClase sesion, LocalDateTime limite) {
        AsignacionCurso asignacion = sesion.getAsignacionCurso();
        Set<Long> marcados = asistenciaRepository.findAllBySesionClaseId(sesion.getId()).stream()
                .map(fila -> fila.getAlumno().getId())
                .collect(Collectors.toSet());
        LocalDateTime registro = limite.equals(finDelDia(sesion)) ? limite.minusSeconds(1) : limite;
        LocalTime hora = registro.toLocalTime();
        List<AsistenciaAlumno> sinMarcar = matriculaService
                .alumnosConMatriculaActiva(asignacion.getSeccion().getId()).stream()
                .filter(alumno -> !marcados.contains(alumno.getId()))
                .map(alumno -> {
                    AsistenciaAlumno fila = new AsistenciaAlumno();
                    fila.setAlumno(alumno);
                    fila.setAsignacionCurso(asignacion);
                    fila.setSesionClase(sesion);
                    fila.setDocenteRegistrador(asignacion.getDocente());
                    fila.setFechaRegistro(sesion.getFecha());
                    fila.setHoraRegistro(hora);
                    fila.setEstadoAsistencia(EstadoAsistencia.SIN_MARCACION);
                    fila.setActivo(true);
                    return fila;
                })
                .toList();
        asistenciaRepository.saveAll(sinMarcar);
        sesion.setEstado(EstadoSesionClase.TOMADA);
        sesion.setDocenteRegistrador(asignacion.getDocente());
        sesion.setFechaHoraFinalizacion(registro);
        sesion.setFechaActualizacion(Instant.now(reloj));
        sesionRepository.saveAndFlush(sesion);
        auditoria.registrar("FINALIZAR_ASISTENCIA_AUTOMATICA", ModuloAuditoria.ASISTENCIA, "SesionClase",
                sesion.getId().toString(), ResultadoAuditoria.EXITOSO, "Venció el plazo de edición",
                null, "sinMarcar=" + sinMarcar.size());
    }

    private static LocalDateTime finDelDia(SesionClase sesion) {
        return sesion.getFecha().plusDays(1).atStartOfDay();
    }

    private static String describir(SesionClase sesion, LocalDateTime limite) {
        return limite.equals(finDelDia(sesion)) ? "al terminar el día" : "a las " + limite.format(HORA);
    }
}
