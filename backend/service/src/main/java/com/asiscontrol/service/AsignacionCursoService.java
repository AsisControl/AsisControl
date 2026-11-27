package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.AsignacionCursoResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.EstadoAsignacion;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioProgramadoAsignacion;
import com.asiscontrol.dto.academico.AcademicoDtos.RetirarAsignacionCursoRequest;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.service.asistencia.HorarioAsignacionCambiadoEvent;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** US-58 / PLA-06: asignacion de docentes activos a cursos y secciones de un año vigente. */
@Service
@Transactional(readOnly = true)
public class AsignacionCursoService {

    private final AsignacionCursoRepository asignacionRepository;
    private final CursoRepository cursoRepository;
    private final SeccionRepository seccionRepository;
    private final DocenteRepository docenteRepository;
    private final HorarioRepository horarioRepository;
    private final PlanEstudiosService planes;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final EntityManager entityManager;

    public AsignacionCursoService(
            AsignacionCursoRepository asignacionRepository,
            CursoRepository cursoRepository,
            SeccionRepository seccionRepository,
            DocenteRepository docenteRepository,
            HorarioRepository horarioRepository,
            PlanEstudiosService planes,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria,
            ApplicationEventPublisher eventos,
            EntityManager entityManager) {
        this.asignacionRepository = asignacionRepository;
        this.cursoRepository = cursoRepository;
        this.seccionRepository = seccionRepository;
        this.docenteRepository = docenteRepository;
        this.horarioRepository = horarioRepository;
        this.planes = planes;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AsignacionCursoResponse crear(CrearAsignacionCursoRequest request) {
        Curso curso = buscarCurso(request.cursoId());
        Seccion seccion = buscarSeccion(request.seccionId());
        Docente docente = buscarDocente(request.docenteId());
        planes.comprobarAsignacion(curso, seccion);

        var existente = asignacionRepository.findByCursoIdAndSeccionId(curso.getId(), seccion.getId());
        if (existente.filter(AsignacionCurso::isActivo).isPresent()) {
            throw new ConflictException(
                    "ASIGNACION_CURSO_DUPLICADA", "El curso ya tiene un docente asignado en esa sección");
        }
        // La restriccion unica (curso, seccion) no distingue el estado: se reutiliza la fila
        // retirada. Quien la tuvo antes queda en la auditoria.
        boolean reactivada = existente.isPresent();
        AsignacionCurso asignacion = existente.orElseGet(AsignacionCurso::new);
        String anterior = reactivada ? describir(asignacion) : null;
        asignacion.setCurso(curso);
        asignacion.setSeccion(seccion);
        asignacion.setDocente(docente);
        asignacion.setActivo(true);
        AsignacionCurso saved = asignacionRepository.saveAndFlush(asignacion);
        entityManager.refresh(saved);
        auditoria.registrar(
                reactivada ? "REACTIVAR_ASIGNACION_CURSO" : "CREAR_ASIGNACION_CURSO",
                ModuloAuditoria.ACADEMICO, "AsignacionCurso", saved.getId().toString(),
                ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return mapear(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AsignacionCursoResponse actualizar(Long id, ActualizarAsignacionCursoRequest request) {
        AsignacionCurso asignacion = obtenerEntidad(id);
        exigirVersion(asignacion.getVersion(), request.version());
        validarUnica(request.cursoId(), request.seccionId(), id);
        String anterior = describir(asignacion);
        Curso curso = buscarCurso(request.cursoId());
        Seccion seccion = buscarSeccion(request.seccionId());
        Docente docente = buscarDocente(request.docenteId());
        planes.comprobarAsignacion(curso, seccion);

        List<Horario> horarios = horarioRepository.findActivosPorAsignacion(id);
        boolean cambiaContexto = !curso.getId().equals(asignacion.getCurso().getId())
                || !seccion.getId().equals(asignacion.getSeccion().getId());
        if (cambiaContexto && !horarios.isEmpty()) {
            throw new ConflictException(
                    "ASIGNACION_CON_HORARIOS",
                    "No se puede cambiar el curso o la sección mientras la asignación tenga bloques de"
                            + " horario; reasígnelos o elimínelos primero",
                    horarios.stream().map(this::mapearHorario).toList());
        }
        if (!docente.getId().equals(asignacion.getDocente().getId())) {
            validarSinCruces(docente, horarios);
        }

        asignacion.setCurso(curso);
        asignacion.setSeccion(seccion);
        asignacion.setDocente(docente);
        AsignacionCurso saved = asignacionRepository.saveAndFlush(asignacion);
        entityManager.refresh(saved);
        auditoria.registrar("ACTUALIZAR_ASIGNACION_CURSO", ModuloAuditoria.ACADEMICO, "AsignacionCurso",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        eventos.publishEvent(new HorarioAsignacionCambiadoEvent(Set.of(id)));
        return mapear(saved);
    }

    /**
     * Retira al docente del curso (PLA-06): exige motivo y que la asignacion no tenga bloques de
     * horario activos. Si los tiene, se rechaza indicando cuales reasignar o eliminar.
     */
    @Transactional
    public AsignacionCursoResponse retirar(Long id, RetirarAsignacionCursoRequest request) {
        AsignacionCurso asignacion = obtenerEntidad(id);
        exigirVersion(asignacion.getVersion(), request.version());
        if (request.motivo() == null || request.motivo().isBlank()) {
            throw new BusinessRuleException("MOTIVO_REQUERIDO", "Indique el motivo del retiro");
        }
        List<Horario> horarios = horarioRepository.findActivosPorAsignacion(id);
        if (!horarios.isEmpty()) {
            throw new ConflictException(
                    "ASIGNACION_CON_HORARIOS_FUTUROS",
                    "El docente tiene " + horarios.size() + " bloque(s) de horario programados en este"
                            + " curso; reasígnelos o elimínelos en la matriz de horarios antes de retirarlo",
                    horarios.stream().map(this::mapearHorario).toList());
        }
        String anterior = describir(asignacion);
        asignacion.setActivo(false);
        AsignacionCurso saved = asignacionRepository.saveAndFlush(asignacion);
        auditoria.registrar("RETIRAR_ASIGNACION_CURSO", ModuloAuditoria.ACADEMICO, "AsignacionCurso",
                id.toString(), ResultadoAuditoria.EXITOSO, request.motivo().trim(), anterior,
                describir(saved));
        eventos.publishEvent(new HorarioAsignacionCambiadoEvent(Set.of(id)));
        return mapear(saved);
    }

    public AsignacionCursoResponse obtener(Long id) {
        AsignacionCurso asignacion = asignacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASIGNACION_CURSO_NO_ENCONTRADA", "No existe una asignacion de curso con id " + id));
        Long personaDocente = accessGuard.restriccionDocentePersonaId();
        if (personaDocente != null
                && !asignacion.getDocente().getPersona().getId().equals(personaDocente)) {
            throw new AccessDeniedException("La asignación no pertenece al docente autenticado");
        }
        return mapear(asignacion);
    }

    public Page<AsignacionCursoResponse> listar(Pageable pageable) {
        return listar(EstadoAsignacion.ACTIVAS, null, null, null, null, pageable);
    }

    /** Un docente solo ve sus propias asignaciones; el administrador ve todas. */
    public Page<AsignacionCursoResponse> listar(
            EstadoAsignacion estado,
            Long docenteId,
            Long cursoId,
            Long seccionId,
            Integer anioAcademico,
            Pageable pageable) {
        Boolean activo = estado == null || estado == EstadoAsignacion.ACTIVAS
                ? Boolean.TRUE
                : estado == EstadoAsignacion.RETIRADAS ? Boolean.FALSE : null;
        return asignacionRepository
                .buscar(activo, docenteId, cursoId, seccionId, anioAcademico,
                        accessGuard.restriccionDocentePersonaId(), pageable)
                .map(this::mapear);
    }

    /**
     * US-22: asignaciones activas del docente autenticado (para crear tareas). Siempre se filtra
     * por la persona de la sesion, incluso para el administrador, que sin perfil docente no tiene ninguna.
     */
    public Page<AsignacionCursoResponse> listarPropias(Integer anioAcademico, Pageable pageable) {
        return asignacionRepository
                .buscar(Boolean.TRUE, null, null, null, anioAcademico, accessGuard.personaIdActual(), pageable)
                .map(this::mapear);
    }

    public AsignacionCurso obtenerEntidad(Long id) {
        return buscarActiva(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASIGNACION_CURSO_NO_ENCONTRADA",
                        "No existe una asignacion de curso activa con id " + id));
    }

    // asistencia alumnos
    public List<AsignacionCurso> listarPorPersonaDocente(Long personaId) {
        return asignacionRepository.buscarPorPersonaDocente(personaId);
    }

    // asistencia alumnos: sin excepción, para sincronizar sesiones de una asignación retirada
    public Optional<AsignacionCurso> buscarActiva(Long id) {
        return asignacionRepository.findByIdAndActivoTrue(id);
    }

    /** Al cambiar de docente, sus otros bloques no pueden cruzarse con los de esta asignacion. */
    private void validarSinCruces(Docente docente, List<Horario> horarios) {
        List<HorarioProgramadoAsignacion> cruces = horarios.stream()
                .filter(h -> !horarioRepository.buscarConflictos(
                                h.getAsignacionCurso().getSeccion().getAnioAcademico(),
                                h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin(),
                                null, docente.getId(), null, h.getId())
                        .isEmpty())
                .map(this::mapearHorario)
                .toList();
        if (!cruces.isEmpty()) {
            throw new ConflictException(
                    "DOCENTE_CON_CRUCE_DE_HORARIO",
                    "El docente ya tiene clases en el mismo horario que algunos bloques de esta asignación",
                    cruces);
        }
    }

    private Curso buscarCurso(Long id) {
        return cursoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CURSO_NO_ENCONTRADO", "No existe un curso activo con id " + id));
    }

    private Seccion buscarSeccion(Long id) {
        return seccionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SECCION_NO_ENCONTRADA", "No existe una seccion activa con id " + id));
    }

    private Docente buscarDocente(Long id) {
        return docenteRepository.findByIdAndActivoTrue(id)
                .filter(docente -> docente.getPersona().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DOCENTE_NO_ENCONTRADO", "No existe un docente activo con id " + id));
    }

    /**
     * Al editar hacia otro curso o seccion, la combinacion no puede existir en ningun estado: la
     * restriccion unica de la base incluye las retiradas, que se recuperan con un alta nueva.
     */
    private void validarUnica(Long cursoId, Long seccionId, Long idActual) {
        asignacionRepository.findByCursoIdAndSeccionId(cursoId, seccionId)
                .filter(existente -> !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    if (existente.isActivo()) {
                        throw new ConflictException(
                                "ASIGNACION_CURSO_DUPLICADA",
                                "El curso ya tiene un docente asignado en esa sección");
                    }
                    throw new ConflictException(
                            "ASIGNACION_RETIRADA_EXISTENTE",
                            "Ese curso ya tuvo un docente retirado en esa sección; regístrelo con"
                                    + " «Nueva asignación» para volver a asignarlo");
                });
    }

    /** Contexto completo para la auditoria: quien, que curso, que seccion y que año. */
    private String describir(AsignacionCurso asignacion) {
        return "docenteId=" + asignacion.getDocente().getId()
                + ",docente=" + asignacion.getDocente().getPersona().nombreCompleto()
                + ",cursoId=" + asignacion.getCurso().getId()
                + ",curso=" + asignacion.nombreCurso()
                + ",seccionId=" + asignacion.getSeccion().getId()
                + ",seccion=" + etiquetaSeccion(asignacion.getSeccion())
                + ",anio=" + asignacion.getSeccion().getAnioAcademico()
                + ",activo=" + asignacion.isActivo();
    }

    /** "1° A": grado y nombre, porque el nombre solo se repite entre grados. */
    static String etiquetaSeccion(Seccion seccion) {
        return seccion.getGrado() + "° " + seccion.getNombre();
    }

    private HorarioProgramadoAsignacion mapearHorario(Horario horario) {
        return new HorarioProgramadoAsignacion(
                horario.getId(), horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFin(),
                horario.getAula().getId(), horario.getAula().getNombre());
    }

    private AsignacionCursoResponse mapear(AsignacionCurso asignacion) {
        return new AsignacionCursoResponse(
                asignacion.getId(), asignacion.getCurso().getId(), asignacion.nombreCurso(),
                asignacion.getSeccion().getId(), asignacion.getSeccion().getNombre(),
                asignacion.getSeccion().getAnioAcademico(), asignacion.getDocente().getId(),
                asignacion.getDocente().getPersona().nombreCompleto(), asignacion.isActivo(),
                asignacion.getVersion(), asignacion.getSeccion().getGrado(),
                asignacion.getId() == null
                        ? 0
                        : horarioRepository.countByAsignacionCursoIdAndActivoTrue(asignacion.getId()),
                horasPlan(asignacion));
    }

    /** OBS-01: el formulario del bloque muestra las horas del plan, programadas y disponibles. */
    private Integer horasPlan(AsignacionCurso asignacion) {
        var horas = planes.horasDelPlan(asignacion.getCurso(), asignacion.getSeccion());
        return horas.isPresent() ? horas.getAsInt() : null;
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La asignacion fue modificada; vuelva a cargarla");
        }
    }
}
