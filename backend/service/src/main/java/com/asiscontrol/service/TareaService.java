package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.tarea.TareaDtos.ActualizarTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CrearTareaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.TareaResponse;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.EntregaTareaRepository;
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
    private final EntregaTareaRepository entregaRepository;
    private final ArchivoStorageService archivoService;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public TareaService(
            TareaRepository tareaRepository,
            EntregaTareaRepository entregaRepository,
            ArchivoStorageService archivoService,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.tareaRepository = tareaRepository;
        this.entregaRepository = entregaRepository;
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
        Integer notaMaxima = entregaRepository.maxNotaPorTarea(tarea.getId());
        if (notaMaxima != null && request.puntajeMaximo() < notaMaxima) {
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
