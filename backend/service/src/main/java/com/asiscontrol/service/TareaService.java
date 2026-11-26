package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.tarea.TareaDtos.ActualizarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CrearTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.TareaResponse;
import com.asiscontrol.dto.tarea.TareaDtos.EvaluacionAcademica;
import com.asiscontrol.dto.tarea.TareaDtos.ResultadoCompetencia;
import com.asiscontrol.dto.tarea.TareaDtos.CerosFaltantes;
import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.EvaluacionDto;
import com.asiscontrol.dto.tarea.TareaDtos.CompetenciasSinPeso;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.Tarea;
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
        configurarEvaluacion(tarea, request.cuentaParaResultadoAcademico(), request.idEvaluacionAcademica(),
                request.competenciasSinPeso(), request.nuevaEvaluacion());
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
                request.competenciasSinPeso(), request.nuevaEvaluacion());
        if (tarea.isPuntajeMaximoFijo() && request.puntajeMaximo() != 20) {
            throw new BusinessRuleException("TAREA_PUNTAJE_FIJO", "El puntaje máximo de esta tarea es 20.", "puntajeMaximo");
        }

        tarea.setNombre(request.nombre().trim());
        tarea.setDescripcion(request.descripcion().trim());
        tarea.setFechaPublicacion(fechaPublicacion);
        tarea.setFechaLimite(request.fechaLimite());
        tarea.setPuntajeMaximo(request.puntajeMaximo());
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
                                       com.asiscontrol.dto.tarea.TareaDtos.EvaluacionParaTarea nueva) {
        boolean modificaVinculo = nueva != null || sinPeso != null || idEvaluacion != null
                || (cuenta != null && tarea.getEvaluacionAcademica() != null);
        if (modificaVinculo) {
            var sesion = SecurityContextHolder.getContext().getAuthentication();
            if (sesion == null || sesion.getAuthorities().stream()
                    .noneMatch(permiso -> "CALIFICACIONES_ESCRIBIR".equals(permiso.getAuthority()))) {
                throw new AccessDeniedException("Se requiere permiso para gestionar calificaciones de la tarea.");
            }
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
                    com.asiscontrol.entity.enums.PeriodoEvaluacion.PERIODO_ACADEMICO, nueva.fechaEvaluacion(), nueva.idsCompetencia()));
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
                    com.asiscontrol.entity.enums.PeriodoEvaluacion.PERIODO_ACADEMICO, sinPeso.fechaEvaluacion(), sinPeso.idsCompetencia()));
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
                || (anterior != null && anterior.getEstado() != EstadoEvaluacion.BORRADOR)) {
            throw new BusinessRuleException("TAREA_VINCULO_NO_EDITABLE", "El vínculo académico ya no se puede modificar.");
        }
        if (idEvaluacion == null) {
            tarea.setEvaluacionAcademica(null);
            return;
        }
        var evaluacion = buscarActivo(Evaluacion.class, idEvaluacion, "EVALUACION_NO_ENCONTRADA", "evaluación");
        var asignacion = tarea.getAsignacionCurso();
        if (!asignacion.getId().equals(evaluacion.getAsignacionCurso().getId())
                || !evaluacion.isCuentaResultado() || evaluacion.getEstado() == EstadoEvaluacion.ANULADA
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
        if (notaMaxima != null && BigDecimal.valueOf(request.puntajeMaximo()).compareTo(notaMaxima) < 0) {
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
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        exigirEstado(tarea, EstadoPublicacionTarea.BORRADOR, "La tarea debe estar en borrador para publicarse");
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
        long entregas = entregaRepository.countByTareaIdAndActivoTrue(id);
        String motivo = normalizar(motivoRecibido);
        if (entregas > 0 && motivo == null) {
            throw new BusinessRuleException(
                    "TAREA_MOTIVO_REQUERIDO",
                    "La tarea ya tiene entregas: indique el motivo de la eliminación", "motivo");
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
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        return tareaRepository.buscarVisibles(
                        asignacionCursoId,
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
        return resumirCeros(cerosPendientes(obtenerEntidad(id)));
    }

    @Transactional
    public CerosFaltantes confirmarCeros(Long id, int estudiantesEsperados) {
        var tarea = obtenerEntidad(id);
        entityManager.lock(tarea, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        if (tarea.getEvaluacionAcademica() != null) {
            entityManager.refresh(tarea.getEvaluacionAcademica(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        var notas = cerosPendientes(tarea);
        var resumen = resumirCeros(notas);
        if (resumen.estudiantes() != estudiantesEsperados) {
            throw new ConflictException("TAREA_FALTANTES_CAMBIARON", "Consulta de nuevo cuántos estudiantes faltan.");
        }
        registro.registrarLote(new CalificacionDto.CargaLoteRequest(notas));
        auditoria.registrar("CALIFICAR_TAREA_FALTANTES_CON_CERO", ModuloAuditoria.TAREAS, "Tarea",
                tarea.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "estudiantes=" + resumen.estudiantes() + ",calificaciones=" + resumen.calificaciones());
        return resumen;
    }

    public com.asiscontrol.dto.tarea.TareaDtos.SinEntregaDisponible sinEntrega(Long id) {
        var tarea = obtenerEntidad(id);
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
        return new com.asiscontrol.dto.tarea.TareaDtos.SinEntregaDisponible(alumnos, competencias, Math.min(20, tarea.getPuntajeMaximo()));
    }

    @Transactional
    public CerosFaltantes calificarSinEntrega(Long id, com.asiscontrol.dto.tarea.TareaDtos.SinEntregaRequest request) {
        var tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        entityManager.refresh(tarea, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (tarea.getEvaluacionAcademica() != null) {
            entityManager.refresh(tarea.getEvaluacionAcademica(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        if (request.nota().compareTo(BigDecimal.valueOf(Math.min(20, tarea.getPuntajeMaximo()))) > 0) {
            throw new BusinessRuleException("TAREA_NOTA_INVALIDA", "La nota supera el puntaje máximo de la tarea.");
        }
        var notas = cerosPendientes(tarea).stream()
                .filter(nota -> request.alumnos().contains(nota.idAlumno()) && request.competencias().contains(nota.idCompetencia()))
                .map(nota -> new CalificacionDto.Request(nota.idEvaluacion(), nota.idAlumno(), nota.idCompetencia(),
                        null, null, request.nota())).toList();
        if (notas.size() != (long) request.alumnos().size() * request.competencias().size()) {
            throw new ConflictException("TAREA_FALTANTES_CAMBIARON",
                    "Hay estudiantes o competencias que ya no admiten la calificación. Actualiza la selección.");
        }
        registro.registrarLote(new CalificacionDto.CargaLoteRequest(notas));
        var resumen = resumirCeros(notas);
        auditoria.registrar("CALIFICAR_TAREA_SIN_ENTREGA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, null,
                "nota=" + request.nota() + ",estudiantes=" + request.alumnos() + ",competencias=" + request.competencias());
        return resumen;
    }

    private List<CalificacionDto.Request> cerosPendientes(Tarea tarea) {
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        var evaluacion = tarea.getEvaluacionAcademica();
        if (evaluacion == null || !evaluacion.isActivo() || evaluacion.getEstado() != EstadoEvaluacion.BORRADOR
                || tarea.getEstadoPublicacion() != EstadoPublicacionTarea.CERRADA) {
            throw new BusinessRuleException("TAREA_CEROS_NO_DISPONIBLES",
                    "La tarea debe estar cerrada y su evaluación vinculada debe seguir en borrador.");
        }
        escalas.exigirVigente();
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
                tarea.getVersion(), tarea.isPuntajeMaximoFijo(), evaluacionAcademica(tarea));
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
        var cantidades = new HashMap<Long, Long>();
        if (academica.pesoComponente() != null) {
            calificaciones.contarPorComponente(tarea.getAsignacionCurso().getId(), evaluacion.getTipoEvaluacion()).stream()
                    .filter(item -> alumnoId.equals(item.getAlumno()))
                    .forEach(item -> cantidades.put(item.getCompetencia(), item.getCantidad()));
        }
        return calificaciones.findAllByEvaluacionIdAndAlumnoIdAndActivoTrue(evaluacion.getId(), alumnoId).stream()
                .filter(nota -> evaluacion.getCompetencias().contains(nota.getCompetencia()))
                .map(nota -> new ResultadoCompetencia(nota.getCompetencia().getNombre(), nota.getNivelLogro(),
                        academica.pesoComponente() == null ? null : ReglaCalificacionService.pesoEfectivo(
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
