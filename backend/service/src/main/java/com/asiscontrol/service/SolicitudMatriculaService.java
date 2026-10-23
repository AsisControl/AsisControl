package com.asiscontrol.service;

import com.asiscontrol.dto.matricula.MatriculaDtos.ActualizarSolicitudMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearSolicitudMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.SolicitudMatriculaResponse;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.repository.SolicitudMatriculaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SolicitudMatriculaService {

    private static final Set<EstadoSolicitudMatricula> ESTADOS_ABIERTOS =
            EnumSet.of(
                    EstadoSolicitudMatricula.BORRADOR,
                    EstadoSolicitudMatricula.ENVIADA,
                    EstadoSolicitudMatricula.EN_REVISION,
                    EstadoSolicitudMatricula.OBSERVADA);

    @org.springframework.beans.factory.annotation.Autowired private ModuloAccessGuard guard;
    private final SolicitudMatriculaRepository solicitudRepository;
    private final AlumnoRepository alumnoRepository;
    private final SeccionRepository seccionRepository;
    private final MatriculaRepository matriculaRepository;
    private final ConfiguracionAcademicaService configuracion;
    private final com.asiscontrol.repository.PersonaRepository personas;
    private final AuditoriaService auditoria;

    public SolicitudMatriculaService(
            SolicitudMatriculaRepository solicitudRepository,
            AlumnoRepository alumnoRepository,
            SeccionRepository seccionRepository,
            MatriculaRepository matriculaRepository,
            ConfiguracionAcademicaService configuracion,
            com.asiscontrol.repository.PersonaRepository personas,
            AuditoriaService auditoria) {
        this.solicitudRepository = solicitudRepository;
        this.alumnoRepository = alumnoRepository;
        this.seccionRepository = seccionRepository;
        this.matriculaRepository = matriculaRepository;
        this.configuracion = configuracion;
        this.personas = personas;
        this.auditoria = auditoria;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SolicitudMatriculaResponse crear(CrearSolicitudMatriculaRequest request) {
        Alumno alumno = buscarAlumno(request.alumnoId());
        boolean familiar = esApoderado();
        if (familiar && (!guard.puedeAccederAlumno(alumno.getId()) || request.seccionId() != null))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo puede solicitar para sus estudiantes asociados; el administrador asigna"
                        + " la sección");
        Seccion seccion = request.seccionId() == null ? null : buscarSeccion(request.seccionId());
        if (seccion == null && (request.anioAcademico() == null || request.grado() == null))
            throw new BusinessRuleException("OFERTA_REQUERIDA", "Seleccione año y grado");
        int anio = seccion == null ? request.anioAcademico() : seccion.getAnioAcademico();
        int grado = seccion == null ? request.grado() : seccion.getGrado();
        configuracion.bloquear(anio);
        configuracion.exigirOferta(anio, grado, true);
        if (familiar) configuracion.exigirAdmision(anio, grado);
        personas.lockActiveById(alumno.getPersona().getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "ALUMNO_INACTIVO", "El estudiante no está habilitado"));
        if (familiar
                && solicitudRepository.contarSolicitudesEnEstados(
                                alumno.getId(),
                                anio,
                                EnumSet.complementOf(
                                        EnumSet.of(EstadoSolicitudMatricula.CANCELADA)))
                        > 0)
            throw new ConflictException(
                    "SOLICITUD_MATRICULA_DUPLICADA",
                    "El estudiante ya tiene una solicitud no cancelada para ese año. Consulte a la"
                        + " administración");
        validarDisponibilidadSolicitud(alumno.getId(), anio, 0);
        validarAlumnoSinMatricula(alumno.getId(), anio);
        SolicitudMatricula solicitud = new SolicitudMatricula();
        solicitud.setAlumno(alumno);
        solicitud.setSeccion(seccion);
        solicitud.setAnioAcademico(anio);
        solicitud.setGrado(grado);
        solicitud.setEstado(EstadoSolicitudMatricula.BORRADOR);
        solicitud.setObservaciones(normalizar(request.observaciones()));
        solicitud.setActivo(true);
        solicitudRepository.saveAndFlush(solicitud);
        auditoria.registrar(
                "GUARDAR_SOLICITUD",
                ModuloAuditoria.MATRICULA,
                "SolicitudMatricula",
                solicitud.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                solicitud.getMotivoRechazo(),
                null,
                "estado="
                        + solicitud.getEstado()
                        + ",seccion="
                        + (solicitud.getSeccion() == null ? null : solicitud.getSeccion().getId()));
        return mapear(solicitud);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SolicitudMatriculaResponse actualizar(
            Long id, ActualizarSolicitudMatriculaRequest request) {
        SolicitudMatricula vista = obtenerEntidad(id);
        boolean familiar = esApoderado();
        if (familiar) {
            if (!guard.puedeAccederAlumno(vista.getAlumno().getId()))
                throw new org.springframework.security.access.AccessDeniedException(
                        "La solicitud no pertenece a un estudiante asociado");
            if (request.seccionId() != null
                    || request.motivoRechazo() != null
                    || (request.estado() != null
                            && request.estado() != vista.getEstado()
                            && request.estado() != EstadoSolicitudMatricula.ENVIADA
                            && request.estado() != EstadoSolicitudMatricula.CANCELADA))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Solo puede editar, enviar o cancelar su solicitud");
        }
        configuracion.bloquear(vista.getAnioAcademico());
        configuracion.exigirOferta(vista.getAnioAcademico(), vista.getGrado(), true);
        if (familiar && request.estado() == EstadoSolicitudMatricula.ENVIADA)
            configuracion.exigirAdmision(vista.getAnioAcademico(), vista.getGrado());
        personas.lockActiveById(vista.getAlumno().getPersona().getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "ALUMNO_INACTIVO", "El estudiante no está habilitado"));
        SolicitudMatricula solicitud =
                solicitudRepository.buscarActivaParaActualizar(id).orElseThrow();
        String anterior =
                "estado="
                        + solicitud.getEstado()
                        + ",seccion="
                        + (solicitud.getSeccion() == null ? null : solicitud.getSeccion().getId());
        exigirVersion(solicitud.getVersion(), request.version());

        if (request.seccionId() != null
                && !request.seccionId()
                        .equals(
                                (solicitud.getSeccion() == null
                                        ? null
                                        : solicitud.getSeccion().getId()))) {
            if (solicitud.getEstado() != EstadoSolicitudMatricula.EN_REVISION)
                exigirEditable(solicitud);
            Seccion nuevaSeccion = buscarSeccion(request.seccionId());
            if (nuevaSeccion.getAnioAcademico() != solicitud.getAnioAcademico()
                    || nuevaSeccion.getGrado() != solicitud.getGrado())
                throw new BusinessRuleException(
                        "SECCION_GRADO_INVALIDO",
                        "La sección debe pertenecer al mismo año y grado solicitado");
            configuracion.exigirOferta(
                    nuevaSeccion.getAnioAcademico(), nuevaSeccion.getGrado(), true);
            long permitidas =
                    nuevaSeccion.getAnioAcademico() == solicitud.getAnioAcademico()
                                    && ESTADOS_ABIERTOS.contains(solicitud.getEstado())
                            ? 1
                            : 0;
            validarDisponibilidadSolicitud(
                    solicitud.getAlumno().getId(), nuevaSeccion.getAnioAcademico(), permitidas);
            validarAlumnoSinMatricula(
                    solicitud.getAlumno().getId(), nuevaSeccion.getAnioAcademico());
            solicitud.setSeccion(nuevaSeccion);
        }

        if (request.estado() == null || request.estado() == solicitud.getEstado()) {
            if (solicitud.getEstado() != EstadoSolicitudMatricula.EN_REVISION)
                exigirEditable(solicitud);
            if (solicitud.getEstado() == EstadoSolicitudMatricula.EN_REVISION
                    && request.seccionId() == null)
                throw new BusinessRuleException(
                        "SOLICITUD_NO_EDITABLE",
                        "En revisión, utilice un cambio de estado o seleccione sección");
            solicitud.setObservaciones(normalizar(request.observaciones()));
            solicitudRepository.saveAndFlush(solicitud);
            auditoria.registrar(
                    "GUARDAR_SOLICITUD",
                    ModuloAuditoria.MATRICULA,
                    "SolicitudMatricula",
                    solicitud.getId().toString(),
                    com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                    request.motivoRechazo() != null
                            ? request.motivoRechazo()
                            : request.observaciones(),
                    anterior,
                    "estado="
                            + solicitud.getEstado()
                            + ",seccion="
                            + (solicitud.getSeccion() == null
                                    ? null
                                    : solicitud.getSeccion().getId()));
            return mapear(solicitud);
        }

        transicionar(solicitud, request.estado(), request.observaciones(), request.motivoRechazo());
        solicitudRepository.saveAndFlush(solicitud);
        auditoria.registrar(
                "GUARDAR_SOLICITUD",
                ModuloAuditoria.MATRICULA,
                "SolicitudMatricula",
                solicitud.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                request.motivoRechazo() != null ? request.motivoRechazo() : request.observaciones(),
                anterior,
                "estado="
                        + solicitud.getEstado()
                        + ",seccion="
                        + (solicitud.getSeccion() == null ? null : solicitud.getSeccion().getId()));
        return mapear(solicitud);
    }

    public SolicitudMatriculaResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<SolicitudMatriculaResponse> listar(
            Long personaAlumnoId, Long personaApoderadoId, Pageable pageable) {
        return solicitudRepository
                .buscarVisibles(personaAlumnoId, personaApoderadoId, pageable)
                .map(this::mapear);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void eliminar(Long id) {
        throw new BusinessRuleException(
                "HISTORIAL_PROTEGIDO",
                "Cancele o rechace la solicitud con motivo; su historia se conserva");
    }

    private SolicitudMatricula obtenerEntidad(Long id) {
        return solicitudRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "SOLICITUD_MATRICULA_NO_ENCONTRADA",
                                        "No existe una solicitud de matricula activa con id "
                                                + id));
    }

    private Alumno buscarAlumno(Long id) {
        return alumnoRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "ALUMNO_NO_ENCONTRADO",
                                        "No existe un alumno activo con id " + id));
    }

    private Seccion buscarSeccion(Long id) {
        return seccionRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "SECCION_NO_ENCONTRADA",
                                        "No existe una seccion activa con id " + id));
    }

    private void transicionar(
            SolicitudMatricula solicitud,
            EstadoSolicitudMatricula destino,
            String observaciones,
            String motivoRechazo) {
        EstadoSolicitudMatricula origen = solicitud.getEstado();
        boolean permitida =
                switch (origen) {
                    case BORRADOR ->
                            destino == EstadoSolicitudMatricula.ENVIADA
                                    || destino == EstadoSolicitudMatricula.CANCELADA;
                    case ENVIADA ->
                            destino == EstadoSolicitudMatricula.EN_REVISION
                                    || destino == EstadoSolicitudMatricula.CANCELADA;
                    case EN_REVISION ->
                            destino == EstadoSolicitudMatricula.OBSERVADA
                                    || destino == EstadoSolicitudMatricula.RECHAZADA;
                    case OBSERVADA ->
                            destino == EstadoSolicitudMatricula.ENVIADA
                                    || destino == EstadoSolicitudMatricula.CANCELADA;
                    case RECHAZADA, MATRICULA_FINALIZADA, CANCELADA -> false;
                };
        if (!permitida) {
            if (destino == EstadoSolicitudMatricula.MATRICULA_FINALIZADA) {
                throw new BusinessRuleException(
                        "SOLICITUD_FINALIZACION_REQUIERE_MATRICULA",
                        "La solicitud solo se finaliza mediante la creacion atomica de la"
                                + " matricula");
            }
            throw new BusinessRuleException(
                    "SOLICITUD_TRANSICION_INVALIDA",
                    "No se permite pasar de " + origen + " a " + destino);
        }

        if (destino == EstadoSolicitudMatricula.CANCELADA && normalizar(observaciones) == null)
            throw new BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Registre el motivo de cancelación en las observaciones");
        if (destino == EstadoSolicitudMatricula.CANCELADA)
            solicitud.setObservaciones(normalizar(observaciones));
        Instant ahora = Instant.now();
        if (destino == EstadoSolicitudMatricula.ENVIADA) {
            validarAlumnoSinMatricula(solicitud.getAlumno().getId(), solicitud.getAnioAcademico());
            solicitud.setFechaEnvio(ahora);
            solicitud.setMotivoRechazo(null);
        } else if (destino == EstadoSolicitudMatricula.EN_REVISION) {
            solicitud.setFechaRevision(ahora);
        } else if (destino == EstadoSolicitudMatricula.OBSERVADA) {
            String detalle = normalizar(observaciones);
            if (detalle == null) {
                throw new BusinessRuleException(
                        "SOLICITUD_OBSERVACION_REQUERIDA",
                        "Debe indicar las observaciones que el solicitante debe subsanar");
            }
            solicitud.setObservaciones(detalle);
            solicitud.setFechaRevision(ahora);
        } else if (destino == EstadoSolicitudMatricula.RECHAZADA) {
            String motivo = normalizar(motivoRechazo);
            if (motivo == null) {
                throw new BusinessRuleException(
                        "SOLICITUD_MOTIVO_RECHAZO_REQUERIDO",
                        "Debe registrar el motivo del rechazo");
            }
            solicitud.setMotivoRechazo(motivo);
            solicitud.setFechaRevision(ahora);
        }
        solicitud.setEstado(destino);
    }

    private void exigirEditable(SolicitudMatricula solicitud) {
        if (solicitud.getEstado() != EstadoSolicitudMatricula.BORRADOR
                && solicitud.getEstado() != EstadoSolicitudMatricula.OBSERVADA) {
            throw new BusinessRuleException(
                    "SOLICITUD_NO_EDITABLE",
                    "Solo puede editarse una solicitud en borrador u observada");
        }
    }

    private void validarDisponibilidadSolicitud(Long alumnoId, int anio, long permitidas) {
        long cantidad =
                solicitudRepository.contarSolicitudesEnEstados(alumnoId, anio, ESTADOS_ABIERTOS);
        if (cantidad > permitidas) {
            throw new ConflictException(
                    "SOLICITUD_MATRICULA_DUPLICADA",
                    "El alumno ya tiene una solicitud abierta para ese anio academico");
        }
    }

    private void validarAlumnoSinMatricula(Long alumnoId, int anio) {
        if (matriculaRepository.existsByAlumnoIdAndAnioAcademicoAndEstadoAndActivoTrue(
                alumnoId, anio, com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA)) {
            throw new ConflictException(
                    "ALUMNO_YA_MATRICULADO",
                    "El alumno ya tiene una matricula para ese anio academico");
        }
    }

    private SolicitudMatriculaResponse mapear(SolicitudMatricula solicitud) {
        return new SolicitudMatriculaResponse(
                solicitud.getId(),
                solicitud.getAlumno().getId(),
                solicitud.getAlumno().getCodigoAlumno(),
                solicitud.getAlumno().getPersona().nombreCompleto(),
                (solicitud.getSeccion() == null ? null : solicitud.getSeccion().getId()),
                solicitud.getSeccion() == null
                        ? "Pendiente de asignación"
                        : solicitud.getSeccion().getNombre(),
                solicitud.getGrado(),
                solicitud.getAnioAcademico(),
                solicitud.getEstado(),
                solicitud.getFechaEnvio(),
                solicitud.getFechaRevision(),
                solicitud.getObservaciones(),
                solicitud.getMotivoRechazo(),
                solicitud.isActivo(),
                solicitud.getVersion());
    }

    private boolean esApoderado() {
        var actor =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        return actor != null
                && actor.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_APODERADO"));
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La solicitud fue modificada; vuelva a cargarla");
        }
    }
}
