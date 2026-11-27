// inscripcion cursos
package com.asiscontrol.service;

import com.asiscontrol.dto.academico.MisCursosDtos.CursoInscrito;
import com.asiscontrol.dto.academico.MisCursosDtos.MisCursosResponse;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoPlan;
import com.asiscontrol.dto.asistencia.PeriodoCurso;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.InscripcionCurso;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.InscripcionCursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Mis cursos" del estudiante: lee sus inscripciones de matrículas ACTIVA (año vigente) y FINALIZADA
 * (años cerrados). Las de matrículas retiradas o anuladas están inactivas y no aparecen. El docente,
 * el horario y el aula salen de la asignación de la sección, nunca de la inscripción.
 */
@Service
@Transactional(readOnly = true)
public class MisCursosService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<EstadoMatricula> CON_CURSOS = List.of(EstadoMatricula.ACTIVA, EstadoMatricula.FINALIZADA);

    private final InscripcionCursoRepository inscripciones;
    private final PlanEstudiosService planEstudiosService;
    private final ConfiguracionAcademicaService configuracion;
    private final AsignacionCursoRepository asignacionRepository;
    private final HorarioService horarioService;
    private final ModuloAccessGuard accessGuard;

    public MisCursosService(
            InscripcionCursoRepository inscripciones,
            PlanEstudiosService planEstudiosService,
            ConfiguracionAcademicaService configuracion,
            AsignacionCursoRepository asignacionRepository,
            HorarioService horarioService,
            ModuloAccessGuard accessGuard) {
        this.inscripciones = inscripciones;
        this.planEstudiosService = planEstudiosService;
        this.configuracion = configuracion;
        this.asignacionRepository = asignacionRepository;
        this.horarioService = horarioService;
        this.accessGuard = accessGuard;
    }

    public MisCursosResponse listar(PeriodoCurso periodo) {
        Map<Integer, Optional<EstadoAnioAcademico>> estados = new HashMap<>();
        Map<String, Map<Long, String>> nombresPlan = new HashMap<>();
        Collator collator = Collator.getInstance(Locale.of("es"));
        List<CursoInscrito> cursos = inscripciones.buscarDelEstudiante(accessGuard.personaIdActual(), CON_CURSOS).stream()
                .filter(inscripcion -> {
                    var estado = estados.computeIfAbsent(inscripcion.getMatricula().getAnioAcademico(),
                            anio -> configuracion.buscarOpcional(anio).map(AnioAcademico::getEstado)).orElse(null);
                    boolean vigente = estado == EstadoAnioAcademico.ACTIVO;
                    boolean pasado = estado == EstadoAnioAcademico.CERRADO;
                    return switch (periodo) {
                        case VIGENTES -> vigente;
                        case PASADOS -> pasado;
                        case TODOS -> vigente || pasado;
                    };
                })
                .map(inscripcion -> inscrito(inscripcion,
                        estados.get(inscripcion.getMatricula().getAnioAcademico()).orElse(null) == EstadoAnioAcademico.ACTIVO,
                        nombresPlan))
                .sorted(Comparator.comparing(CursoInscrito::anioAcademico).reversed()
                        .thenComparing(CursoInscrito::curso, collator))
                .toList();
        return new MisCursosResponse(cursos);
    }

    private CursoInscrito inscrito(InscripcionCurso inscripcion, boolean vigente, Map<String, Map<Long, String>> nombresPlan) {
        Seccion seccion = inscripcion.getMatricula().getSeccion();
        var curso = inscripcion.getCurso();
        String nombre = nombresPlan.computeIfAbsent(seccion.getAnioAcademico() + "-" + seccion.getGrado(),
                        clave -> planEstudiosService.cursosDelPlan(seccion.getAnioAcademico(), seccion.getGrado()).stream()
                                .collect(Collectors.toMap(CursoPlan::id, CursoPlan::nombre, (a, b) -> a)))
                .getOrDefault(curso.getId(), curso.getNombre());
        String nombreSeccion = seccion.getGrado() + "° Grado " + seccion.getNombre();
        AsignacionCurso asignacion = asignacionRepository
                .findByCursoIdAndSeccionIdAndActivoTrue(curso.getId(), seccion.getId())
                .orElse(null);
        if (asignacion == null) {
            return new CursoInscrito(curso.getId(), nombre, null, null, null,
                    seccion.getAnioAcademico(), nombreSeccion, vigente);
        }
        List<Horario> bloques = horarioService.listarActivosPorAsignacion(asignacion.getId()).stream()
                .sorted(Comparator.comparing(Horario::getDiaSemana).thenComparing(Horario::getHoraInicio))
                .toList();
        String horario = bloques.isEmpty() ? null : bloques.stream()
                .map(b -> dia(b.getDiaSemana()) + " " + b.getHoraInicio().format(HORA) + "–" + b.getHoraFin().format(HORA))
                .collect(Collectors.joining(" · "));
        String aula = bloques.isEmpty() ? null : bloques.stream()
                .map(b -> b.getAula().getNombre())
                .distinct()
                .collect(Collectors.joining(", "));
        return new CursoInscrito(curso.getId(), nombre, asignacion.getDocente().getPersona().nombreCompleto(),
                horario, aula, seccion.getAnioAcademico(), nombreSeccion, vigente);
    }

    private static String dia(DiaSemana dia) {
        return switch (dia) {
            case LUNES -> "Lun";
            case MARTES -> "Mar";
            case MIERCOLES -> "Mié";
            case JUEVES -> "Jue";
            case VIERNES -> "Vie";
            case SABADO -> "Sáb";
        };
    }
}
