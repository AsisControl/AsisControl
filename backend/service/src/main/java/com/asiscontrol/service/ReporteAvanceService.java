package com.asiscontrol.service;

import com.asiscontrol.dto.ReporteAvanceDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.*;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReporteAvanceService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final AlumnoRepository alumnos;
    private final AsignacionCursoRepository asignaciones;
    private final PeriodoAcademicoRepository periodos;
    private final MatriculaRepository matriculas;
    private final CalificacionRepository calificaciones;
    private final ModuloAccessGuard acceso;

    public ReporteAvanceService(AlumnoRepository alumnos, AsignacionCursoRepository asignaciones,
            PeriodoAcademicoRepository periodos, MatriculaRepository matriculas,
            CalificacionRepository calificaciones, ModuloAccessGuard acceso) {
        this.alumnos = alumnos;
        this.asignaciones = asignaciones;
        this.periodos = periodos;
        this.matriculas = matriculas;
        this.calificaciones = calificaciones;
        this.acceso = acceso;
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto preparar(Long idAlumno, Long idAsignacionCurso, Long idPeriodoAcademico) {
        var asignacion = asignaciones.findByIdAndActivoTrue(idAsignacionCurso)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "Curso no encontrado"));
        acceso.verificarAsignacionDocente(asignacion);
        var alumno = alumnos.findByIdAndActivoTrue(idAlumno)
                .orElseThrow(() -> new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "Alumno no encontrado"));
        var periodo = periodos.findById(idPeriodoAcademico).filter(p -> p.isActivo() && p.getAnioAcademico().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "Periodo no encontrado"));
        var seccion = asignacion.getSeccion();
        if (periodo.getAnioAcademico().getAnio() != seccion.getAnioAcademico()) {
            throw new BusinessRuleException("PERIODO_CURSO_INVALIDO", "El periodo no pertenece al año del curso");
        }
        boolean pertenece = Stream.of(EstadoMatricula.ACTIVA, EstadoMatricula.FINALIZADA)
                .anyMatch(estado -> matriculas.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(
                        idAlumno, seccion.getId(), estado));
        if (!pertenece) {
            throw new BusinessRuleException("ALUMNO_CURSO_INVALIDO", "El alumno no tiene matricula vigente o finalizada en esta seccion");
        }
        // La consulta existente incluye exclusivamente evaluaciones publicadas o cerradas.
        // El periodo academico se aplica por fecha, no por el tipo BIMESTRAL/MENSUAL.
        var filas = calificaciones.buscarParaConsolidado(idAlumno, idAsignacionCurso).stream()
                .filter(c -> !c.getEvaluacion().getFechaEvaluacion().isBefore(periodo.getFechaInicio())
                        && !c.getEvaluacion().getFechaEvaluacion().isAfter(periodo.getFechaFin()))
                .sorted(Comparator.comparing((Calificacion c) -> c.getCompetencia().getNombre())
                        .thenComparing(c -> c.getEvaluacion().getFechaEvaluacion())
                        .thenComparing(Calificacion::getId))
                .map(c -> new ReporteAvanceDto.Fila(c.getCompetencia().getNombre(),
                        c.getEvaluacion().getTitulo(), FECHA.format(c.getEvaluacion().getFechaEvaluacion()),
                        c.getNivelLogro().name(),
                        c.getEvaluacion().getPonderacionPorcentaje().stripTrailingZeros().toPlainString() + " %",
                        c.getObservacion() == null || c.getObservacion().isBlank() ? "Sin observaciones" : c.getObservacion()))
                .toList();
        if (filas.isEmpty()) {
            throw new BusinessRuleException("REPORTE_SIN_CALIFICACIONES", "No hay calificaciones publicadas en el periodo seleccionado");
        }
        return new ReporteAvanceDto(nombre(alumno.getPersona()), alumno.getCodigoAlumno(),
                asignacion.getCurso().getNombre(), nombre(asignacion.getDocente().getPersona()),
                seccion.getGrado(), seccion.getNombre(), seccion.getAnioAcademico(), periodo.getNombre(),
                FECHA.format(periodo.getFechaInicio()) + " - " + FECHA.format(periodo.getFechaFin()), Instant.now(), filas);
    }

    private String nombre(Persona persona) {
        return Stream.of(persona.getNombres(), persona.getApellidoPaterno(), persona.getApellidoMaterno())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" "));
    }
}
