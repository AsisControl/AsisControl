package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.tarea.TareaDtos.ActualizarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CrearTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.TareaResponse;
import com.asiscontrol.dto.tarea.TareaDtos.EvaluacionAcademica;
import com.asiscontrol.dto.tarea.TareaDtos.ResultadoCompetencia;
import com.asiscontrol.dto.tarea.TareaDtos.CerosFaltantes;
import com.asiscontrol.dto.tarea.TareaDtos.RevisionSinEntrega;
import com.asiscontrol.dto.tarea.TareaDtos.NivelSinEntregaRequest;
import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.dto.tarea.TareaDtos.CompetenciasSinPeso;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.NotaTareaSinEntrega;
import com.asiscontrol.entity.DevolucionTareaSinEntrega;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.EntregaTareaRepository;
import com.asiscontrol.repository.CalificacionRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.TareaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TareaService {

    private final TareaRepository tareaRepository;
    private final EntregaTareaRepository entregaRepository;
    private final ArchivoStorageService archivoService;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final ReglaCalificacionService reglas;
    private final CalificacionRepository calificaciones;
    private final CalificacionService registro;
    private final MatriculaRepository matriculas;
    private final EvaluacionService evaluaciones;
    private final EscalaInstitucionalService escalas;

    public TareaService(
            TareaRepository tareaRepository,
            EntregaTareaRepository entregaRepository,
            ArchivoStorageService archivoService,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria, ReglaCalificacionService reglas, CalificacionRepository calificaciones,
            CalificacionService registro, MatriculaRepository matriculas, EvaluacionService evaluaciones,
            EscalaInstitucionalService escalas) {
        this.tareaRepository = tareaRepository;
        this.entregaRepository = entregaRepository;
        this.archivoService = archivoService;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.reglas = reglas;
        this.calificaciones = calificaciones;
        this.registro = registro;
        this.matriculas = matriculas;
        this.evaluaciones = evaluaciones;
        this.escalas = escalas;
    }

    @Transactional
    public TareaResponse crear(CrearTareaRequest request) {
        AsignacionCurso asignacion = buscarActivo(
                AsignacionCurso.class, request.asignacionCursoId(), "ASIGNACION_CURSO_NO_ENCONTRADA", "asignación de curso");
        accessGuard.verificarAsignacionDocente(asignacion);
        validarFechas(request.fechaPublicacion(), request.fechaLimite());

        Tarea tarea = new Tarea();
        tarea.setAsignacionCurso(asignacion);
        tarea.setNombre(request.nombre().trim());
        tarea.setDescripcion(request.descripcion().trim());
        tarea.setFechaPublicacion(request.fechaPublicacion());
        tarea.setFechaLimite(request.fechaLimite());
        tarea.setPuntajeMaximo(20);
        tarea.setPuntajeMaximoFijo(true);
        tarea.setModeloCualitativo(true);
        configurarEvaluacion(tarea, request.cuentaParaResultadoAcademico(), request.idEvaluacionAcademica(),
                request.competenciasSinPeso(), request.nuevaEvaluacion(), request.fechaLimite());
        tarea.setMaxArchivosEntrega(request.maxArchivosEntrega());
        tarea.setArchivos(archivoService.obtenerActivos(request.archivoIds()));
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.BORRADOR);
        tarea.setActivo(true);
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("CREAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return mapResponse(saved);
    }

    /**
     * US-22: se editan tareas en borrador o publicadas. Si la tarea publicada ya tiene entregas,
     * cambiar la fecha límite exige motivo y recalcula si cada entrega fue a tiempo o tarde.
     */
    @Transactional
    public TareaResponse actualizar(Long id, ActualizarTareaRequest request) {
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        exigirVersion(tarea.getVersion(), request.version());
        boolean publicada = tarea.getEstadoPublicacion() == EstadoPublicacionTarea.PUBLICADA;
        if (!publicada && tarea.getEstadoPublicacion() != EstadoPublicacionTarea.BORRADOR) {
            throw new BusinessRuleException(
                    "TAREA_TRANSICION_INVALIDA", "Solo se puede editar una tarea en borrador o publicada");
        }
        LocalDateTime fechaPublicacion = request.fechaPublicacion();
        if (publicada) {
            // Ya es visible para los alumnos: la publicacion no se reprograma.
            if (fechaPublicacion != null && !fechaPublicacion.equals(tarea.getFechaPublicacion())) {
                throw new BusinessRuleException(
                        "TAREA_PUBLICACION_NO_EDITABLE",
                        "No se puede cambiar la fecha de publicación de una tarea publicada",
                        "fechaPublicacion");
            }
            fechaPublicacion = tarea.getFechaPublicacion();
        }
        validarFechas(fechaPublicacion, request.fechaLimite());
        boolean cambiaLimite = !request.fechaLimite().equals(tarea.getFechaLimite());
        String motivo = normalizar(request.motivo());
        if (publicada) {
            validarCambiosConEntregas(tarea, request, cambiaLimite, motivo);
        }
        String anterior = describir(tarea);
        configurarEvaluacion(tarea, request.cuentaParaResultadoAcademico(), request.idEvaluacionAcademica(),
                request.competenciasSinPeso(), request.nuevaEvaluacion(), request.fechaLimite());
        if (tarea.isPuntajeMaximoFijo() && request.puntajeMaximo() != null && request.puntajeMaximo() != 20) {
            throw new BusinessRuleException("TAREA_PUNTAJE_FIJO", "El puntaje máximo de esta tarea es 20.", "puntajeMaximo");
        }

        tarea.setNombre(request.nombre().trim());
        tarea.setDescripcion(request.descripcion().trim());
        tarea.setFechaPublicacion(fechaPublicacion);
        tarea.setFechaLimite(request.fechaLimite());
        if (request.puntajeMaximo() != null) tarea.setPuntajeMaximo(request.puntajeMaximo());
        tarea.setMaxArchivosEntrega(request.maxArchivosEntrega());
        tarea.setArchivos(archivoService.obtenerActivos(request.archivoIds()));
        int recalculadas = publicada && cambiaLimite ? recalcularEntregas(tarea) : 0;
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("ACTUALIZAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, motivo, anterior,
                describir(saved) + (recalculadas > 0 ? ",entregasRecalculadas=" + recalculadas : ""));
        return mapResponse(saved);
    }

    private void configurarEvaluacion(Tarea tarea, Boolean cuenta, Long idEvaluacion, CompetenciasSinPeso sinPeso,
                                       com.asiscontrol.dto.tarea.TareaDtos.EvaluacionParaTarea nueva,
                                       LocalDateTime fechaLimite) {
        boolean modificaVinculo = nueva != null || sinPeso != null || idEvaluacion != null
                || (cuenta != null && tarea.getEvaluacionAcademica() != null);
        if (modificaVinculo) {
            var sesion = SecurityContextHolder.getContext().getAuthentication();
            if (sesion == null || sesion.getAuthorities().stream()
                    .noneMatch(permiso -> "CALIFICACIONES_ESCRIBIR".equals(permiso.getAuthority()))) {
                throw new AccessDeniedException("Se requiere permiso para gestionar calificaciones de la tarea.");
            }
        }
        if (tarea.isModeloCualitativo() && (nueva != null || sinPeso != null)) {
            throw new BusinessRuleException("TAREA_EVALUACION_PREVIA_REQUERIDA", "Crea primero la evaluación y selecciona su borrador sin calificaciones.");
        }
        if (nueva != null) {
            if (!Boolean.TRUE.equals(cuenta) || idEvaluacion != null || sinPeso != null
                    || tarea.getEvaluacionAcademica() != null || tarea.getEstadoPublicacion() != EstadoPublicacionTarea.BORRADOR) {
                throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE",
                        "Crea una evaluación académica desde una tarea en borrador y sin vínculo previo.");
            }
            if (reglas.vigente(tarea.getAsignacionCurso())
                    .filter(regla -> regla.getComponentes().containsKey(nueva.tipoEvaluacion())).isEmpty()) {
                throw new BusinessRuleException("COMPONENTE_NO_VIGENTE", "Selecciona un componente de la regla vigente.");
            }
            var creada = evaluaciones.crear(new EvaluacionDto.Request(tarea.getAsignacionCurso().getId(),
                    tarea.getNombre(), null, nueva.tipoEvaluacion(),
                    com.asiscontrol.entity.enums.PeriodoEvaluacion.PERIODO_ACADEMICO,
                    fechaAcademica(tarea, fechaLimite), nueva.idsCompetencia()));
            tarea.setEvaluacionAcademica(entityManager.getReference(Evaluacion.class, creada.id()));
            return;
        }
        if (sinPeso != null) {
            if (Boolean.TRUE.equals(cuenta) || idEvaluacion != null || tarea.getEvaluacionAcademica() != null
                    || tarea.getEstadoPublicacion() != EstadoPublicacionTarea.BORRADOR) {
                throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE",
                        "Las competencias sin peso se configuran en una tarea en borrador y sin evaluación vinculada.");
            }
            var creada = evaluaciones.crearSinPeso(new EvaluacionDto.Request(tarea.getAsignacionCurso().getId(),
                    tarea.getNombre(), null, com.asiscontrol.entity.enums.TipoEvaluacion.TRABAJO_ACADEMICO,
                    com.asiscontrol.entity.enums.PeriodoEvaluacion.PERIODO_ACADEMICO,
                    fechaAcademica(tarea, fechaLimite), sinPeso.idsCompetencia()));
            tarea.setEvaluacionAcademica(entityManager.getReference(Evaluacion.class, creada.id()));
            return;
        }
        if (cuenta == null && idEvaluacion == null) return;
        if (Boolean.TRUE.equals(cuenta) != (idEvaluacion != null)) {
            throw new BusinessRuleException("TAREA_EVALUACION_REQUERIDA", "Selecciona una evaluación académica.");
        }
        var anterior = tarea.getEvaluacionAcademica();
        if (Objects.equals(anterior == null ? null : anterior.getId(), idEvaluacion)) return;
        if (tarea.getEstadoPublicacion() != EstadoPublicacionTarea.BORRADOR
                || (anterior != null && (anterior.getEstado() != EstadoEvaluacion.BORRADOR || tieneCalificaciones(anterior.getId())))) {
            throw new BusinessRuleException("TAREA_VINCULO_NO_EDITABLE", "El vínculo académico ya no se puede modificar.");
        }
        if (idEvaluacion == null) {
            tarea.setEvaluacionAcademica(null);
            return;
        }
        var evaluacion = buscarActivo(Evaluacion.class, idEvaluacion, "EVALUACION_NO_ENCONTRADA", "evaluación");
        entityManager.refresh(evaluacion, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var asignacion = tarea.getAsignacionCurso();
        if (!asignacion.getId().equals(evaluacion.getAsignacionCurso().getId())
                || !evaluacion.isCuentaResultado() || !evaluacion.isModeloCualitativo()
                || evaluacion.getEstado() != EstadoEvaluacion.BORRADOR || tieneCalificaciones(evaluacion.getId())
                || evaluacion.getCompetencias().isEmpty()) {
            throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE",
                    "La evaluación debe pertenecer a la asignación, tener competencias y estar habilitada.");
        }
        evaluaciones.validarFechaEvaluacion(evaluacion.getFechaEvaluacion(), asignacion.getSeccion().getAnioAcademico());
        if (reglas.vigente(asignacion).filter(regla -> regla.getComponentes().containsKey(evaluacion.getTipoEvaluacion())).isEmpty()) {
            throw new BusinessRuleException("COMPONENTE_NO_VIGENTE", "La evaluación debe tener un componente de la regla vigente.");
        }
        if (tareaRepository.findByEvaluacionAcademicaId(idEvaluacion).isPresent()) {
            throw new ConflictException("EVALUACION_YA_VINCULADA", "La evaluación ya está vinculada a otra tarea.");
        }
        tarea.setEvaluacionAcademica(evaluacion);
        evaluacion.setEntregaPorConfigurar(false);
    }

    private boolean tieneCalificaciones(Long evaluacion) {
        return entityManager.createQuery("select count(c) from Calificacion c where c.evaluacion.id = :evaluacion", Long.class)
                .setParameter("evaluacion", evaluacion).getSingleResult() > 0;
    }

    private Tarea tareaParaResultados(Long id) {
        var tarea = bloquearTareaActiva(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        if (tarea.getEvaluacionAcademica() == null || tarea.getEstadoPublicacion() == EstadoPublicacionTarea.BORRADOR
                || tarea.getEstadoPublicacion() == EstadoPublicacionTarea.ARCHIVADA) {
            throw new BusinessRuleException("TAREA_NO_CALIFICABLE", "La tarea no está habilitada para gestionar resultados académicos.");
        }
        return tarea;
    }

    @Transactional
    public List<CalificacionDto.Response> calificarEntregas(Long id, CalificacionDto.CargaLoteRequest request) {
        var tarea = tareaParaResultados(id);
        var estados = entregaRepository.findAllByTareaIdAndActivoTrue(id).stream()
                .collect(java.util.stream.Collectors.toMap(entrega -> entrega.getAlumno().getId(), EntregaTarea::getEstado));
        if (request.calificaciones().stream().anyMatch(nota ->
                estados.get(nota.idAlumno()) == EstadoTarea.ANULADO
                        || (!estados.containsKey(nota.idAlumno()) && tarea.getEstadoPublicacion() != EstadoPublicacionTarea.CERRADA))) {
            throw new BusinessRuleException("TAREA_ENTREGA_REQUERIDA", "El estudiante debe tener una entrega válida o la recepción debe estar cerrada.");
        }
        return registro.registrarLote(request, tarea.getId());
    }

    @Transactional
    public List<CalificacionDto.Response> corregirResultados(Long id, CalificacionDto.CorreccionRequest request) {
        return registro.corregir(request, tareaParaResultados(id).getId());
    }

    @Transactional
    public void quitarCalificacion(Long id, Long calificacion, Long version) {
        tareaParaResultados(id);
        registro.eliminar(calificacion, id, version);
    }

    @Transactional
    public EvaluacionDto.Response publicarResultados(Long id) {
        var tarea = tareaParaResultados(id);
        return evaluaciones.cambiarEstado(tarea.getEvaluacionAcademica().getId(), EstadoEvaluacion.PUBLICADA, id);
    }

    private java.time.LocalDate fechaAcademica(Tarea tarea, LocalDateTime fechaLimite) {
        var fecha = fechaLimite.toLocalDate();
        try {
            evaluaciones.validarFechaEvaluacion(fecha, tarea.getAsignacionCurso().getSeccion().getAnioAcademico());
        } catch (com.asiscontrol.exception.FormFieldException error) {
            throw new com.asiscontrol.exception.FormFieldException("fechaLimite", error.getMessage());
        }
        return fecha;
    }

    private void validarCambiosConEntregas(
            Tarea tarea, ActualizarTareaRequest request, boolean cambiaLimite, String motivo) {
        if (cambiaLimite && !request.fechaLimite().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException(
                    "TAREA_FECHA_LIMITE_VENCIDA",
                    "La nueva fecha límite de una tarea publicada debe ser futura", "fechaLimite");
        }
        if (cambiaLimite && motivo == null && entregaRepository.countByTareaIdAndActivoTrue(tarea.getId()) > 0) {
            throw new BusinessRuleException(
                    "TAREA_MOTIVO_REQUERIDO",
                    "La tarea ya tiene entregas: indique el motivo del cambio de fecha límite", "motivo");
        }
        BigDecimal notaMaxima = entregaRepository.maxNotaPorTarea(tarea.getId());
        if (notaMaxima != null && request.puntajeMaximo() != null
                && BigDecimal.valueOf(request.puntajeMaximo()).compareTo(notaMaxima) < 0) {
            throw new BusinessRuleException(
                    "TAREA_PUNTAJE_MENOR_A_NOTAS",
                    "El puntaje máximo no puede ser menor que una nota ya registrada (" + notaMaxima + ")",
                    "puntajeMaximo");
        }
    }

    /** Las entregas sin calificar pasan a tiempo o tarde segun la nueva fecha limite. */
    private int recalcularEntregas(Tarea tarea) {
        int cambiadas = 0;
        for (EntregaTarea entrega : entregaRepository.findAllByTareaIdAndActivoTrue(tarea.getId())) {
            if (entrega.getEstado() != EstadoTarea.ENTREGADO && entrega.getEstado() != EstadoTarea.ENTREGADO_TARDE) {
                continue;
            }
            EstadoTarea nuevo = entrega.getFechaHoraEntrega().isAfter(tarea.getFechaLimite())
                    ? EstadoTarea.ENTREGADO_TARDE
                    : EstadoTarea.ENTREGADO;
            if (nuevo != entrega.getEstado()) {
                entrega.setEstado(nuevo);
                cambiadas++;
            }
        }
        return cambiadas;
    }

    @Transactional
    public TareaResponse publicar(Long id) {
        Tarea tarea = bloquearTareaActiva(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        exigirEstado(tarea, EstadoPublicacionTarea.BORRADOR, "La tarea debe estar en borrador para publicarse");
        var evaluacion = tarea.getEvaluacionAcademica();
        if (evaluacion != null && (!evaluacion.isActivo() || evaluacion.getEstado() == EstadoEvaluacion.ANULADA)) {
            throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE",
                    "La evaluación vinculada ya no está disponible para activar la actividad.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (!tarea.getFechaLimite().isAfter(ahora)) {
            throw new BusinessRuleException("TAREA_FECHA_LIMITE_VENCIDA", "No se puede publicar una tarea ya vencida");
        }
        if (tarea.getFechaPublicacion() != null && tarea.getFechaPublicacion().isAfter(ahora)) {
            throw new BusinessRuleException(
                    "TAREA_PUBLICACION_FUTURA", "Use una fecha de publicación actual o anterior para publicar la tarea");
        }
        if (tarea.getFechaPublicacion() == null) {
            tarea.setFechaPublicacion(ahora);
        }
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.PUBLICADA);
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("PUBLICAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "estado=" + EstadoPublicacionTarea.BORRADOR, "estado=" + saved.getEstadoPublicacion());
        return mapResponse(saved);
    }

    @Transactional
    public TareaResponse cerrar(Long id) {
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        entityManager.refresh(tarea, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (tarea.getEstadoPublicacion() == EstadoPublicacionTarea.CERRADA) return mapResponse(tarea);
        exigirEstado(tarea, EstadoPublicacionTarea.PUBLICADA, "Solo se puede cerrar una tarea publicada");
        if (LocalDateTime.now().isBefore(tarea.getFechaLimite())) {
            throw new BusinessRuleException(
                    "TAREA_AUN_VIGENTE", "La tarea todavía acepta entregas y no puede cerrarse antes de su fecha límite");
        }
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.CERRADA);
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("CERRAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "estado=" + EstadoPublicacionTarea.PUBLICADA, "estado=" + saved.getEstadoPublicacion());
        return mapResponse(saved);
    }

    @Transactional
    public TareaResponse archivar(Long id) {
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        exigirEstado(tarea, EstadoPublicacionTarea.CERRADA, "Solo se puede archivar una tarea cerrada");
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.ARCHIVADA);
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("ARCHIVAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null,
                "estado=" + EstadoPublicacionTarea.CERRADA, "estado=" + saved.getEstadoPublicacion());
        return mapResponse(saved);
    }

    /**
     * US-22: baja logica en cualquier estado. Con entregas exige motivo; las entregas se conservan
     * para la trazabilidad y la tarea deja de mostrarse a docentes, alumnos y apoderados.
     */
    @Transactional
    public void eliminar(Long id, String motivoRecibido) {
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        entityManager.refresh(tarea, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        long entregas = entregaRepository.countByTareaIdAndActivoTrue(id);
        String motivo = normalizar(motivoRecibido);
        if (entregas > 0 && motivo == null) {
            throw new BusinessRuleException(
                    "TAREA_MOTIVO_REQUERIDO",
                    "La tarea ya tiene entregas: indique el motivo de la eliminación", "motivo");
        }
        var evaluacion = tarea.getEvaluacionAcademica();
        if (evaluacion != null && entregas == 0 && tarea.getEstadoPublicacion() == EstadoPublicacionTarea.BORRADOR
                && evaluacion.getEstado() == EstadoEvaluacion.BORRADOR && !tieneCalificaciones(evaluacion.getId())) {
            tarea.setEvaluacionAcademica(null);
        }
        tarea.setActivo(false);
        tareaRepository.save(tarea);
        auditoria.registrar("ELIMINAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, motivo,
                describir(tarea) + ",entregas=" + entregas, null);
    }

    public TareaResponse obtener(Long id) {
        return mapResponse(obtenerEntidad(id));
    }

    public Page<TareaResponse> listar(
            Long asignacionCursoId,
            Long cursoId,
            EstadoPublicacionTarea estado,
            String search,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        return tareaRepository.buscarVisibles(
                        asignacionCursoId,
                        cursoId,
                        estado,
                        search == null ? "" : search.strip().toLowerCase(java.util.Locale.ROOT),
                        personaDocenteId,
                        personaAlumnoId,
                        personaApoderadoId,
                        pageable)
                .map(this::mapResponse);
    }

    public Tarea obtenerEntidad(Long id) {
        return tareaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TAREA_NO_ENCONTRADA", "No existe una tarea activa con id " + id));
    }

    public CerosFaltantes previsualizarCeros(Long id) {
        var tarea = obtenerEntidad(id);
        exigirFlujoHistoricoDeCeros(tarea);
        return resumirCeros(cerosPendientes(tarea));
    }

    @Transactional
    public CerosFaltantes confirmarCeros(Long id, int estudiantesEsperados) {
        var tarea = obtenerEntidad(id);
        entityManager.lock(tarea, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        exigirFlujoHistoricoDeCeros(tarea);
        if (tarea.getEvaluacionAcademica() != null) {
            entityManager.refresh(tarea.getEvaluacionAcademica(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        var notas = cerosPendientes(tarea);
        var resumen = resumirCeros(notas);
        if (resumen.estudiantes() != estudiantesEsperados) {
            throw new ConflictException("TAREA_FALTANTES_CAMBIARON", "Consulta de nuevo cuántos estudiantes faltan.");
        }
        registro.registrarLote(new CalificacionDto.CargaLoteRequest(notas), id);
        auditoria.registrar("CALIFICAR_TAREA_FALTANTES_CON_CERO", ModuloAuditoria.TAREAS, "Tarea",
                tarea.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "estudiantes=" + resumen.estudiantes() + ",calificaciones=" + resumen.calificaciones());
        return resumen;
    }

    private void exigirFlujoHistoricoDeCeros(Tarea tarea) {
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        if (tarea.isModeloCualitativo() || tarea.getEvaluacionAcademica() != null && tarea.getEvaluacionAcademica().isModeloCualitativo()) {
            throw new BusinessRuleException("NIVEL_CUALITATIVO_REQUERIDO", "Usa Calificar sin entrega y selecciona AD, A, B o C.");
        }
    }

    public com.asiscontrol.dto.tarea.TareaDtos.SinEntregaDisponible sinEntrega(Long id) {
        var tarea = obtenerEntidad(id);
        if (tarea.getEvaluacionAcademica() == null) {
            var alumnos = alumnosSinEntregaCalificable(tarea).stream().map(alumno ->
                    new com.asiscontrol.dto.tarea.TareaDtos.SinEntregaAlumno(alumno.getId(), alumno.getPersona().nombreCompleto(),
                            alumno.getCodigoAlumno(), List.of())).toList();
            return new com.asiscontrol.dto.tarea.TareaDtos.SinEntregaDisponible(alumnos, List.of(), tarea.getPuntajeMaximo(), tarea.isModeloCualitativo());
        }
        var porAlumno = cerosPendientes(tarea).stream().collect(java.util.stream.Collectors.groupingBy(
                CalificacionDto.Request::idAlumno, java.util.stream.Collectors.mapping(
                        CalificacionDto.Request::idCompetencia, java.util.stream.Collectors.toList())));
        var alumnos = matriculas.buscarAlumnosActivosDeSeccion(tarea.getAsignacionCurso().getSeccion().getId()).stream()
                .filter(alumno -> porAlumno.containsKey(alumno.getId()))
                .map(alumno -> new com.asiscontrol.dto.tarea.TareaDtos.SinEntregaAlumno(alumno.getId(),
                        alumno.getPersona().nombreCompleto(), alumno.getCodigoAlumno(), porAlumno.get(alumno.getId())))
                .toList();
        var competencias = tarea.getEvaluacionAcademica().getCompetencias().stream()
                .map(competencia -> new com.asiscontrol.dto.tarea.TareaDtos.SinEntregaCompetencia(
                        competencia.getId(), competencia.getNombre())).toList();
        return new com.asiscontrol.dto.tarea.TareaDtos.SinEntregaDisponible(alumnos, competencias, Math.min(20, tarea.getPuntajeMaximo()),
                tarea.getEvaluacionAcademica().isModeloCualitativo());
    }

    @Transactional
    public CerosFaltantes calificarSinEntrega(Long id, com.asiscontrol.dto.tarea.TareaDtos.SinEntregaRequest request) {
        String observacion = normalizar(request.observacion());
        var tarea = bloquearTareaActiva(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        int maximo = tarea.getEvaluacionAcademica() == null ? tarea.getPuntajeMaximo() : Math.min(20, tarea.getPuntajeMaximo());
        boolean cualitativa = tarea.getEvaluacionAcademica() == null ? tarea.isModeloCualitativo()
                : tarea.getEvaluacionAcademica().isModeloCualitativo();
        if (cualitativa ? request.nivelLogro() == null || request.nota() != null
                : request.nota() == null || request.nivelLogro() != null) {
            throw new BusinessRuleException("TAREA_CALIFICACION_INCOMPATIBLE",
                    cualitativa ? "Selecciona un nivel AD, A, B o C." : "Registra el puntaje histórico de la tarea.");
        }
        if (request.nota() != null && request.nota().compareTo(BigDecimal.valueOf(maximo)) > 0) {
            throw new BusinessRuleException("TAREA_NOTA_INVALIDA", "La nota supera el puntaje máximo de la tarea.");
        }
        if (tarea.getEvaluacionAcademica() == null) {
            if (!request.competencias().isEmpty()) {
                throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE", "Esta tarea se califica sin seleccionar competencias.");
            }
            var alumnos = alumnosSinEntregaCalificable(tarea).stream()
                    .filter(alumno -> request.alumnos().contains(alumno.getId())).toList();
            if (alumnos.size() != request.alumnos().size()) {
                throw new ConflictException("TAREA_FALTANTES_CAMBIARON", "Hay estudiantes que ya no admiten la calificación. Actualiza la selección.");
            }
            alumnos.forEach(alumno -> entityManager.persist(new com.asiscontrol.entity.NotaTareaSinEntrega(
                    tarea, alumno, request.nota(), request.nivelLogro(), observacion)));
            auditarCalificacionSinEntrega(id, request);
            return new CerosFaltantes(alumnos.size(), alumnos.size());
        }
        if (request.competencias().isEmpty()) {
            throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE", "Selecciona las competencias que deseas calificar.");
        }
        var competenciasEvaluadas = tarea.getEvaluacionAcademica().getCompetencias().stream()
                .map(Competencia::getId).toList();
        if (!competenciasEvaluadas.containsAll(request.competencias())) {
            throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE",
                    "Selecciona únicamente competencias de la evaluación vinculada.");
        }
        var notas = cerosPendientes(tarea).stream()
                .filter(nota -> request.alumnos().contains(nota.idAlumno()) && request.competencias().contains(nota.idCompetencia()))
                .map(nota -> new CalificacionDto.Request(nota.idEvaluacion(), nota.idAlumno(), nota.idCompetencia(),
                        request.nivelLogro(), observacion, request.nota(), -1L)).toList();
        if (notas.stream().map(CalificacionDto.Request::idAlumno).distinct().count() != request.alumnos().size()) {
            throw new ConflictException("TAREA_FALTANTES_CAMBIARON",
                    "Hay estudiantes o competencias que ya no admiten la calificación. Actualiza la selección.");
        }
        registro.registrarLote(new CalificacionDto.CargaLoteRequest(notas), id);
        var resumen = resumirCeros(notas);
        auditarCalificacionSinEntrega(id, request);
        return resumen;
    }

    public RevisionSinEntrega revisionSinEntrega(Long id, Long alumno) {
        var tarea = obtenerEntidad(id);
        verificarRevisionSinEntrega(tarea, alumno, false);
        var calificacion = notaSinEntrega(id, alumno);
        var devolucion = entityManager.find(DevolucionTareaSinEntrega.class, new DevolucionTareaSinEntrega.Clave(id, alumno));
        return new RevisionSinEntrega(calificacion == null ? null : calificacion.getNota(),
                calificacion == null ? null : calificacion.getNivelLogro(),
                calificacion == null ? null : calificacion.getObservacion(),
                devolucion == null ? null : devolucion.getComentario());
    }

    @Transactional
    public void guardarNivelSinEntrega(Long id, Long alumno, NivelSinEntregaRequest request) {
        var tarea = tareaParaRevisionSinEntrega(id, alumno);
        if (tarea.getEvaluacionAcademica() != null) {
            throw new BusinessRuleException("TAREA_EVALUACION_INCOMPATIBLE", "Registra el nivel de cada competencia en la evaluación vinculada.");
        }
        if (tarea.isModeloCualitativo() ? request.nivelLogro() == null || request.nota() != null
                : request.nivelLogro() != null || request.nota() == null || request.nota().compareTo(BigDecimal.valueOf(tarea.getPuntajeMaximo())) > 0) {
            throw new BusinessRuleException("TAREA_CALIFICACION_INCOMPATIBLE", "Selecciona un nivel o un puntaje válido para esta tarea.");
        }
        var nota = notaSinEntrega(id, alumno);
        String anterior = nota == null ? null : describirNivelSinEntrega(alumno, nota.getNota(), nota.getNivelLogro(), nota.getObservacion());
        var observacion = normalizar(request.observacion());
        if (nota == null) {
            entityManager.persist(new NotaTareaSinEntrega(tarea, entityManager.getReference(Alumno.class, alumno),
                    request.nota(), request.nivelLogro(), observacion));
        } else {
            nota.actualizar(request.nota(), request.nivelLogro(), observacion);
        }
        auditoria.registrar("CALIFICAR_TAREA_SIN_ENTREGA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior,
                describirNivelSinEntrega(alumno, request.nota(), request.nivelLogro(), observacion));
    }

    private String describirNivelSinEntrega(Long alumno, BigDecimal nota, com.asiscontrol.entity.enums.NivelLogro nivel,
                                           String observacion) {
        return "alumno=" + alumno + ",nota=" + nota + ",nivel=" + nivel + ",observacion=" + observacion;
    }

    @Transactional
    public void guardarDevolucionSinEntrega(Long id, Long alumno, String comentario) {
        tareaParaRevisionSinEntrega(id, alumno);
        var clave = new DevolucionTareaSinEntrega.Clave(id, alumno);
        var devolucion = entityManager.find(DevolucionTareaSinEntrega.class, clave);
        String anterior = devolucion == null ? null : "alumno=" + alumno + ",devolucion=" + devolucion.getComentario();
        String texto = normalizar(comentario);
        if (texto == null) {
            if (devolucion != null) entityManager.remove(devolucion);
        } else if (devolucion == null) {
            entityManager.persist(new DevolucionTareaSinEntrega(clave, texto));
        } else {
            devolucion.setComentario(texto);
        }
        auditoria.registrar("DEVOLUCION_TAREA_SIN_ENTREGA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, "alumno=" + alumno + ",devolucion=" + texto);
    }

    private Tarea tareaParaRevisionSinEntrega(Long id, Long alumno) {
        var tarea = bloquearTareaActiva(id);
        verificarRevisionSinEntrega(tarea, alumno, true);
        return tarea;
    }

    private Tarea bloquearTareaActiva(Long id) {
        return tareaRepository.buscarActivaParaPublicar(id)
                .orElseThrow(() -> new ResourceNotFoundException("TAREA_NO_ENCONTRADA", "No existe una tarea activa con id " + id));
    }

    private void verificarRevisionSinEntrega(Tarea tarea, Long alumno, boolean escritura) {
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        var estado = tarea.getEstadoPublicacion();
        if ((estado != EstadoPublicacionTarea.CERRADA && (escritura || estado != EstadoPublicacionTarea.ARCHIVADA))
                || entregaRepository.existsByTareaIdAndAlumnoIdAndActivoTrue(tarea.getId(), alumno)
                || !matriculas.existsByAlumnoIdAndSeccionIdAndEstadoAndActivoTrue(alumno,
                        tarea.getAsignacionCurso().getSeccion().getId(), com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA)) {
            throw new BusinessRuleException("TAREA_REVISION_SIN_ENTREGA_NO_DISPONIBLE", "La revisión requiere una tarea cerrada y un estudiante matriculado sin entrega.");
        }
    }

    private NotaTareaSinEntrega notaSinEntrega(Long tarea, Long alumno) {
        return entityManager.createQuery("""
                select n from NotaTareaSinEntrega n where n.tarea.id = :tarea and n.alumno.id = :alumno
                """, NotaTareaSinEntrega.class).setParameter("tarea", tarea).setParameter("alumno", alumno)
                .getResultList().stream().findFirst().orElse(null);
    }

    private void auditarCalificacionSinEntrega(Long id, com.asiscontrol.dto.tarea.TareaDtos.SinEntregaRequest request) {
        auditoria.registrar("CALIFICAR_TAREA_SIN_ENTREGA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, null,
                "nota=" + request.nota() + ",nivel=" + request.nivelLogro()
                        + ",estudiantes=" + request.alumnos() + ",competencias=" + request.competencias());
    }

    private List<com.asiscontrol.entity.Alumno> alumnosSinEntregaCalificable(Tarea tarea) {
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        if (tarea.getEstadoPublicacion() != EstadoPublicacionTarea.CERRADA) {
            throw new BusinessRuleException("TAREA_CEROS_NO_DISPONIBLES", "La tarea debe estar cerrada.");
        }
        var excluidos = entregaRepository.findAllByTareaIdAndActivoTrue(tarea.getId()).stream()
                .map(item -> item.getAlumno().getId()).collect(java.util.stream.Collectors.toSet());
        excluidos.addAll(entityManager.createQuery(
                "select n.alumno.id from NotaTareaSinEntrega n where n.tarea.id = :tarea and (n.nota is not null or n.nivelLogro is not null)", Long.class)
                .setParameter("tarea", tarea.getId()).getResultList());
        return matriculas.buscarAlumnosActivosDeSeccion(tarea.getAsignacionCurso().getSeccion().getId()).stream()
                .filter(alumno -> !excluidos.contains(alumno.getId())).toList();
    }

    private List<CalificacionDto.Request> cerosPendientes(Tarea tarea) {
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        var evaluacion = tarea.getEvaluacionAcademica();
        if (evaluacion == null || !evaluacion.isActivo() || evaluacion.getEstado() != EstadoEvaluacion.BORRADOR
                || tarea.getEstadoPublicacion() != EstadoPublicacionTarea.CERRADA) {
            throw new BusinessRuleException("TAREA_CEROS_NO_DISPONIBLES",
                    "La tarea debe estar cerrada y su evaluación vinculada debe seguir en borrador.");
        }
        if (!evaluacion.isModeloCualitativo()) escalas.exigirVigente();
        var entregaron = entregaRepository.findAllByTareaIdAndActivoTrue(tarea.getId()).stream()
                .map(item -> item.getAlumno().getId()).collect(java.util.stream.Collectors.toSet());
        record Celda(Long alumno, Long competencia) {}
        var existentes = calificaciones.findAllByEvaluacionIdAndActivoTrue(evaluacion.getId()).stream()
                .map(nota -> new Celda(nota.getAlumno().getId(), nota.getCompetencia().getId()))
                .collect(java.util.stream.Collectors.toSet());
        return matriculas.buscarAlumnosActivosDeSeccion(tarea.getAsignacionCurso().getSeccion().getId()).stream()
                .filter(alumno -> !entregaron.contains(alumno.getId()))
                .flatMap(alumno -> evaluacion.getCompetencias().stream()
                        .filter(competencia -> !existentes.contains(new Celda(alumno.getId(), competencia.getId())))
                        .map(competencia -> new CalificacionDto.Request(evaluacion.getId(), alumno.getId(),
                                competencia.getId(), null, null, BigDecimal.ZERO)))
                .toList();
    }

    private CerosFaltantes resumirCeros(List<CalificacionDto.Request> notas) {
        return new CerosFaltantes((int) notas.stream().map(CalificacionDto.Request::idAlumno).distinct().count(), notas.size());
    }

    public TareaResponse mapResponse(Tarea tarea) {
        return new TareaResponse(
                tarea.getId(),
                tarea.getAsignacionCurso().getId(),
                tarea.getNombre(),
                tarea.getDescripcion(),
                tarea.getFechaPublicacion(),
                tarea.getFechaLimite(),
                tarea.getPuntajeMaximo(),
                tarea.getMaxArchivosEntrega(),
                tarea.getEstadoPublicacion(),
                tarea.getArchivos().stream().map(archivoService::mapResponse).toList(),
                tarea.getVersion(), tarea.isPuntajeMaximoFijo(), tarea.isModeloCualitativo(), evaluacionAcademica(tarea));
    }

    private EvaluacionAcademica evaluacionAcademica(Tarea tarea) {
        var evaluacion = tarea.getEvaluacionAcademica();
        if (evaluacion == null) return null;
        var componente = evaluacion.isCuentaResultado() ? reglas.vigente(tarea.getAsignacionCurso())
                .map(regla -> regla.getComponentes().get(evaluacion.getTipoEvaluacion())).orElse(null) : null;
        return new EvaluacionAcademica(evaluacion.getId(), evaluacion.getTitulo(), evaluacion.getEstado(),
                evaluacion.isCuentaResultado(), componente == null ? null : componente.getNombre(),
                componente == null ? null : componente.getPeso());
    }

    List<ResultadoCompetencia> resultadosParaAlumno(Tarea tarea, Long alumnoId) {
        var evaluacion = tarea.getEvaluacionAcademica();
        if (evaluacion == null || !evaluacion.isActivo()
                || (evaluacion.getEstado() != EstadoEvaluacion.PUBLICADA && evaluacion.getEstado() != EstadoEvaluacion.CERRADA)) {
            return List.of();
        }
        var academica = evaluacionAcademica(tarea);
        var dinamica = reglas.vigente(tarea.getAsignacionCurso())
                .filter(regla -> regla.getFormula() == com.asiscontrol.entity.ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1);
        var pesos = dinamica.map(regla -> reglas.pesosDinamicos(evaluacion, regla)).orElseGet(Map::of);
        var cantidades = new HashMap<Long, Long>();
        if (dinamica.isEmpty() && academica.pesoComponente() != null) {
            calificaciones.contarPorComponente(tarea.getAsignacionCurso().getId(), evaluacion.getTipoEvaluacion()).stream()
                    .filter(item -> alumnoId.equals(item.getAlumno()))
                    .forEach(item -> cantidades.put(item.getCompetencia(), item.getCantidad()));
        }
        return calificaciones.findAllByEvaluacionIdAndAlumnoIdAndActivoTrue(evaluacion.getId(), alumnoId).stream()
                .filter(nota -> evaluacion.getCompetencias().contains(nota.getCompetencia()))
                .map(nota -> new ResultadoCompetencia(nota.getCompetencia().getNombre(), nota.getNivelLogro(),
                        dinamica.isPresent() ? pesos.get(nota.getCompetencia().getId())
                                : academica.pesoComponente() == null ? null : ReglaCalificacionService.pesoEfectivo(
                                academica.pesoComponente(), cantidades.getOrDefault(nota.getCompetencia().getId(), 0L))))
                .toList();
    }

    private String describir(Tarea tarea) {
        return "nombre=" + tarea.getNombre()
                + ",asignacionCursoId=" + tarea.getAsignacionCurso().getId()
                + ",estado=" + tarea.getEstadoPublicacion()
                + ",fechaLimite=" + tarea.getFechaLimite()
                + ",puntajeMaximo=" + tarea.getPuntajeMaximo()
                + ",evaluacionAcademica=" + (tarea.getEvaluacionAcademica() == null ? null : tarea.getEvaluacionAcademica().getId())
                + ",archivos=" + tarea.getArchivos().size();
    }

    private void validarFechas(LocalDateTime fechaPublicacion, LocalDateTime fechaLimite) {
        LocalDateTime referencia = fechaPublicacion == null ? LocalDateTime.now() : fechaPublicacion;
        if (!fechaLimite.isAfter(referencia)) {
            throw new BusinessRuleException(
                    "TAREA_RANGO_FECHAS_INVALIDO", "La fecha límite debe ser posterior a la fecha de publicación");
        }
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirEstado(Tarea tarea, EstadoPublicacionTarea esperado, String mensaje) {
        if (tarea.getEstadoPublicacion() != esperado) {
            throw new BusinessRuleException("TAREA_TRANSICION_INVALIDA", mensaje);
        }
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La tarea fue modificada por otro usuario; vuelva a cargarla");
        }
    }

    private <T extends AuditableEntity> T buscarActivo(
            Class<T> tipo, Long id, String codigo, String nombreRecurso) {
        T entidad = entityManager.find(tipo, id);
        if (entidad == null || !entidad.isActivo()) {
            throw new ResourceNotFoundException(codigo, "No existe una " + nombreRecurso + " activa con id " + id);
        }
        return entidad;
    }
}
