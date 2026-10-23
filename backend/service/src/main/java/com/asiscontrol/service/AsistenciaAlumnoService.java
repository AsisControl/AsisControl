package com.asiscontrol.service;


import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.ActualizarAsistenciaAlumnoRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaAlumnoItemRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaAlumnoResponse;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RegistrarAsistenciaAlumnoLoteRequest;
import com.asiscontrol.dto.asistencia.AsistenciaDtos.RegistrarAsistenciaAlumnoRequest;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.AuditableEntity;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AsistenciaAlumnoService {

    private static final Set<EstadoAsistencia> ESTADOS_REGISTRO_DIRECTO = Set.of(
            EstadoAsistencia.PRESENTE,
            EstadoAsistencia.TARDANZA,
            EstadoAsistencia.INASISTENCIA,
            EstadoAsistencia.SIN_MARCACION);

    private final AsistenciaAlumnoRepository asistenciaRepository;
    private final EntityManager entityManager;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public AsistenciaAlumnoService(
            AsistenciaAlumnoRepository asistenciaRepository,
            EntityManager entityManager,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.asistenciaRepository = asistenciaRepository;
        this.entityManager = entityManager;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
    }

    @Transactional
    public AsistenciaAlumnoResponse registrar(RegistrarAsistenciaAlumnoRequest request) {
        validarFecha(request.fechaRegistro());
        validarEstadoDirecto(request.estado());
        AsignacionCurso asignacion = buscarActivo(
                AsignacionCurso.class, request.asignacionCursoId(), "ASIGNACION_CURSO_NO_ENCONTRADA", "asignación de curso");
        accessGuard.verificarAsignacionDocente(asignacion);
        Alumno alumno = buscarActivo(Alumno.class, request.alumnoId(), "ALUMNO_NO_ENCONTRADO", "alumno");
        Docente registrador = resolverRegistrador(asignacion, request.docenteRegistradorId());
        return mapResponse(registrarEntidad(
                alumno,
                asignacion,
                registrador,
                request.fechaRegistro(),
                request.horaRegistro(),
                request.estado(),
                request.comentario()));
    }

    @Transactional
    public List<AsistenciaAlumnoResponse> registrarLote(RegistrarAsistenciaAlumnoLoteRequest request) {
        validarFecha(request.fechaRegistro());
        validarDuplicadosEnLote(request.asistencias());
        AsignacionCurso asignacion = buscarActivo(
                AsignacionCurso.class, request.asignacionCursoId(), "ASIGNACION_CURSO_NO_ENCONTRADA", "asignación de curso");
        accessGuard.verificarAsignacionDocente(asignacion);
        Docente registrador = resolverRegistrador(asignacion, request.docenteRegistradorId());

        for (AsistenciaAlumnoItemRequest item : request.asistencias()) {
            validarEstadoDirecto(item.estado());
            if (asistenciaRepository.findByAlumnoIdAndAsignacionCursoIdAndFechaRegistroAndActivoTrue(
                    item.alumnoId(), request.asignacionCursoId(), request.fechaRegistro()).isPresent()) {
                throw new ConflictException(
                        "ASISTENCIA_DUPLICADA",
                        "Ya existe asistencia para el alumno " + item.alumnoId() + " en la fecha indicada");
            }
        }

        return request.asistencias().stream().map(item -> {
            Alumno alumno = buscarActivo(Alumno.class, item.alumnoId(), "ALUMNO_NO_ENCONTRADO", "alumno");
            return mapResponse(registrarEntidad(
                    alumno,
                    asignacion,
                    registrador,
                    request.fechaRegistro(),
                    item.horaRegistro(),
                    item.estado(),
                    item.comentario()));
        }).toList();
    }

    @Transactional
    public AsistenciaAlumnoResponse actualizar(Long id, ActualizarAsistenciaAlumnoRequest request) {
        AsistenciaAlumno asistencia = obtenerEntidad(id);
        accessGuard.verificarAsignacionDocente(asistencia.getAsignacionCurso());
        exigirVersion(asistencia.getVersion(), request.version());
        validarEstadoDirecto(request.estado());
        asistencia.setEstadoAsistencia(request.estado());
        asistencia.setComentario(normalizar(request.comentario()));
        AsistenciaAlumno saved = asistenciaRepository.save(asistencia);
        auditoria.registrar("ACTUALIZAR_ASISTENCIA_ALUMNO", "ASISTENCIA", "AsistenciaAlumno",
                id.toString(), ResultadoAuditoria.EXITOSO, null, null,
                "estado=" + request.estado());
        return mapResponse(saved);
    }

    public AsistenciaAlumnoResponse obtener(Long id) {
        return mapResponse(obtenerEntidad(id));
    }

    public Page<AsistenciaAlumnoResponse> listar(
            Long alumnoId,
            Long asignacionCursoId,
            LocalDate fechaRegistro,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable) {
        return asistenciaRepository.buscarActivas(
                        alumnoId,
                        asignacionCursoId,
                        fechaRegistro,
                        personaDocenteId,
                        personaAlumnoId,
                        personaApoderadoId,
                        pageable)
                .map(this::mapResponse);
    }

    public AsistenciaAlumno obtenerEntidad(Long id) {
        return asistenciaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASISTENCIA_ALUMNO_NO_ENCONTRADA", "No existe una asistencia activa con id " + id));
    }

    private AsistenciaAlumno registrarEntidad(
            Alumno alumno,
            AsignacionCurso asignacion,
            Docente registrador,
            LocalDate fecha,
            LocalTime hora,
            EstadoAsistencia estado,
            String comentario) {
        validarMatriculaActiva(alumno.getId(), asignacion.getSeccion().getId());
        if (asistenciaRepository.findByAlumnoIdAndAsignacionCursoIdAndFechaRegistroAndActivoTrue(
                alumno.getId(), asignacion.getId(), fecha).isPresent()) {
            throw new ConflictException(
                    "ASISTENCIA_DUPLICADA", "Ya existe asistencia del alumno para la asignación y fecha indicadas");
        }
        AsistenciaAlumno asistencia = new AsistenciaAlumno();
        asistencia.setAlumno(alumno);
        asistencia.setAsignacionCurso(asignacion);
        asistencia.setDocenteRegistrador(registrador);
        asistencia.setFechaRegistro(fecha);
        asistencia.setHoraRegistro(hora == null ? LocalTime.now() : hora);
        asistencia.setEstadoAsistencia(estado);
        asistencia.setComentario(normalizar(comentario));
        asistencia.setActivo(true);
        AsistenciaAlumno saved = asistenciaRepository.save(asistencia);
        auditoria.registrar("REGISTRAR_ASISTENCIA_ALUMNO", "ASISTENCIA", "AsistenciaAlumno",
                saved.getId().toString(), ResultadoAuditoria.EXITOSO, null, null,
                "alumno=" + alumno.getId() + ",estado=" + estado + ",fecha=" + fecha);
        return saved;
    }

    private void validarFecha(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "ASISTENCIA_FECHA_FUTURA", "No se puede registrar asistencia para una fecha futura");
        }
    }

    private void validarEstadoDirecto(EstadoAsistencia estado) {
        if (!ESTADOS_REGISTRO_DIRECTO.contains(estado)) {
            throw new BusinessRuleException(
                    "ASISTENCIA_ESTADO_RESERVADO",
                    "Los estados justificados solo se asignan al aprobar una justificación");
        }
    }

    private void validarDuplicadosEnLote(List<AsistenciaAlumnoItemRequest> items) {
        Set<Long> alumnos = new HashSet<>();
        for (AsistenciaAlumnoItemRequest item : items) {
            if (!alumnos.add(item.alumnoId())) {
                throw new ConflictException(
                        "ASISTENCIA_LOTE_DUPLICADO", "El lote contiene al alumno " + item.alumnoId() + " más de una vez");
            }
        }
    }

    private Docente resolverRegistrador(AsignacionCurso asignacion, Long docenteSolicitadoId) {
        Docente asignado = asignacion.getDocente();
        if (docenteSolicitadoId != null && !asignado.getId().equals(docenteSolicitadoId)) {
            throw new BusinessRuleException(
                    "DOCENTE_NO_ASIGNADO",
                    "El docente registrador debe ser el docente asignado al curso y sección");
        }
        return asignado;
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
                    "El alumno no tiene una matrícula activa en la sección de la asignación");
        }
    }

    private AsistenciaAlumnoResponse mapResponse(AsistenciaAlumno asistencia) {
        return new AsistenciaAlumnoResponse(
                asistencia.getId(),
                asistencia.getAlumno().getId(),
                asistencia.getAsignacionCurso().getId(),
                asistencia.getDocenteRegistrador() == null ? null : asistencia.getDocenteRegistrador().getId(),
                asistencia.getFechaRegistro(),
                asistencia.getHoraRegistro(),
                asistencia.getEstadoAsistencia(),
                asistencia.getComentario(),
                asistencia.getVersion());
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La asistencia fue modificada por otro usuario; vuelva a cargarla");
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
