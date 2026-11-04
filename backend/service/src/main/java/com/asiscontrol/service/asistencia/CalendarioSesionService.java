// asistencia alumnos
package com.asiscontrol.service.asistencia;

import com.asiscontrol.dto.asistencia.SesionClaseDtos.SesionClaseResponse;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.entity.planificacion.DiaNoLectivo;
import com.asiscontrol.repository.asistencia.SesionClaseRepository;
import com.asiscontrol.service.AsignacionCursoService;
import com.asiscontrol.service.ConfiguracionAcademicaService;
import com.asiscontrol.service.DiaNoLectivoService;
import com.asiscontrol.service.HorarioService;
import com.asiscontrol.service.ModuloAccessGuard;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calendario de sesiones de clase de cada asignación. Genera un encuentro por cada grupo de
 * bloques consecutivos del mismo día, en las fechas del año académico que no son días no
 * lectivos. Solo crea o retira sesiones desde hoy en adelante, y nunca toca una sesión en
 * BORRADOR o TOMADA: el historial de lo ya registrado se conserva aunque cambie el horario.
 */
@Service
@Transactional(readOnly = true)
public class CalendarioSesionService {

    private final SesionClaseRepository sesionRepository;
    private final HorarioService horarioService;
    private final AsignacionCursoService asignacionService;
    private final ConfiguracionAcademicaService configuracionService;
    private final DiaNoLectivoService diaNoLectivoService;
    private final ModuloAccessGuard accessGuard;
    private final Clock reloj;

    public CalendarioSesionService(
            SesionClaseRepository sesionRepository,
            HorarioService horarioService,
            AsignacionCursoService asignacionService,
            ConfiguracionAcademicaService configuracionService,
            DiaNoLectivoService diaNoLectivoService,
            ModuloAccessGuard accessGuard,
            Clock reloj) {
        this.sesionRepository = sesionRepository;
        this.horarioService = horarioService;
        this.asignacionService = asignacionService;
        this.configuracionService = configuracionService;
        this.diaNoLectivoService = diaNoLectivoService;
        this.accessGuard = accessGuard;
        this.reloj = reloj;
    }

    /** Se ejecuta dentro de la transacción del cambio de horario: si falla, se revierte todo. */
    @EventListener
    @Transactional
    public void alCambiarHorario(HorarioAsignacionCambiadoEvent evento) {
        evento.asignacionCursoIds().forEach(this::sincronizar);
    }

    @EventListener
    @Transactional
    public void alCambiarCalendario(CalendarioAnioCambiadoEvent evento) {
        horarioService.asignacionesConHorarioEnAnio(evento.anio()).forEach(this::sincronizar);
    }

    @Transactional
    public void sincronizar(Long asignacionCursoId) {
        LocalDate hoy = LocalDate.now(reloj);
        Map<LocalDate, List<Encuentro>> esperadas = asignacionService.buscarActiva(asignacionCursoId)
                .map(asignacion -> calcularEncuentros(asignacion, hoy))
                .orElse(Map.of());

        Map<Clave, SesionClase> existentes = new HashMap<>();
        for (SesionClase sesion : sesionRepository
                .findAllByAsignacionCursoIdAndFechaGreaterThanEqual(asignacionCursoId, hoy)) {
            existentes.put(new Clave(sesion.getFecha(), sesion.getHoraInicio()), sesion);
        }

        List<SesionClase> cambios = new ArrayList<>();
        Set<Clave> claves = new HashSet<>();
        esperadas.forEach((fecha, encuentros) -> {
            for (Encuentro encuentro : encuentros) {
                Clave clave = new Clave(fecha, encuentro.horaInicio());
                claves.add(clave);
                SesionClase sesion = existentes.get(clave);
                if (sesion == null) {
                    cambios.add(nueva(encuentro, fecha));
                } else if (sesion.getEstado() == EstadoSesionClase.PENDIENTE) {
                    sesion.setActivo(true);
                    sesion.setHorario(encuentro.horario());
                    sesion.setHoraFin(encuentro.horaFin());
                    cambios.add(sesion);
                }
            }
        });
        // Sesiones futuras que ya no corresponden al horario: se retiran sin borrarse.
        existentes.forEach((clave, sesion) -> {
            if (!claves.contains(clave) && sesion.isActivo()
                    && sesion.getEstado() == EstadoSesionClase.PENDIENTE) {
                sesion.setActivo(false);
                cambios.add(sesion);
            }
        });
        sesionRepository.saveAll(cambios);
    }

    public List<SesionClaseResponse> listar(Long asignacionCursoId) {
        AsignacionCurso asignacion = asignacionService.obtenerEntidad(asignacionCursoId);
        accessGuard.verificarAsignacionDocente(asignacion);
        LocalDate hoy = LocalDate.now(reloj);
        return sesionRepository
                .findAllByAsignacionCursoIdAndActivoTrueOrderByFechaAscHoraInicioAsc(asignacionCursoId)
                .stream()
                .map(sesion -> mapear(sesion, hoy))
                .toList();
    }

    /** Un BORRADOR de un día anterior ya venció: se informa como PENDIENTE (R07, R08). */
    public static EstadoSesionClase estadoEfectivo(SesionClase sesion, LocalDate hoy) {
        if (sesion.getEstado() == EstadoSesionClase.BORRADOR && sesion.getFecha().isBefore(hoy)) {
            return EstadoSesionClase.PENDIENTE;
        }
        return sesion.getEstado();
    }

    private SesionClaseResponse mapear(SesionClase sesion, LocalDate hoy) {
        EstadoSesionClase estado = estadoEfectivo(sesion, hoy);
        boolean esHoy = sesion.getFecha().isEqual(hoy);
        return new SesionClaseResponse(
                sesion.getId(),
                sesion.getAsignacionCurso().getId(),
                sesion.getFecha(),
                sesion.getHoraInicio(),
                sesion.getHoraFin(),
                estado,
                esHoy && estado != EstadoSesionClase.TOMADA,
                !sesion.getFecha().isAfter(hoy),
                estado == EstadoSesionClase.TOMADA,
                sesion.getVersion());
    }

    private Map<LocalDate, List<Encuentro>> calcularEncuentros(AsignacionCurso asignacion, LocalDate hoy) {
        Optional<AnioAcademico> anio = configuracionService.buscarOpcional(
                asignacion.getSeccion().getAnioAcademico());
        if (anio.isEmpty()) {
            return Map.of();
        }
        Map<DiaSemana, List<Encuentro>> porDia = agruparEncuentros(
                horarioService.listarActivosPorAsignacion(asignacion.getId()));
        if (porDia.isEmpty()) {
            return Map.of();
        }
        Set<LocalDate> noLectivos = diaNoLectivoService.listar(anio.get().getAnio()).stream()
                .map(DiaNoLectivo::getFecha)
                .collect(Collectors.toSet());

        Map<LocalDate, List<Encuentro>> resultado = new HashMap<>();
        LocalDate desde = anio.get().getFechaInicio().isAfter(hoy) ? anio.get().getFechaInicio() : hoy;
        for (LocalDate fecha = desde; !fecha.isAfter(anio.get().getFechaFin()); fecha = fecha.plusDays(1)) {
            List<Encuentro> encuentros = porDia.get(diaSemana(fecha.getDayOfWeek()));
            if (encuentros != null && !noLectivos.contains(fecha)) {
                resultado.put(fecha, encuentros);
            }
        }
        return resultado;
    }

    /** Une los bloques del mismo día en que uno empieza cuando termina el anterior. */
    static Map<DiaSemana, List<Encuentro>> agruparEncuentros(List<Horario> horarios) {
        Map<DiaSemana, List<Encuentro>> porDia = new EnumMap<>(DiaSemana.class);
        Map<DiaSemana, List<Horario>> bloques = horarios.stream()
                .collect(Collectors.groupingBy(Horario::getDiaSemana));
        bloques.forEach((dia, delDia) -> {
            delDia.sort(Comparator.comparing(Horario::getHoraInicio));
            List<Encuentro> encuentros = new ArrayList<>();
            Encuentro actual = null;
            for (Horario bloque : delDia) {
                if (actual != null && actual.horaFin().equals(bloque.getHoraInicio())) {
                    actual = new Encuentro(actual.horario(), actual.horaInicio(), bloque.getHoraFin());
                } else {
                    if (actual != null) {
                        encuentros.add(actual);
                    }
                    actual = new Encuentro(bloque, bloque.getHoraInicio(), bloque.getHoraFin());
                }
            }
            encuentros.add(actual);
            porDia.put(dia, encuentros);
        });
        return porDia;
    }

    private SesionClase nueva(Encuentro encuentro, LocalDate fecha) {
        SesionClase sesion = new SesionClase();
        sesion.setAsignacionCurso(encuentro.horario().getAsignacionCurso());
        sesion.setHorario(encuentro.horario());
        sesion.setFecha(fecha);
        sesion.setHoraInicio(encuentro.horaInicio());
        sesion.setHoraFin(encuentro.horaFin());
        sesion.setEstado(EstadoSesionClase.PENDIENTE);
        sesion.setActivo(true);
        return sesion;
    }

    private static DiaSemana diaSemana(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> DiaSemana.LUNES;
            case TUESDAY -> DiaSemana.MARTES;
            case WEDNESDAY -> DiaSemana.MIERCOLES;
            case THURSDAY -> DiaSemana.JUEVES;
            case FRIDAY -> DiaSemana.VIERNES;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> null;
        };
    }

    /** Encuentro programado: el primer bloque del horario y el rango de horas unido. */
    record Encuentro(Horario horario, LocalTime horaInicio, LocalTime horaFin) {
    }

    private record Clave(LocalDate fecha, LocalTime horaInicio) {
    }
}
