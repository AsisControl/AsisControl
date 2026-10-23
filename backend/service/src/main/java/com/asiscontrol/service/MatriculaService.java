package com.asiscontrol.service;

import com.asiscontrol.dto.matricula.MatriculaDtos.ActualizarMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.MatriculaResponse;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.repository.SolicitudMatriculaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final SolicitudMatriculaRepository solicitudRepository;
    private final SeccionRepository seccionRepository;
    private final ConfiguracionAcademicaService configuracion;
    private final com.asiscontrol.repository.PersonaRepository personas;
    private final AuditoriaService auditoria;

    public MatriculaService(
            MatriculaRepository matriculaRepository,
            SolicitudMatriculaRepository solicitudRepository,
            SeccionRepository seccionRepository,
            ConfiguracionAcademicaService configuracion,
            com.asiscontrol.repository.PersonaRepository personas,
            AuditoriaService auditoria) {
        this.matriculaRepository = matriculaRepository;
        this.solicitudRepository = solicitudRepository;
        this.seccionRepository = seccionRepository;
        this.configuracion = configuracion;
        this.personas = personas;
        this.auditoria = auditoria;
    }

    // El bloqueo serializa la última vacante; READ_COMMITTED evita contar una instantánea anterior
    // en MySQL.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MatriculaResponse crear(CrearMatriculaRequest request) {
        SolicitudMatricula vista =
                solicitudRepository
                        .findByIdAndActivoTrue(request.solicitudMatriculaId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SOLICITUD_NO_ENCONTRADA",
                                                "No se encontró la solicitud"));
        if (vista.getSeccion() == null)
            throw new BusinessRuleException(
                    "SECCION_REQUERIDA", "Asigne una sección antes de finalizar la matrícula");
        configuracion.bloquear(vista.getAnioAcademico());
        configuracion.exigirOferta(
                vista.getSeccion().getAnioAcademico(), vista.getSeccion().getGrado(), true);
        personas.lockActiveById(vista.getAlumno().getPersona().getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "ALUMNO_INACTIVO", "El estudiante no está habilitado"));
        SolicitudMatricula solicitud =
                solicitudRepository
                        .buscarActivaParaActualizar(request.solicitudMatriculaId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SOLICITUD_MATRICULA_NO_ENCONTRADA",
                                                "No existe la solicitud de matricula indicada"));
        exigirVersion(solicitud.getVersion(), request.versionSolicitud(), "solicitud");
        if (solicitud.getEstado() != EstadoSolicitudMatricula.EN_REVISION) {
            throw new BusinessRuleException(
                    "SOLICITUD_NO_FINALIZABLE",
                    "La solicitud debe estar en revision para crear la matricula");
        }
        if (matriculaRepository
                .findBySolicitudMatriculaIdAndActivoTrue(solicitud.getId())
                .isPresent()) {
            throw new ConflictException(
                    "SOLICITUD_YA_FINALIZADA", "La solicitud ya genero una matricula");
        }

        Seccion seccion =
                seccionRepository
                        .buscarActivaParaActualizar(solicitud.getSeccion().getId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SECCION_NO_ENCONTRADA",
                                                "La seccion de la solicitud ya no esta"
                                                        + " disponible"));
        long ocupados =
                matriculaRepository.countBySeccionIdAndEstadoAndActivoTrue(
                        seccion.getId(), EstadoMatricula.ACTIVA);
        if (ocupados >= seccion.getCapacidadMaxima()) {
            throw new BusinessRuleException(
                    "SECCION_SIN_VACANTES", "La seccion ya alcanzo su capacidad maxima");
        }
        if (matriculaRepository.existsByAlumnoIdAndAnioAcademicoAndEstadoAndActivoTrue(
                solicitud.getAlumno().getId(),
                seccion.getAnioAcademico(),
                EstadoMatricula.ACTIVA)) {
            throw new ConflictException(
                    "ALUMNO_YA_MATRICULADO",
                    "El alumno ya tiene una matricula para ese anio academico");
        }

        Instant ahora = Instant.now();
        Matricula matricula = new Matricula();
        matricula.setCodigoMatricula(generarCodigo(seccion.getAnioAcademico()));
        matricula.setSolicitudMatricula(solicitud);
        matricula.setAlumno(solicitud.getAlumno());
        matricula.setSeccion(seccion);
        matricula.setAnioAcademico(seccion.getAnioAcademico());
        matricula.setFechaMatricula(ahora);
        matricula.setEstado(EstadoMatricula.ACTIVA);
        matricula.setActivo(true);

        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        solicitud.setFechaRevision(ahora);
        solicitudRepository.saveAndFlush(solicitud);
        matriculaRepository.saveAndFlush(matricula);
        auditoria.registrar(
                "FINALIZAR_SOLICITUD",
                ModuloAuditoria.MATRICULA,
                "SolicitudMatricula",
                solicitud.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                "Aprobación administrativa",
                "EN_REVISION",
                "MATRICULA_FINALIZADA");
        auditoria.registrar(
                "CREAR_MATRICULA",
                ModuloAuditoria.MATRICULA,
                "Matricula",
                matricula.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                "Aprobación administrativa",
                null,
                "estado=ACTIVA,seccion=" + seccion.getId());
        return mapear(matricula);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MatriculaResponse actualizar(Long id, ActualizarMatriculaRequest request) {
        Matricula matricula = obtenerEntidad(id);
        configuracion.bloquear(matricula.getAnioAcademico());
        if (configuracion.buscar(matricula.getAnioAcademico()).getEstado()
                == com.asiscontrol.entity.enums.EstadoAnioAcademico.CERRADO)
            throw new BusinessRuleException(
                    "ANIO_CERRADO", "El año cerrado admite solo consulta histórica");
        exigirVersion(matricula.getVersion(), request.version(), "matricula");
        if (matricula.getEstado() == request.estado()) {
            return mapear(matricula);
        }
        if (matricula.getEstado() != EstadoMatricula.ACTIVA
                || (request.estado() != EstadoMatricula.RETIRADA
                        && request.estado() != EstadoMatricula.FINALIZADA
                        && request.estado() != EstadoMatricula.ANULADA)) {
            throw new BusinessRuleException(
                    "MATRICULA_TRANSICION_INVALIDA",
                    "No se permite pasar de " + matricula.getEstado() + " a " + request.estado());
        }
        if (request.motivo() == null || request.motivo().isBlank())
            throw new BusinessRuleException(
                    "MOTIVO_REQUERIDO", "Registre el motivo del cambio de estado");
        String anterior = matricula.getEstado().name();
        seccionRepository.buscarActivaParaActualizar(matricula.getSeccion().getId()).orElseThrow();
        matricula.setEstado(request.estado());
        matriculaRepository.saveAndFlush(matricula);
        auditoria.registrar(
                "CAMBIAR_ESTADO_MATRICULA",
                ModuloAuditoria.MATRICULA,
                "Matricula",
                id.toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                request.motivo().trim(),
                anterior,
                request.estado().name());
        return mapear(matricula);
    }

    public MatriculaResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<MatriculaResponse> listar(
            Long personaAlumnoId, Long personaApoderadoId, Pageable pageable) {
        return matriculaRepository
                .buscarVisibles(personaAlumnoId, personaApoderadoId, pageable)
                .map(this::mapear);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void eliminar(Long id) {
        throw new BusinessRuleException(
                "HISTORIAL_PROTEGIDO",
                "Use el cambio de estado con motivo; la matrícula y su historia se conservan");
    }

    private Matricula obtenerEntidad(Long id) {
        return matriculaRepository
                .findByIdAndActivoTrue(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "MATRICULA_NO_ENCONTRADA",
                                        "No existe una matricula activa con id " + id));
    }

    private String generarCodigo(int anio) {
        return "MAT-" + anio + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private MatriculaResponse mapear(Matricula matricula) {
        return new MatriculaResponse(
                matricula.getId(),
                matricula.getCodigoMatricula(),
                matricula.getSolicitudMatricula().getId(),
                matricula.getAlumno().getId(),
                matricula.getAlumno().getCodigoAlumno(),
                matricula.getAlumno().getPersona().nombreCompleto(),
                matricula.getSeccion().getId(),
                matricula.getSeccion().getNombre(),
                matricula.getSeccion().getGrado(),
                matricula.getAnioAcademico(),
                matricula.getFechaMatricula(),
                matricula.getEstado(),
                matricula.isActivo(),
                matricula.getVersion());
    }

    private void exigirVersion(long actual, Long recibida, String recurso) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA",
                    "La " + recurso + " fue modificada; vuelva a cargarla");
        }
    }
}
