package com.asiscontrol.service;


import com.asiscontrol.service.ArchivoStorageService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.tarea.TareaDtos.ActualizarEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.CalificarEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.EntregaTareaResponse;
import com.asiscontrol.dto.tarea.TareaDtos.RegistrarEntregaRequest;
import com.asiscontrol.dto.tarea.TareaDtos.SeguimientoAlumno;
import com.asiscontrol.dto.tarea.TareaDtos.SeguimientoTareaResponse;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.entity.Tarea;
import com.asiscontrol.entity.enums.EstadoPublicacionTarea;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoTarea;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.EntregaTareaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

@Service
@Transactional(readOnly = true)
public class EntregaTareaService {

    private final EntregaTareaRepository entregaRepository;
    private final TareaService tareaService;
    private final ArchivoStorageService archivoService;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public EntregaTareaService(
            EntregaTareaRepository entregaRepository,
            TareaService tareaService,
            ArchivoStorageService archivoService,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.entregaRepository = entregaRepository;
        this.tareaService = tareaService;
        this.archivoService = archivoService;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
    }

    @Transactional
    public EntregaTareaResponse registrar(Long tareaId, RegistrarEntregaRequest request) {
        Tarea tarea = tareaService.obtenerEntidad(tareaId);
        validarRecepcion(tarea);
        Alumno alumno = buscarActivo(Alumno.class, request.alumnoId(), "ALUMNO_NO_ENCONTRADO", "alumno");
        accessGuard.verificarEntregaAlumno(alumno);
        validarMatriculaActiva(alumno.getId(), tarea.getAsignacionCurso().getSeccion().getId());
        if (entregaRepository.existsByTareaIdAndAlumnoIdAndActivoTrue(tareaId, alumno.getId())) {
            throw new ConflictException(
                    "ENTREGA_DUPLICADA", "El alumno ya posee una entrega activa para esta tarea");
        }
        validarCantidadArchivos(tarea, request.archivoIds().size());

        LocalDateTime ahora = LocalDateTime.now();
        EntregaTarea entrega = new EntregaTarea();
        entrega.setTarea(tarea);
        entrega.setAlumno(alumno);
        entrega.setFechaHoraEntrega(ahora);
        entrega.setArchivos(archivosDeEntrega(request.archivoIds()));
        entrega.setComentario(normalizar(request.comentario()));
        entrega.setEstado(ahora.isAfter(tarea.getFechaLimite())
                ? EstadoTarea.ENTREGADO_TARDE
                : EstadoTarea.ENTREGADO);
        entrega.setActivo(true);
        EntregaTarea saved = entregaRepository.save(entrega);
        auditoria.registrar("REGISTRAR_ENTREGA_TAREA", ModuloAuditoria.TAREAS, "EntregaTarea",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null, describir(saved));
        return mapResponse(saved);
    }

    @Transactional
    public EntregaTareaResponse actualizar(Long id, ActualizarEntregaRequest request) {
        EntregaTarea entrega = obtenerEntidad(id);
        accessGuard.verificarEntregaAlumno(entrega.getAlumno());
        exigirVersion(entrega.getVersion(), request.version());
        validarRecepcion(entrega.getTarea());
        validarMatriculaActiva(entrega.getAlumno().getId(), entrega.getTarea().getAsignacionCurso().getSeccion().getId());
        if (entrega.getEstado() == EstadoTarea.CALIFICADO) {
            throw new BusinessRuleException("ENTREGA_YA_CALIFICADA", "No se puede reemplazar una entrega calificada");
        }
        validarCantidadArchivos(entrega.getTarea(), request.archivoIds().size());
        String anterior = describir(entrega);
        LocalDateTime ahora = LocalDateTime.now();
        entrega.setFechaHoraEntrega(ahora);
        entrega.setArchivos(archivosDeEntrega(request.archivoIds()));
        // Los clientes que solo editan archivos no deben borrar comentarios existentes.
        if (request.comentario() != null) entrega.setComentario(normalizar(request.comentario()));
        entrega.setEstado(ahora.isAfter(entrega.getTarea().getFechaLimite())
                ? EstadoTarea.ENTREGADO_TARDE
                : EstadoTarea.ENTREGADO);
        EntregaTarea saved = entregaRepository.saveAndFlush(entrega);
        auditoria.registrar("ACTUALIZAR_ENTREGA_TAREA", ModuloAuditoria.TAREAS, "EntregaTarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return mapResponse(saved);
    }

    @Transactional
    public EntregaTareaResponse calificar(Long id, CalificarEntregaRequest request) {
        EntregaTarea entrega = obtenerEntidad(id);
        // Antes de cualquier otra validacion, para no revelar datos de entregas ajenas.
        accessGuard.verificarAsignacionDocente(entrega.getTarea().getAsignacionCurso());
        exigirVersion(entrega.getVersion(), request.version());
        EstadoPublicacionTarea estadoTarea = entrega.getTarea().getEstadoPublicacion();
        if (estadoTarea != EstadoPublicacionTarea.PUBLICADA
                && estadoTarea != EstadoPublicacionTarea.CERRADA) {
            throw new BusinessRuleException(
                    "TAREA_NO_CALIFICABLE",
                    "Solo se pueden calificar entregas de tareas publicadas o cerradas");
        }
        if (entrega.getEstado() == EstadoTarea.ANULADO) {
            throw new BusinessRuleException("ENTREGA_ANULADA", "No se puede calificar una entrega anulada");
        }
        if (request.nota() > entrega.getTarea().getPuntajeMaximo()) {
            throw new BusinessRuleException(
                    "NOTA_FUERA_DE_RANGO", "La nota no puede superar el puntaje máximo de la tarea");
        }
        String anterior = "estado=" + entrega.getEstado() + ",nota=" + entrega.getNota();
        entrega.setNota(request.nota());
        entrega.setComentario(normalizar(request.comentario()));
        entrega.setEstado(EstadoTarea.CALIFICADO);
        EntregaTarea saved = entregaRepository.saveAndFlush(entrega);
        auditoria.registrar("CALIFICAR_ENTREGA_TAREA", ModuloAuditoria.TAREAS, "EntregaTarea",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior,
                "estado=" + saved.getEstado() + ",nota=" + saved.getNota());
        return mapResponse(saved);
    }

    public EntregaTareaResponse obtener(Long id) {
        return mapResponse(obtenerEntidad(id));
    }

    public Page<EntregaTareaResponse> listarPorTarea(
            Long tareaId,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable
    ) {
        tareaService.obtenerEntidad(tareaId);
        return entregaRepository.buscarVisibles(
                        tareaId,
                        personaDocenteId,
                        personaAlumnoId,
                        personaApoderadoId,
                        pageable)
                .map(this::mapResponse);
    }

    /**
     * US-22: padron completo de la seccion con el estado de entrega de cada alumno matriculado.
     * Quien entrego y luego dejo la seccion se conserva en la lista, pero no suma al total.
     */
    public SeguimientoTareaResponse seguimiento(Long tareaId) {
        Tarea tarea = tareaService.obtenerEntidad(tareaId);
        Map<Long, EntregaTarea> entregas = new HashMap<>();
        for (EntregaTarea entrega : entregaRepository.findAllByTareaIdAndActivoTrue(tareaId)) {
            entregas.put(entrega.getAlumno().getId(), entrega);
        }
        List<Alumno> matriculados = entityManager.createQuery("""
                        SELECT DISTINCT m.alumno FROM Matricula m
                        JOIN FETCH m.alumno.persona
                        WHERE m.activo = true
                          AND m.estado = :estado
                          AND m.seccion.id = :seccionId
                        """, Alumno.class)
                .setParameter("estado", EstadoMatricula.ACTIVA)
                .setParameter("seccionId", tarea.getAsignacionCurso().getSeccion().getId())
                .getResultList();

        List<SeguimientoAlumno> filas = new ArrayList<>();
        Set<Long> enPadron = new HashSet<>();
        for (Alumno alumno : matriculados) {
            enPadron.add(alumno.getId());
            filas.add(filaSeguimiento(alumno, true, entregas.get(alumno.getId())));
        }
        for (EntregaTarea entrega : entregas.values()) {
            if (!enPadron.contains(entrega.getAlumno().getId())) {
                filas.add(filaSeguimiento(entrega.getAlumno(), false, entrega));
            }
        }
        filas.sort(Comparator.comparing(SeguimientoAlumno::nombreCompleto, String.CASE_INSENSITIVE_ORDER));

        int entregados = 0;
        int calificados = 0;
        for (SeguimientoAlumno fila : filas) {
            if (!fila.matriculaActiva()) {
                continue;
            }
            if (fila.estado() == EstadoTarea.CALIFICADO) {
                calificados++;
            }
            if (fila.estado() == EstadoTarea.ENTREGADO
                    || fila.estado() == EstadoTarea.ENTREGADO_TARDE
                    || fila.estado() == EstadoTarea.CALIFICADO) {
                entregados++;
            }
        }
        int total = matriculados.size();
        return new SeguimientoTareaResponse(tareaId, total, entregados, calificados, total - entregados, filas);
    }

    private SeguimientoAlumno filaSeguimiento(Alumno alumno, boolean matriculaActiva, EntregaTarea entrega) {
        return new SeguimientoAlumno(
                alumno.getId(),
                alumno.getCodigoAlumno(),
                alumno.getPersona().nombreCompleto(),
                matriculaActiva,
                entrega == null ? null : entrega.getId(),
                entrega == null ? EstadoTarea.PENDIENTE : entrega.getEstado(),
                entrega == null ? null : entrega.getFechaHoraEntrega(),
                entrega == null ? null : entrega.getNota());
    }

    private EntregaTarea obtenerEntidad(Long id) {
        return entregaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ENTREGA_NO_ENCONTRADA", "No existe una entrega activa con id " + id));
    }

    private void validarRecepcion(Tarea tarea) {
        if (tarea.getEstadoPublicacion() != EstadoPublicacionTarea.PUBLICADA) {
            throw new BusinessRuleException(
                    "TAREA_NO_RECIBE_ENTREGAS", "La tarea debe estar publicada y abierta para recibir entregas");
        }
        if (tarea.getFechaPublicacion() != null && LocalDateTime.now().isBefore(tarea.getFechaPublicacion())) {
            throw new BusinessRuleException("TAREA_AUN_NO_PUBLICADA", "La tarea todavía no está disponible");
        }
        if (!LocalDateTime.now().isBefore(tarea.getFechaLimite())) {
            throw new BusinessRuleException("TAREA_PLAZO_VENCIDO", "La fecha límite venció; la tarea ya no acepta entregas ni cambios");
        }
    }

    private Set<ArchivoAdjunto> archivosDeEntrega(Set<Long> ids) {
        var archivos = archivoService.obtenerActivos(ids);
        Long personaAlumnoId = accessGuard.restriccionAlumnoPersonaId();
        if (personaAlumnoId != null && archivos.stream().anyMatch(
                archivo -> !personaAlumnoId.equals(archivo.getCargadoPor().getId()))) {
            throw new AccessDeniedException("Solo puede entregar archivos cargados por usted");
        }
        return archivos;
    }

    private void validarCantidadArchivos(Tarea tarea, int cantidad) {
        if (cantidad > tarea.getMaxArchivosEntrega()) {
            throw new BusinessRuleException(
                    "ENTREGA_EXCEDE_ARCHIVOS", "La entrega excede el máximo de archivos permitido");
        }
    }

    private void validarMatriculaActiva(Long alumnoId, Long seccionId) {
        Long cantidad = entityManager.createQuery("""
                        SELECT COUNT(m) FROM Matricula m
                        WHERE m.activo = true
                          AND m.alumno.id = :alumnoId
                          AND m.seccion.id = :seccionId
                          AND m.estado = :estado
                        """, Long.class)
                .setParameter("alumnoId", alumnoId)
                .setParameter("seccionId", seccionId)
                .setParameter("estado", EstadoMatricula.ACTIVA)
                .getSingleResult();
        if (cantidad == 0) {
            throw new BusinessRuleException(
                    "ALUMNO_NO_MATRICULADO_EN_SECCION",
                    "El alumno no tiene una matrícula activa en la sección de la tarea");
        }
    }

    EntregaTareaResponse mapResponse(EntregaTarea entrega) {
        return new EntregaTareaResponse(
                entrega.getId(),
                entrega.getTarea().getId(),
                entrega.getAlumno().getId(),
                entrega.getFechaHoraEntrega(),
                entrega.getArchivos().stream().map(archivoService::mapResponse).toList(),
                entrega.getNota(),
                entrega.getComentario(),
                entrega.getEstado(),
                entrega.getVersion());
    }

    private String describir(EntregaTarea entrega) {
        return "tareaId=" + entrega.getTarea().getId()
                + ",alumnoId=" + entrega.getAlumno().getId()
                + ",estado=" + entrega.getEstado()
                + ",archivos=" + entrega.getArchivos().size();
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La entrega fue modificada por otro usuario; vuelva a cargarla");
        }
    }

    private <T extends AuditableEntity> T buscarActivo(
            Class<T> tipo, Long id, String codigo, String nombreRecurso) {
        T entidad = entityManager.find(tipo, id);
        if (entidad == null || !entidad.isActivo()) {
            throw new ResourceNotFoundException(codigo, "No existe un " + nombreRecurso + " activo con id " + id);
        }
        return entidad;
    }
}
