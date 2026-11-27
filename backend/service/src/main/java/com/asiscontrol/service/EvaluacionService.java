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
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CompetenciaPlan;
import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.ReglaCalificacion;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.FormFieldException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvaluacionService {
    private final FlujoEntregaAcademica flujoEntrega;

    private final EvaluacionRepository evaluacionRepository;
    private final AsignacionCursoRepository asignacionCursoRepository;
    private final CompetenciaRepository competenciaRepository;
    private final PlanEstudiosService planes;
    private final ReglaCalificacionService reglas;
    private final EscalaInstitucionalService escalas;
    private final CalificacionRepository calificacionRepository;
    private final CorreccionCalificacionRepository correccionRepository;
    private final MatriculaRepository matriculaRepository;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final AvisosAcademicos avisos;
    private final com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository periodos;

    public EvaluacionService(
            EvaluacionRepository evaluacionRepository,
            AsignacionCursoRepository asignacionCursoRepository,
            CompetenciaRepository competenciaRepository,
            PlanEstudiosService planes,
            ReglaCalificacionService reglas,
            EscalaInstitucionalService escalas,
            CalificacionRepository calificacionRepository,
            CorreccionCalificacionRepository correccionRepository,
            MatriculaRepository matriculaRepository,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria,
            AvisosAcademicos avisos,
            com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository periodos,
            FlujoEntregaAcademica flujoEntrega
    ) {
        this.flujoEntrega = flujoEntrega;
        this.calificacionRepository = calificacionRepository;
        this.correccionRepository = correccionRepository;
        this.matriculaRepository = matriculaRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.asignacionCursoRepository = asignacionCursoRepository;
        this.competenciaRepository = competenciaRepository;
        this.planes = planes;
        this.reglas = reglas;
        this.escalas = escalas;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.periodos = periodos;
    }



    @Transactional(readOnly = true)
    public List<EvaluacionDto.PeriodoInforme> periodosPublicables(Long idAsignacionCurso) {
        var asignacion = asignacionCursoRepository.findByIdAndActivoTrue(idAsignacionCurso)
                .orElseThrow(() -> new ResourceNotFoundException("CURSO_NO_ENCONTRADO", "Curso no encontrado"));
        accessGuard.verificarAsignacionDocente(asignacion);
        return periodos.findByAnioAcademicoAnioOrderByOrden(asignacion.getSeccion().getAnioAcademico()).stream()
                .filter(p -> p.isActivo() && p.getAnioAcademico().isActivo())
                .map(p -> new EvaluacionDto.PeriodoInforme(p.getId(), p.getNombre(), p.getFechaInicio(), p.getFechaFin())).toList();
    }

    @Transactional(readOnly = true)
    public List<CompetenciaPlan> competenciasDisponibles(Long idAsignacionCurso) {
        var asignacion = asignacionCursoRepository.findByIdAndActivoTrue(idAsignacionCurso)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_CURSO_NO_ENCONTRADA", "No se encontró la asignación del curso"));
        accessGuard.verificarAsignacionDocente(asignacion);
        return planes.competenciasDeAsignacion(asignacion).stream()
                .map(competencia -> new CompetenciaPlan(competencia.getId(), competencia.getCodigo(), competencia.getNombre(), true))
                .toList();
    }

    /** Publica el periodo en una transaccion: las restricciones se validan antes de cambiar estados. */
    @Transactional
    public int publicarPeriodo(Long idAsignacionCurso, Long idPeriodoAcademico) {
        var asignacion = asignacionCursoRepository.findByIdAndActivoTrue(idAsignacionCurso)
                .orElseThrow(() -> new ResourceNotFoundException("CURSO_NO_ENCONTRADO", "Curso no encontrado"));
        accessGuard.verificarAsignacionDocente(asignacion);
        var periodo = periodos.findById(idPeriodoAcademico)
                .filter(p -> p.isActivo() && p.getAnioAcademico().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "Periodo no encontrado"));
        if (periodo.getAnioAcademico().getAnio() != asignacion.getSeccion().getAnioAcademico()) {
            throw new BusinessRuleException("PERIODO_CURSO_INVALIDO", "El periodo no pertenece al año del curso");
        }
        var borradores = evaluacionRepository.borradoresParaPublicar(idAsignacionCurso,
                periodo.getFechaInicio(), periodo.getFechaFin()).stream()
                .filter(e -> !e.isEntregaPorConfigurar() && flujoEntrega.tareaVinculada(e) == null).toList();
        validarPublicacion(borradores);
        for (var evaluacion : borradores) cambiarEstado(evaluacion.getId(), EstadoEvaluacion.PUBLICADA);
        return borradores.size();
    }

    void validarPublicacion(List<Evaluacion> borradores) {
        for (var evaluacion : borradores) validarTransicion(evaluacion, EstadoEvaluacion.PUBLICADA);
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
            boolean vinculables,
            Long tareaActual,
            Pageable pageable
    ) {
        String filtro = busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
        var reglasPorAsignacion = new HashMap<Long, java.util.Optional<com.asiscontrol.entity.ReglaCalificacion>>();
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
                        vinculables, tareaActual,
                        pageable)
                .map(evaluacion -> toResponse(evaluacion, reglasPorAsignacion.computeIfAbsent(
                        evaluacion.getAsignacionCurso().getId(),
                        id -> reglas.vigente(evaluacion.getAsignacionCurso())).orElse(null)));
    }

    public record EvaluacionAcademica(EvaluacionDto.Response evaluacion, long registradas, long esperadas) {}

    @Transactional(readOnly = true)
    public Page<EvaluacionAcademica> consultarAcademicas(int anio, Long periodo, int grado, Long seccion,
            Long curso, Pageable pageable) {
        var calendario = periodos.findById(periodo)
                .filter(p -> p.getAnioAcademico().getAnio() == anio)
                .orElseThrow(() -> new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "Período no encontrado para el año"));
        var resultados = evaluacionRepository.consultarAvanceAcademico(anio, grado, seccion, curso,
                calendario.getFechaInicio(), calendario.getFechaFin(), pageable);
        if (resultados.isEmpty()) return new org.springframework.data.domain.PageImpl<>(List.of(), pageable, resultados.getTotalElements());
        var alumnos = matriculaRepository.contarAlumnosEnPlanilla(seccion);
        var regla = reglas.vigente(resultados.getContent().getFirst().getEvaluacion().getAsignacionCurso()).orElse(null);
        return resultados.map(row -> {
            var e = row.getEvaluacion();
            return new EvaluacionAcademica(toResponse(e, regla), row.getRegistradas(), alumnos * e.getCompetencias().size());
        });
    }

    @Transactional(readOnly = true)
    public EvaluacionDto.Response obtener(Long id) {
        return toResponse(buscarActiva(id));
    }

    /** US-18: alumnos matriculados en la seccion con sus niveles vigentes en esta evaluacion. */
    @Transactional(readOnly = true)
    public EvaluacionDto.PlanillaResponse planilla(Long id) {
        return planilla(id, null);
    }

    @Transactional(readOnly = true)
    public EvaluacionDto.PlanillaResponse planilla(Long id, Long idAlumno) {
        Evaluacion evaluacion = buscarActiva(id);
        List<Calificacion> calificaciones = idAlumno == null ? calificacionRepository.findAllByEvaluacionIdAndActivoTrue(id)
                : calificacionRepository.findAllByEvaluacionIdAndAlumnoIdAndActivoTrue(id, idAlumno);
        var escala = evaluacion.getEstado() == EstadoEvaluacion.BORRADOR
                ? escalas.vigente().orElse(null) : evaluacion.getEscalaInstitucional();
        var regla = reglas.vigente(evaluacion.getAsignacionCurso()).orElse(null);
        var componente = regla == null || !evaluacion.isCuentaResultado() ? null : regla.getComponentes().get(evaluacion.getTipoEvaluacion());
        boolean dinamica = regla != null && regla.getFormula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1;
        var pesosDinamicos = dinamica ? reglas.pesosDinamicos(evaluacion, regla) : Map.<Long, BigDecimal>of();
        record Celda(Long alumno, Long competencia) {}
        var cantidades = new HashMap<Celda, Long>();
        var competenciasEvaluadas = evaluacion.getCompetencias().stream().map(Competencia::getId).collect(Collectors.toSet());
        if (!dinamica && componente != null && !calificaciones.isEmpty()
                && (evaluacion.getEstado() == EstadoEvaluacion.PUBLICADA || evaluacion.getEstado() == EstadoEvaluacion.CERRADA)) {
            calificacionRepository.contarPorComponente(evaluacion.getAsignacionCurso().getId(), evaluacion.getTipoEvaluacion())
                    .forEach(fila -> cantidades.put(new Celda(fila.getAlumno(), fila.getCompetencia()), fila.getCantidad()));
        }
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
                            escala == null || calificacion.getNotaNumerica() == null ? calificacion.getNivelLogro()
                                    : EscalaInstitucionalService.nivel(escala, calificacion.getNotaNumerica()),
                            calificacion.getObservacion(),
                            calificacion.getVersion(),
                            corregidas.contains(calificacion.getId()),
                            dinamica ? pesosDinamicos.get(calificacion.getCompetencia().getId())
                                    : componente == null || calificacion.getNivelLogro() == null
                                    || !competenciasEvaluadas.contains(calificacion.getCompetencia().getId()) ? null
                                    : ReglaCalificacionService.pesoEfectivo(componente.getPeso(), cantidades.getOrDefault(
                                            new Celda(calificacion.getAlumno().getId(), calificacion.getCompetencia().getId()), 0L)),
                            calificacion.getNotaNumerica()));
        }
        List<EvaluacionDto.PlanillaAlumno> alumnos = matriculaRepository
                .buscarAlumnosActivosDeSeccion(evaluacion.getAsignacionCurso().getSeccion().getId())
                .stream()
                .filter(alumno -> idAlumno == null || idAlumno.equals(alumno.getId()))
                .map(alumno -> new EvaluacionDto.PlanillaAlumno(
                        alumno.getId(),
                        alumno.getCodigoAlumno(),
                        nombreEnPlanilla(alumno.getPersona()),
                        notas.getOrDefault(alumno.getId(), List.of())))
                .toList();
        return new EvaluacionDto.PlanillaResponse(toResponse(evaluacion, regla), alumnos,
                escala == null ? null : escala.getNumeroVersion());
    }

    private String nombreEnPlanilla(Persona persona) {
        String apellidos = (persona.getApellidoPaterno() + " "
                + (persona.getApellidoMaterno() == null ? "" : persona.getApellidoMaterno())).trim();
        return apellidos + ", " + persona.getNombres();
    }

    @Transactional
    public EvaluacionDto.Response crear(EvaluacionDto.Request request) {
        return crear(request, true);
    }

    EvaluacionDto.Response crearSinPeso(EvaluacionDto.Request request) {
        return crear(request, false);
    }

    private EvaluacionDto.Response crear(EvaluacionDto.Request request, boolean cuentaResultado) {
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.setModeloCualitativo(true);
        evaluacion.setEntregaPorConfigurar(Boolean.TRUE.equals(request.permitirEntrega()));
        evaluacion.setCuentaResultado(cuentaResultado);
        evaluacion.setPonderacionPorcentaje(java.math.BigDecimal.valueOf(100));
        aplicarDatosEditables(evaluacion, request);
        evaluacion.setEstado(EstadoEvaluacion.BORRADOR);
        Evaluacion saved = evaluacionRepository.save(evaluacion);
        auditoria.registrar("CREAR_EVALUACION", ModuloAuditoria.ACADEMICO, "Evaluacion",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return toResponse(saved);
    }

    @Transactional
    public EvaluacionDto.Response actualizar(Long id, EvaluacionDto.Request request) {
        Evaluacion evaluacion = evaluacionRepository.buscarActivaParaEscritura(id)
                .orElseThrow(() -> new ResourceNotFoundException("EVALUACION_NO_ENCONTRADA", "No se encontró la evaluación."));
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        flujoEntrega.verificar(evaluacion, null);
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
        return cambiarEstado(id, estadoDestino, null);
    }

    EvaluacionDto.Response cambiarEstado(Long id, EstadoEvaluacion estadoDestino, Long tarea) {
        Evaluacion evaluacion = evaluacionRepository.buscarActivaParaEscritura(id)
                .orElseThrow(() -> new ResourceNotFoundException("EVALUACION_NO_ENCONTRADA", "No se encontró la evaluación."));
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        flujoEntrega.verificar(evaluacion, tarea);
        EstadoEvaluacion estadoActual = evaluacion.getEstado();
        if (estadoActual == estadoDestino) {
            return toResponse(evaluacion);
        }
        validarTransicion(evaluacion, estadoDestino);

        evaluacion.setEstado(estadoDestino);
        if (estadoDestino == EstadoEvaluacion.PUBLICADA) {
            fijarEscala(evaluacion);
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

    private void fijarEscala(Evaluacion evaluacion) {
        var notas = calificacionRepository.findAllByEvaluacionIdAndActivoTrue(evaluacion.getId()).stream()
                .filter(nota -> nota.getNotaNumerica() != null).toList();
        if (notas.isEmpty()) return;
        var escala = escalas.exigirVigente();
        evaluacion.setEscalaInstitucional(escala);
        notas.forEach(nota -> nota.setNivelLogro(EscalaInstitucionalService.nivel(escala, nota.getNotaNumerica())));
    }

    /** US-18: la auditoria conserva actor, nivel, competencia, estudiante y fecha de cada nivel. */
    private void auditarNivelesPublicados(Evaluacion evaluacion) {
        var notificados = new java.util.HashSet<Long>();
        for (Calificacion calificacion : calificacionRepository.findAllByEvaluacionIdAndActivoTrue(evaluacion.getId())) {
            if (notificados.add(calificacion.getAlumno().getId())) {
                avisos.enviar(calificacion.getAlumno(), "Resultados publicados", evaluacion.getTitulo());
            }
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
        Evaluacion evaluacion = evaluacionRepository.buscarActivaParaEscritura(id)
                .orElseThrow(() -> new ResourceNotFoundException("EVALUACION_NO_ENCONTRADA", "No se encontró la evaluación."));
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        flujoEntrega.verificar(evaluacion, null);
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

    @Transactional
    public EvaluacionDto.Response cancelarEntrega(Long id) {
        var evaluacion = evaluacionRepository.buscarActivaParaEscritura(id)
                .orElseThrow(() -> new ResourceNotFoundException("EVALUACION_NO_ENCONTRADA", "No se encontró la evaluación."));
        accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
        if (!evaluacion.isEntregaPorConfigurar() || evaluacion.getEstado() != EstadoEvaluacion.BORRADOR
                || flujoEntrega.tareaVinculada(evaluacion) != null
                || !calificacionRepository.findAllByEvaluacionIdAndActivoTrue(id).isEmpty()) {
            throw new BusinessRuleException("ENTREGA_NO_CANCELABLE", "Solo se puede cancelar una configuración pendiente y sin calificaciones.");
        }
        evaluacion.setEntregaPorConfigurar(false);
        auditoria.registrar("CANCELAR_CONFIGURACION_ENTREGA", ModuloAuditoria.ACADEMICO, "Evaluacion",
                id.toString(), ResultadoAuditoria.EXITOSO, null, null, null);
        return toResponse(evaluacion);
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
        validarFechaEvaluacion(request.fechaEvaluacion(), asignacion.getSeccion().getAnioAcademico());
        boolean conservaTipo = evaluacion.getAsignacionCurso() != null
                && evaluacion.getAsignacionCurso().getId().equals(asignacion.getId())
                && evaluacion.getTipoEvaluacion() == request.tipoEvaluacion();
        if (evaluacion.isCuentaResultado() && !conservaTipo && reglas.vigente(asignacion)
                .filter(regla -> !regla.getComponentes().containsKey(request.tipoEvaluacion())).isPresent()) {
            throw new BusinessRuleException("COMPONENTE_NO_VIGENTE",
                    "Selecciona un componente de la regla de calificación vigente.", "tipoEvaluacion");
        }
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
        evaluacion.setCompetencias(new LinkedHashSet<>(competencias));
    }

    void validarFechaEvaluacion(LocalDate fecha, int anio) {
        if (fecha.getYear() < 1900 || fecha.getYear() > 2100) {
            throw new FormFieldException("fechaEvaluacion", "Ingresa un año entre 1900 y 2100.");
        }
        if (fecha.getYear() != anio) {
            throw new FormFieldException("fechaEvaluacion", "La fecha debe pertenecer al año académico " + anio + ".");
        }
        var activos = periodos.findByAnioAcademicoAnioOrderByOrden(anio).stream()
                .filter(p -> p.isActivo() && p.getAnioAcademico().isActivo()).toList();
        if (!activos.isEmpty() && activos.stream().noneMatch(p ->
                !fecha.isBefore(p.getFechaInicio()) && !fecha.isAfter(p.getFechaFin()))) {
            throw new FormFieldException("fechaEvaluacion", "La fecha debe estar dentro de un período académico activo de la asignación.");
        }
    }

    private void validarCompetenciasDisponibles(AsignacionCurso asignacion, Set<Long> idsCompetencia) {
        Set<Long> disponibles = planes.competenciasDeAsignacion(asignacion).stream().map(Competencia::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!disponibles.containsAll(idsCompetencia)) {
            throw new BusinessRuleException(
                    "COMPETENCIA_NO_DISPONIBLE_PARA_CURSO",
                    "Selecciona competencias activas del plan de estudios de esta sección."
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
        return toResponse(evaluacion, reglas.vigente(evaluacion.getAsignacionCurso()).orElse(null));
    }

    private EvaluacionDto.Response toResponse(Evaluacion evaluacion, com.asiscontrol.entity.ReglaCalificacion regla) {
        var componente = regla == null || !evaluacion.isCuentaResultado() ? null : regla.getComponentes().get(evaluacion.getTipoEvaluacion());
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

        var actividad = flujoEntrega.actividadVinculada(evaluacion);
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
                evaluacion.getAsignacionCurso().nombreCurso(),
                evaluacion.getAsignacionCurso().getSeccion().getId(),
                AsignacionCursoService.etiquetaSeccion(evaluacion.getAsignacionCurso().getSeccion()),
                evaluacion.getAsignacionCurso().getSeccion().getAnioAcademico(),
                regla != null,
                componente == null ? null : componente.getNombre(),
                componente == null ? null : componente.getPeso(),
                evaluacion.isModeloCualitativo(),
                evaluacion.isEntregaPorConfigurar(),
                actividad == null ? null : actividad.getId(),
                actividad == null ? null : actividad.getEstadoPublicacion()
        );
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
