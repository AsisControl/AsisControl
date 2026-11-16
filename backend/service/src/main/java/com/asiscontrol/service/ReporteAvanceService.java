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
    private final EvaluacionRepository evaluaciones;
    private final ModuloAccessGuard acceso;

    public ReporteAvanceService(AlumnoRepository alumnos, AsignacionCursoRepository asignaciones,
            PeriodoAcademicoRepository periodos, MatriculaRepository matriculas,
            CalificacionRepository calificaciones, ModuloAccessGuard acceso, EvaluacionRepository evaluaciones) {
        this.alumnos = alumnos;
        this.asignaciones = asignaciones;
        this.periodos = periodos;
        this.matriculas = matriculas;
        this.calificaciones = calificaciones;
        this.acceso = acceso;
        this.evaluaciones = evaluaciones;
    }

    @Transactional(readOnly = true)
    public java.util.List<com.asiscontrol.dto.InformeDisponibleDto> disponibles() {
        var opciones = new java.util.LinkedHashMap<String, com.asiscontrol.dto.InformeDisponibleDto>();
        var porAnio = new java.util.HashMap<Integer, java.util.List<com.asiscontrol.entity.planificacion.PeriodoAcademico>>();
        for (var fila : evaluaciones.informesPermitidos(acceso.restriccionDocentePersonaId(),
                acceso.restriccionAlumnoPersonaId(), acceso.restriccionApoderadoPersonaId())) {
            var alumno = (Alumno) fila[0];
            var evaluacion = (Evaluacion) fila[1];
            var asignacion = evaluacion.getAsignacionCurso();
            int anio = asignacion.getSeccion().getAnioAcademico();
            var lista = porAnio.computeIfAbsent(anio, periodos::findByAnioAcademicoAnioOrderByOrden);
            for (var periodo : lista) {
                var fecha = evaluacion.getFechaEvaluacion();
                if (!periodo.isActivo() || !periodo.getAnioAcademico().isActivo()
                        || fecha.isBefore(periodo.getFechaInicio()) || fecha.isAfter(periodo.getFechaFin())) continue;
                String clave = alumno.getId() + ":" + asignacion.getId() + ":" + periodo.getId();
                opciones.putIfAbsent(clave, new com.asiscontrol.dto.InformeDisponibleDto(alumno.getId(),
                        nombre(alumno.getPersona()), asignacion.getId(), asignacion.getCurso().getNombre(),
                        anio, periodo.getId(), periodo.getNombre()));
            }
        }
        return java.util.List.copyOf(opciones.values());
    }

    @Transactional(readOnly = true)
    public ReporteAvanceDto preparar(Long idAlumno, Long idAsignacionCurso, Long idPeriodoAcademico) {
        var asignacion = asignaciones.findByIdAndActivoTrue(idAsignacionCurso)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "Curso no encontrado"));
        acceso.verificarConsultaInforme(asignacion, idAlumno);
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
        // Las filas parten de las evaluaciones y competencias, aunque no exista una calificacion.
        var notas = calificaciones.buscarParaConsolidado(idAlumno, idAsignacionCurso).stream()
                .collect(Collectors.toMap(c -> c.getEvaluacion().getId() + ":" + c.getCompetencia().getId(), c -> c));
        var filas = evaluaciones.publicadasParaInforme(idAsignacionCurso, periodo.getFechaInicio(), periodo.getFechaFin())
                .stream().flatMap(e -> e.getCompetencias().stream().map(competencia -> {
                    var nota = notas.get(e.getId() + ":" + competencia.getId());
                    return new ReporteAvanceDto.Fila(competencia.getNombre(), e.getTitulo(),
                            FECHA.format(e.getFechaEvaluacion()), nota == null ? "Sin registrar" : nota.getNivelLogro().name(),
                            e.getPonderacionPorcentaje().stripTrailingZeros().toPlainString() + " %",
                            nota == null || nota.getObservacion() == null || nota.getObservacion().isBlank()
                                    ? "Sin observaciones" : nota.getObservacion());
                }))
                .sorted(Comparator.comparing(ReporteAvanceDto.Fila::competencia))
                .toList();
        if (filas.isEmpty()) {
            throw new BusinessRuleException("REPORTE_SIN_CALIFICACIONES", "No hay evaluaciones publicadas en el periodo seleccionado");
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
