package com.asiscontrol.service;


import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.CorreccionCalificacionRepository;
import com.asiscontrol.repository.CalificacionRepository;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Calificacion;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.CompetenciaDto;
import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.CursoCompetenciaRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvaluacionService {

    private final EvaluacionRepository evaluacionRepository;
    private final AsignacionCursoRepository asignacionCursoRepository;
    private final CompetenciaRepository competenciaRepository;
    private final CursoCompetenciaRepository cursoCompetenciaRepository;
    private final CalificacionRepository calificacionRepository;
    private final CorreccionCalificacionRepository correccionRepository;
    private final MatriculaRepository matriculaRepository;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public EvaluacionService(
            EvaluacionRepository evaluacionRepository,
            AsignacionCursoRepository asignacionCursoRepository,
            CompetenciaRepository competenciaRepository,
            CursoCompetenciaRepository cursoCompetenciaRepository,
            CalificacionRepository calificacionRepository,
            CorreccionCalificacionRepository correccionRepository,
            MatriculaRepository matriculaRepository,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria
    ) {
        this.calificacionRepository = calificacionRepository;
        this.correccionRepository = correccionRepository;
        this.matriculaRepository = matriculaRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.asignacionCursoRepository = asignacionCursoRepository;
        this.competenciaRepository = competenciaRepository;
        this.cursoCompetenciaRepository = cursoCompetenciaRepository;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<EvaluacionDto.Response> listar(
            EstadoEvaluacion estado,
            Long idAsignacionCurso,
            com.asiscontrol.entity.enums.PeriodoEvaluacion periodo,
            Long idCurso,
            Long idSeccion,
            String busqueda,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        String filtro = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        return evaluacionRepository.buscar(
                        estado,
                        idAsignacionCurso,
                        periodo,
                        idCurso,
                        idSeccion,
                        filtro,
                        personaDocenteId,
                        personaAlumnoId,
                        personaApoderadoId,
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EvaluacionDto.Response obtener(Long id) {
        return toResponse(buscarActiva(id));
    }

    /** US-18: alumnos matriculados en la seccion con sus niveles vigentes en esta evaluacion. */
    @Transactional(readOnly = true)
    public EvaluacionDto.PlanillaResponse planilla(Long id) {
        Evaluacion evaluacion = buscarActiva(id);
        List<Calificacion> calificaciones = calificacionRepository.findAllByEvaluacionIdAndActivoTrue(id);
        Set<Long> corregidas = calificaciones.isEmpty()
                ? Set.of()
                : new HashSet<>(correccionRepository.buscarCalificacionesCorregidas(
                        calificaciones.stream().map(Calificacion::getId).toList()));
        Map<Long, List<EvaluacionDto.PlanillaNota>> notas = new HashMap<>();
        for (Calificacion calificacion : calificaciones) {
            notas.computeIfAbsent(calificacion.getAlumno().getId(), alumno -> new ArrayList<>())
                    .add(new EvaluacionDto.PlanillaNota(
                            calificacion.getId(),
                            calificacion.getCompetencia().getId(),
                            calificacion.getNivelLogro(),
                            calificacion.getVersion(),
                            corregidas.contains(calificacion.getId())));
        }
        List<EvaluacionDto.PlanillaAlumno> alumnos = matriculaRepository
                .buscarAlumnosActivosDeSeccion(evaluacion.getAsignacionCurso().getSeccion().getId())
                .stream()
                .map(alumno -> new EvaluacionDto.PlanillaAlumno(
                        alumno.getId(),
                        alumno.getCodigoAlumno(),
                        nombreEnPlanilla(alumno.getPersona()),
                        notas.getOrDefault(alumno.getId(), List.of())))
                .toList();
        return new EvaluacionDto.PlanillaResponse(toResponse(evaluacion), alumnos);
    }

    private String nombreEnPlanilla(Persona persona) {
        String apellidos = (persona.getApellidoPaterno() + " "
                + (persona.getApellidoMaterno() == null ? "" : persona.getApellidoMaterno())).trim();
        return apellidos + ", " + persona.getNombres();
    }

    @Transactional
    public EvaluacionDto.Response crear(EvaluacionDto.Request request) {
        Evaluacion evaluacion = new Evaluacion();
        aplicarDatosEditables(evaluacion, request);
        evaluacion.setEstado(EstadoEvaluacion.BORRADOR);
        Evaluacion saved = evaluacionRepository.save(evaluacion);
        auditoria.registrar("CREAR_EVALUACION", ModuloAuditoria.ACADEMICO, "Evaluacion",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return toResponse(saved);
    }

    @Transactional
    public EvaluacionDto.Response actualizar(Long id, EvaluacionDto.Request request) {
        Evaluacion evaluacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        if (evaluacion.getEstado() != EstadoEvaluacion.BORRADOR) {
            throw new BusinessRuleException(
                    "EVALUACION_NO_EDITABLE",
                    "Solo una evaluacion en borrador puede modificar sus datos principales"
            );
        }
        String anterior = describir(evaluacion);
        aplicarDatosEditables(evaluacion, request);
        Evaluacion saved = evaluacionRepository.save(evaluacion);
        auditoria.registrar("ACTUALIZAR_EVALUACION", ModuloAuditoria.ACADEMICO, "Evaluacion",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return toResponse(saved);
    }

    @Transactional
    public EvaluacionDto.Response cambiarEstado(Long id, EstadoEvaluacion estadoDestino) {
        Evaluacion evaluacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        EstadoEvaluacion estadoActual = evaluacion.getEstado();
        if (estadoActual == estadoDestino) {
            return toResponse(evaluacion);
        }
        validarTransicion(evaluacion, estadoDestino);

        evaluacion.setEstado(estadoDestino);
        if (estadoDestino == EstadoEvaluacion.PUBLICADA) {
            evaluacion.setFechaPublicacion(Instant.now());
            evaluacion.setFechaCierre(null);
        } else if (estadoDestino == EstadoEvaluacion.CERRADA) {
            evaluacion.setFechaCierre(Instant.now());
        }
        Evaluacion saved = evaluacionRepository.save(evaluacion);
        auditoria.registrar(accionEstado(estadoDestino), ModuloAuditoria.ACADEMICO, "Evaluacion",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "estado=" + estadoActual, "estado=" + estadoDestino);
        if (estadoDestino == EstadoEvaluacion.PUBLICADA) {
            auditarNivelesPublicados(saved);
        }
        return toResponse(saved);
    }

    /** US-18: la auditoria conserva actor, nivel, competencia, estudiante y fecha de cada nivel. */
    private void auditarNivelesPublicados(Evaluacion evaluacion) {
        for (Calificacion calificacion : calificacionRepository.findAllByEvaluacionIdAndActivoTrue(evaluacion.getId())) {
            auditoria.registrar("PUBLICAR_CALIFICACION", ModuloAuditoria.ACADEMICO, "Calificacion",
                    calificacion.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                    "nivelLogro=" + calificacion.getNivelLogro()
                            + ",competencia=" + calificacion.getCompetencia().getNombre()
                            + ",alumnoId=" + calificacion.getAlumno().getId()
                            + ",evaluacionId=" + evaluacion.getId()
                            + ",fechaPublicacion=" + evaluacion.getFechaPublicacion());
        }
    }

    /** US-18: solo se publica una planilla completa: todos los alumnos en todas las competencias. */
    private void exigirPlanillaCompleta(Evaluacion evaluacion) {
        List<Alumno> alumnos = matriculaRepository
                .buscarAlumnosActivosDeSeccion(evaluacion.getAsignacionCurso().getSeccion().getId());
        if (alumnos.isEmpty()) {
            throw new BusinessRuleException(
                    "EVALUACION_SIN_ESTUDIANTES",
                    "La seccion no tiene estudiantes matriculados para evaluar"
            );
        }
        Set<String> registradas = calificacionRepository.findAllByEvaluacionIdAndActivoTrue(evaluacion.getId())
                .stream()
                .map(item -> item.getAlumno().getId() + ":" + item.getCompetencia().getId())
                .collect(Collectors.toSet());
        long faltantes = alumnos.stream()
                .flatMap(alumno -> evaluacion.getCompetencias().stream()
                        .map(competencia -> alumno.getId() + ":" + competencia.getId()))
                .filter(clave -> !registradas.contains(clave))
                .count();
        if (faltantes > 0) {
            throw new BusinessRuleException(
                    "EVALUACION_INCOMPLETA",
                    "Faltan " + faltantes + " niveles por registrar antes de publicar la evaluacion"
            );
        }
    }

    @Transactional
    public void eliminar(Long id) {
        Evaluacion evaluacion = buscarActiva(id);
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        if (evaluacion.getEstado() != EstadoEvaluacion.BORRADOR
                && evaluacion.getEstado() != EstadoEvaluacion.ANULADA) {
            throw new BusinessRuleException(
                    "EVALUACION_NO_ELIMINABLE",
                    "Solo se puede eliminar logicamente una evaluacion en borrador o anulada"
            );
        }
        evaluacion.setActivo(false);
        evaluacionRepository.save(evaluacion);
        auditoria.registrar("ELIMINAR_EVALUACION", ModuloAuditoria.ACADEMICO, "Evaluacion",
                id.toString(), ResultadoAuditoria.EXITOSO, null, describir(evaluacion), null);
    }

    private String accionEstado(EstadoEvaluacion destino) {
        return switch (destino) {
            case PUBLICADA -> "PUBLICAR_EVALUACION";
            case CERRADA -> "CERRAR_EVALUACION";
            case ANULADA -> "ANULAR_EVALUACION";
            case BORRADOR -> "CAMBIAR_ESTADO_EVALUACION";
        };
    }

    private String describir(Evaluacion evaluacion) {
        String competencias = evaluacion.getCompetencias().stream()
                .map(competencia -> competencia.getId().toString())
                .collect(Collectors.joining("|"));
        return "titulo=" + evaluacion.getTitulo()
                + ",asignacionCursoId=" + evaluacion.getAsignacionCurso().getId()
                + ",estado=" + evaluacion.getEstado()
                + ",ponderacion=" + evaluacion.getPonderacionPorcentaje()
                + ",competencias=" + competencias;
    }

    private void aplicarDatosEditables(Evaluacion evaluacion, EvaluacionDto.Request request) {
        AsignacionCurso asignacion = asignacionCursoRepository.findByIdAndActivoTrue(request.idAsignacionCurso())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASIGNACION_CURSO_NO_ENCONTRADA",
                        "No se encontro la asignacion de curso con id " + request.idAsignacionCurso()
                ));
        accessGuard.verificarAsignacionDocente(asignacion);
        Set<Long> idsUnicos = new LinkedHashSet<>(request.idsCompetencia());
        List<Competencia> competencias = competenciaRepository.findAllByIdInAndActivoTrue(idsUnicos);
        if (competencias.size() != idsUnicos.size()) {
            throw new ResourceNotFoundException(
                    "COMPETENCIA_NO_ENCONTRADA",
                    "Una o mas competencias no existen o estan inactivas"
            );
        }

        validarCompetenciasDisponibles(asignacion, idsUnicos);

        evaluacion.setAsignacionCurso(asignacion);
        evaluacion.setTitulo(request.titulo().trim());
        evaluacion.setDescripcion(limpiar(request.descripcion()));
        evaluacion.setTipoEvaluacion(request.tipoEvaluacion());
        evaluacion.setPeriodoEvaluacion(request.periodoEvaluacion());
        evaluacion.setFechaEvaluacion(request.fechaEvaluacion());
        evaluacion.setPonderacionPorcentaje(request.ponderacionPorcentaje());
        evaluacion.setCompetencias(new LinkedHashSet<>(competencias));
    }

    private void validarCompetenciasDisponibles(AsignacionCurso asignacion, Set<Long> idsCompetencia) {
        Set<Long> disponibles = cursoCompetenciaRepository
                .buscarIdsCompetenciasDisponiblesPorCurso(asignacion.getCurso().getId());
        if (!disponibles.containsAll(idsCompetencia)) {
            throw new BusinessRuleException(
                    "COMPETENCIA_NO_DISPONIBLE_PARA_CURSO",
                    "Todas las competencias deben estar asociadas al curso y tanto la relacion "
                            + "como el curso y la competencia deben estar activos"
            );
        }
    }

    private void validarTransicion(Evaluacion evaluacion, EstadoEvaluacion destino) {
        EstadoEvaluacion actual = evaluacion.getEstado();
        boolean permitida = switch (actual) {
            case BORRADOR -> destino == EstadoEvaluacion.PUBLICADA || destino == EstadoEvaluacion.ANULADA;
            case PUBLICADA -> destino == EstadoEvaluacion.CERRADA || destino == EstadoEvaluacion.ANULADA;
            case CERRADA, ANULADA -> false;
        };
        if (!permitida) {
            throw new BusinessRuleException(
                    "TRANSICION_EVALUACION_INVALIDA",
                    "No se puede cambiar una evaluacion de " + actual + " a " + destino
            );
        }
        if (destino == EstadoEvaluacion.PUBLICADA && evaluacion.getCompetencias().isEmpty()) {
            throw new BusinessRuleException(
                    "EVALUACION_SIN_COMPETENCIAS",
                    "La evaluacion debe incluir al menos una competencia antes de publicarse"
            );
        }
        if (destino == EstadoEvaluacion.PUBLICADA) {
            Set<Long> idsCompetencia = evaluacion.getCompetencias().stream()
                    .map(Competencia::getId)
                    .collect(Collectors.toSet());
            validarCompetenciasDisponibles(evaluacion.getAsignacionCurso(), idsCompetencia);
            exigirPlanillaCompleta(evaluacion);
        }
    }

    private Evaluacion buscarActiva(Long id) {
        return evaluacionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVALUACION_NO_ENCONTRADA",
                        "No se encontro la evaluacion con id " + id
                ));
    }

    private EvaluacionDto.Response toResponse(Evaluacion evaluacion) {
        Set<CompetenciaDto.Response> competencias = evaluacion.getCompetencias().stream()
                .map(competencia -> new CompetenciaDto.Response(
                        competencia.getId(),
                        competencia.getCodigo(),
                        competencia.getNombre(),
                        competencia.getDescripcion(),
                        competencia.isActivo(),
                        competencia.getFechaCreacion(),
                        competencia.getFechaActualizacion()
                ))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new EvaluacionDto.Response(
                evaluacion.getId(),
                evaluacion.getAsignacionCurso().getId(),
                evaluacion.getTitulo(),
                evaluacion.getDescripcion(),
                evaluacion.getTipoEvaluacion(),
                evaluacion.getPeriodoEvaluacion(),
                evaluacion.getEstado(),
                evaluacion.getFechaEvaluacion(),
                evaluacion.getPonderacionPorcentaje(),
                competencias,
                evaluacion.getFechaPublicacion(),
                evaluacion.getFechaCierre(),
                evaluacion.getFechaCreacion(),
                evaluacion.getFechaActualizacion(),
                evaluacion.getAsignacionCurso().getCurso().getId(),
                evaluacion.getAsignacionCurso().getCurso().getNombre(),
                evaluacion.getAsignacionCurso().getSeccion().getId(),
                AsignacionCursoService.etiquetaSeccion(evaluacion.getAsignacionCurso().getSeccion()),
                evaluacion.getAsignacionCurso().getSeccion().getAnioAcademico()
        );
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
