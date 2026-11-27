package com.asiscontrol.service;

import com.asiscontrol.dto.matricula.MatriculaDtos.ActualizarMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CambiarSeccionMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.FiltroMatriculas;
import com.asiscontrol.dto.matricula.MatriculaDtos.MatriculaDirectaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.MatriculaResponse;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.HistorialSeccionMatricula;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.HistorialSeccionMatriculaRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.repository.SolicitudMatriculaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MatriculaService {

    private static final List<EstadoSolicitudMatricula> SOLICITUDES_EN_TRAMITE =
            List.of(
                    EstadoSolicitudMatricula.BORRADOR,
                    EstadoSolicitudMatricula.ENVIADA,
                    EstadoSolicitudMatricula.EN_REVISION,
                    EstadoSolicitudMatricula.OBSERVADA);

    private final MatriculaRepository matriculaRepository;
    private final SolicitudMatriculaRepository solicitudRepository;
    private final SeccionRepository seccionRepository;
    private final ConfiguracionAcademicaService configuracion;
    private final com.asiscontrol.repository.PersonaRepository personas;
    private final AuditoriaService auditoria;
    private final PlanPensionService planes;
    private final HistorialSeccionMatriculaRepository historialSecciones;
    private final AlumnoRepository alumnoRepository;
    private final AlumnoApoderadoRepository vinculosFamiliares;
    private final InscripcionCursoService inscripciones;

    public MatriculaService(
            MatriculaRepository matriculaRepository,
            SolicitudMatriculaRepository solicitudRepository,
            SeccionRepository seccionRepository,
            ConfiguracionAcademicaService configuracion,
            com.asiscontrol.repository.PersonaRepository personas,
            AuditoriaService auditoria,
            PlanPensionService planes,
            HistorialSeccionMatriculaRepository historialSecciones,
            AlumnoRepository alumnoRepository,
            AlumnoApoderadoRepository vinculosFamiliares,
            InscripcionCursoService inscripciones) {
        this.matriculaRepository = matriculaRepository;
        this.solicitudRepository = solicitudRepository;
        this.seccionRepository = seccionRepository;
        this.configuracion = configuracion;
        this.personas = personas;
        this.auditoria = auditoria;
        this.planes = planes;
        this.historialSecciones = historialSecciones;
        this.alumnoRepository = alumnoRepository;
        this.vinculosFamiliares = vinculosFamiliares;
        this.inscripciones = inscripciones;
    }

    // El bloqueo serializa la última vacante; READ_COMMITTED evita contar una instantánea anterior
    // en MySQL.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MatriculaResponse crear(CrearMatriculaRequest request) {
        return crear(request, false);
    }

    /**
     * Matricula a un estudiante sin que exista un trámite. La solicitud que respalda la matrícula se
     * genera en la misma transacción; si alguna regla falla, no queda registrada.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MatriculaResponse matricularDirectamente(MatriculaDirectaRequest request) {
        Seccion seccion =
                seccionRepository
                        .findByIdAndActivoTrue(request.seccionId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SECCION_NO_ENCONTRADA",
                                                "La sección seleccionada no está disponible"));
        Alumno alumno =
                alumnoRepository
                        .findByIdAndActivoTrue(request.alumnoId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "ALUMNO_NO_ENCONTRADO", "No se encontró el estudiante"));
        configuracion.bloquear(seccion.getAnioAcademico());
        personas.lockActiveById(alumno.getPersona().getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "ALUMNO_INACTIVO", "El estudiante no está habilitado"));
        if (solicitudRepository.contarSolicitudesEnEstados(
                        alumno.getId(), seccion.getAnioAcademico(), SOLICITUDES_EN_TRAMITE)
                > 0) {
            throw new ConflictException(
                    "SOLICITUD_MATRICULA_DUPLICADA",
                    "El estudiante ya tiene una solicitud en trámite para ese año. Resuélvela en Solicitudes.");
        }
        if (!vinculosFamiliares.existsByAlumnoIdAndActivoTrueAndApoderadoActivoTrueAndApoderadoPersonaActivoTrue(
                alumno.getId())) {
            throw new BusinessRuleException(
                    "APODERADO_REQUERIDO",
                    "Asigne un apoderado al estudiante antes de matricularlo");
        }
        SolicitudMatricula respaldo = new SolicitudMatricula();
        respaldo.setAlumno(alumno);
        respaldo.setSeccion(seccion);
        respaldo.setAnioAcademico(seccion.getAnioAcademico());
        respaldo.setGrado(seccion.getGrado());
        respaldo.setFechaEnvio(Instant.now());
        respaldo.setEstado(EstadoSolicitudMatricula.EN_REVISION);
        respaldo.setObservaciones("Matrícula registrada por administración desde la ficha del estudiante");
        respaldo.setActivo(true);
        solicitudRepository.saveAndFlush(respaldo);
        return crear(
                new CrearMatriculaRequest(respaldo.getId(), respaldo.getVersion(), request.firmaPlan()),
                false);
    }

    // Solo el flujo de renovación llama a este método, dentro de su transacción de revisión.
    MatriculaResponse crearDesdeRenovacion(CrearMatriculaRequest request) {
        return crear(request, true);
    }

    private MatriculaResponse crear(CrearMatriculaRequest request, boolean renovacion) {
        SolicitudMatricula vista =
                solicitudRepository
                        .findByIdAndActivoTrue(request.solicitudMatriculaId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SOLICITUD_NO_ENCONTRADA",
                                                "No se encontró la solicitud"));
        configuracion.bloquear(vista.getAnioAcademico());
        configuracion.exigirOferta(vista.getAnioAcademico(), vista.getGrado(), true);
        personas.lockActiveById(vista.getAlumno().getPersona().getId())
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "ALUMNO_INACTIVO", "El estudiante no está habilitado"));
        if (!renovacion) {
            exigirIngresoInicial(matriculaRepository, vista.getAlumno().getId(), vista.getAnioAcademico());
        }
        SolicitudMatricula solicitud =
                solicitudRepository
                        .buscarActivaParaActualizar(request.solicitudMatriculaId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SOLICITUD_MATRICULA_NO_ENCONTRADA",
                                                "No existe la solicitud de matricula indicada"));
        exigirVersion(solicitud.getVersion(), request.versionSolicitud(), "solicitud");
        EstadoSolicitudMatricula estadoAnterior = solicitud.getEstado();
        if (estadoAnterior != EstadoSolicitudMatricula.ENVIADA
                && estadoAnterior != EstadoSolicitudMatricula.EN_REVISION) {
            throw new BusinessRuleException(
                    "SOLICITUD_NO_FINALIZABLE",
                    "La solicitud debe estar enviada o en revisión para crear la matrícula");
        }
        if (matriculaRepository
                .findBySolicitudMatriculaIdAndActivoTrue(solicitud.getId())
                .isPresent()) {
            throw new ConflictException(
                    "SOLICITUD_YA_FINALIZADA", "La solicitud ya genero una matricula");
        }

        Seccion seccion = bloquearSeccionDestino(solicitud, request.seccionId());
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

        solicitud.setSeccion(seccion);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        solicitud.setFechaRevision(ahora);
        solicitudRepository.saveAndFlush(solicitud);
        matriculaRepository.saveAndFlush(matricula);
        planes.generar(matricula,request.firmaPlan());
        // inscripcion cursos: en la misma transacción que las cuotas
        inscripciones.inscribir(matricula);
        auditoria.registrar(
                "FINALIZAR_SOLICITUD",
                ModuloAuditoria.MATRICULA,
                "SolicitudMatricula",
                solicitud.getId().toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                "Aprobación administrativa",
                estadoAnterior.name(),
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

    private Seccion bloquearSeccionDestino(SolicitudMatricula solicitud, Long seccionElegida) {
        Long seccionId = seccionElegida;
        if (seccionId == null && solicitud.getSeccion() != null)
            seccionId = solicitud.getSeccion().getId();
        if (seccionId == null)
            throw new BusinessRuleException(
                    "SECCION_REQUERIDA", "Asigne una sección antes de finalizar la matrícula");
        Seccion seccion =
                seccionRepository
                        .buscarActivaParaActualizar(seccionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SECCION_NO_ENCONTRADA",
                                                "La sección seleccionada no está disponible"));
        if (seccion.getAnioAcademico() != solicitud.getAnioAcademico()
                || seccion.getGrado() != solicitud.getGrado())
            throw new BusinessRuleException(
                    "SECCION_GRADO_INVALIDO",
                    "La sección debe pertenecer al mismo año y grado solicitado");
        return seccion;
    }

    static void exigirIngresoInicial(MatriculaRepository matriculas, Long alumno, int anio) {
        if (matriculas.existsByAlumnoIdAndAnioAcademicoLessThanAndActivoTrueAndEstadoIn(
                alumno, anio, java.util.List.of(EstadoMatricula.ACTIVA, EstadoMatricula.FINALIZADA))) {
            throw new BusinessRuleException("RENOVACION_REQUERIDA", "El estudiante ya tiene historial de matrícula. "
                    + "Usa Renovación anual para el año consecutivo o consulta con la institución para gestionar un reingreso.");
        }
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
        // inscripcion cursos: retirada o anulada deja sus cursos inactivos; finalizada los conserva
        if (request.estado() == EstadoMatricula.RETIRADA || request.estado() == EstadoMatricula.ANULADA) {
            inscripciones.desactivar(matricula);
        }
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MatriculaResponse cambiarSeccion(Long id, CambiarSeccionMatriculaRequest request) {
        configuracion.bloquear(obtenerEntidad(id).getAnioAcademico());
        Matricula matricula = matriculaRepository.bloquearPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATRICULA_NO_ENCONTRADA", "No existe la matrícula indicada"));
        exigirVersion(matricula.getVersion(), request.version(), "matricula");
        if (matricula.getEstado() != EstadoMatricula.ACTIVA) {
            throw new BusinessRuleException(
                    "MATRICULA_NO_ACTIVA", "Solo puede cambiarse la sección de una matrícula activa");
        }
        Seccion actual = matricula.getSeccion();
        if (actual.getId().equals(request.seccionId())) {
            throw new BusinessRuleException(
                    "SECCION_SIN_CAMBIOS", "Seleccione una sección diferente");
        }
        Seccion nueva = seccionRepository.buscarActivaParaActualizar(request.seccionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SECCION_NO_ENCONTRADA", "La sección seleccionada no está disponible"));
        if (nueva.getAnioAcademico() != matricula.getAnioAcademico()
                || nueva.getGrado() != actual.getGrado()) {
            throw new BusinessRuleException(
                    "SECCION_GRADO_INVALIDO", "La sección debe pertenecer al mismo año y grado");
        }
        configuracion.exigirOferta(nueva.getAnioAcademico(), nueva.getGrado(), true);
        long ocupados = matriculaRepository.countBySeccionIdAndEstadoAndActivoTrue(
                nueva.getId(), EstadoMatricula.ACTIVA);
        if (ocupados >= nueva.getCapacidadMaxima()) {
            throw new BusinessRuleException(
                    "SECCION_SIN_VACANTES", "La sección ya alcanzó su capacidad máxima");
        }
        matricula.setSeccion(nueva);
        matriculaRepository.saveAndFlush(matricula);
        historialSecciones.save(
                new HistorialSeccionMatricula(matricula, actual, Instant.now(), LocalDateTime.now()));
        auditoria.registrar(
                "CAMBIAR_SECCION_MATRICULA",
                ModuloAuditoria.MATRICULA,
                "Matricula",
                id.toString(),
                com.asiscontrol.entity.enums.ResultadoAuditoria.EXITOSO,
                request.motivo().trim(),
                "seccion=" + actual.getId(),
                "seccion=" + nueva.getId());
        return mapear(matricula);
    }

    // asistencia alumnos: estudiantes con matrícula ACTIVA en la sección
    public java.util.List<com.asiscontrol.entity.Alumno> alumnosConMatriculaActiva(Long seccionId) {
        return matriculaRepository.findAlumnosPorSeccionYEstado(
                seccionId, com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA);
    }

    public MatriculaResponse obtener(Long id) {
        return mapear(obtenerEntidad(id));
    }

    public Page<MatriculaResponse> listar(
            Long personaAlumnoId, Long personaApoderadoId, FiltroMatriculas filtro, Pageable pageable) {
        String busqueda =
                filtro.search() == null || filtro.search().isBlank()
                        ? null
                        : "%" + filtro.search().strip().toLowerCase(Locale.ROOT) + "%";
        return matriculaRepository
                .buscarVisibles(
                        personaAlumnoId,
                        personaApoderadoId,
                        filtro.alumnoId(),
                        filtro.anioAcademico(),
                        filtro.grado(),
                        filtro.seccionId(),
                        filtro.vigentes(),
                        busqueda,
                        pageable)
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
                matricula.getVersion(),
                matricula.getAlumno().getPersona().getNumeroDocumento());
    }

    private void exigirVersion(long actual, Long recibida, String recurso) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA",
                    "La " + recurso + " fue modificada; vuelva a cargarla");
        }
    }
}
