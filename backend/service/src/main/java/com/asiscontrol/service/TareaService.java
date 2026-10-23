package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.tarea.TareaDtos.ActualizarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CrearTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.TareaResponse;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.TareaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class TareaService {

    private final TareaRepository tareaRepository;
    private final ArchivoStorageService archivoService;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public TareaService(
            TareaRepository tareaRepository,
            ArchivoStorageService archivoService,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.tareaRepository = tareaRepository;
        this.archivoService = archivoService;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
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
        tarea.setPuntajeMaximo(request.puntajeMaximo());
        tarea.setMaxArchivosEntrega(request.maxArchivosEntrega());
        tarea.setArchivos(archivoService.obtenerActivos(request.archivoIds()));
        tarea.setEstadoPublicacion(EstadoPublicacionTarea.BORRADOR);
        tarea.setActivo(true);
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("CREAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return mapResponse(saved);
    }

    @Transactional
    public TareaResponse actualizar(Long id, ActualizarTareaRequest request) {
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        exigirVersion(tarea.getVersion(), request.version());
        exigirEstado(tarea, EstadoPublicacionTarea.BORRADOR, "Solo se puede editar una tarea en borrador");
        validarFechas(request.fechaPublicacion(), request.fechaLimite());
        String anterior = describir(tarea);

        tarea.setNombre(request.nombre().trim());
        tarea.setDescripcion(request.descripcion().trim());
        tarea.setFechaPublicacion(request.fechaPublicacion());
        tarea.setFechaLimite(request.fechaLimite());
        tarea.setPuntajeMaximo(request.puntajeMaximo());
        tarea.setMaxArchivosEntrega(request.maxArchivosEntrega());
        tarea.setArchivos(archivoService.obtenerActivos(request.archivoIds()));
        Tarea saved = tareaRepository.save(tarea);
        auditoria.registrar("ACTUALIZAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return mapResponse(saved);
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

    @Transactional
    public void eliminar(Long id) {
        Tarea tarea = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(tarea.getAsignacionCurso());
        if (tarea.getEstadoPublicacion() != EstadoPublicacionTarea.BORRADOR
                && tarea.getEstadoPublicacion() != EstadoPublicacionTarea.ARCHIVADA) {
            throw new BusinessRuleException(
                    "TAREA_NO_ELIMINABLE", "Solo se puede eliminar lógicamente una tarea en borrador o archivada");
        }
        tarea.setActivo(false);
        tareaRepository.save(tarea);
        auditoria.registrar("ELIMINAR_TAREA", ModuloAuditoria.TAREAS, "Tarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, describir(tarea), null);
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
                tarea.getVersion());
    }

    private String describir(Tarea tarea) {
        return "nombre=" + tarea.getNombre()
                + ",asignacionCursoId=" + tarea.getAsignacionCurso().getId()
                + ",estado=" + tarea.getEstadoPublicacion()
                + ",fechaLimite=" + tarea.getFechaLimite()
                + ",puntajeMaximo=" + tarea.getPuntajeMaximo()
                + ",archivos=" + tarea.getArchivos().size();
    }

    private void validarFechas(LocalDateTime fechaPublicacion, LocalDateTime fechaLimite) {
        LocalDateTime referencia = fechaPublicacion == null ? LocalDateTime.now() : fechaPublicacion;
        if (!fechaLimite.isAfter(referencia)) {
            throw new BusinessRuleException(
                    "TAREA_RANGO_FECHAS_INVALIDO", "La fecha límite debe ser posterior a la fecha de publicación");
        }
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
