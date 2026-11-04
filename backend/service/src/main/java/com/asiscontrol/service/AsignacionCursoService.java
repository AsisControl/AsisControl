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
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** US-58 / PLA-06: asignacion de docentes activos a cursos y secciones de un año vigente. */
@Service
@Transactional(readOnly = true)
public class AsignacionCursoService {

    private final AsignacionCursoRepository asignacionRepository;
    private final CursoRepository cursoRepository;
    private final SeccionRepository seccionRepository;
    private final DocenteRepository docenteRepository;
    private final HorarioRepository horarioRepository;
    private final AnioAcademicoRepository anioRepository;
    private final ModuloAccessGuard accessGuard;
    private final AuditoriaService auditoria;

    public AsignacionCursoService(
            AsignacionCursoRepository asignacionRepository,
            CursoRepository cursoRepository,
            SeccionRepository seccionRepository,
            DocenteRepository docenteRepository,
            HorarioRepository horarioRepository,
            AnioAcademicoRepository anioRepository,
            ModuloAccessGuard accessGuard,
            AuditoriaService auditoria) {
        this.asignacionRepository = asignacionRepository;
        this.cursoRepository = cursoRepository;
        this.seccionRepository = seccionRepository;
        this.docenteRepository = docenteRepository;
        this.horarioRepository = horarioRepository;
        this.anioRepository = anioRepository;
        this.accessGuard = accessGuard;
        this.auditoria = auditoria;
    }

    @Transactional
    public AsignacionCursoResponse crear(CrearAsignacionCursoRequest request) {
        Curso curso = buscarCurso(request.cursoId());
        Seccion seccion = buscarSeccion(request.seccionId());
        validarMismoGrado(curso, seccion);
        validarAnioVigente(seccion);
        Docente docente = buscarDocente(request.docenteId());

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
        auditoria.registrar(
                reactivada ? "REACTIVAR_ASIGNACION_CURSO" : "CREAR_ASIGNACION_CURSO",
                ModuloAuditoria.ACADEMICO, "AsignacionCurso", saved.getId().toString(),
                ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
        return mapear(saved);
    }

    @Transactional
    public AsignacionCursoResponse actualizar(Long id, ActualizarAsignacionCursoRequest request) {
        AsignacionCurso asignacion = obtenerEntidad(id);
        exigirVersion(asignacion.getVersion(), request.version());
        validarUnica(request.cursoId(), request.seccionId(), id);
        String anterior = describir(asignacion);
        Curso curso = buscarCurso(request.cursoId());
        Seccion seccion = buscarSeccion(request.seccionId());
        Docente docente = buscarDocente(request.docenteId());
        validarMismoGrado(curso, seccion);
        validarAnioVigente(seccion);

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
        auditoria.registrar("ACTUALIZAR_ASIGNACION_CURSO", ModuloAuditoria.ACADEMICO, "AsignacionCurso",
                id.toString(), ResultadoAuditoria.EXITOSO, null, anterior, describir(saved));
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

    public AsignacionCurso obtenerEntidad(Long id) {
        return asignacionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASIGNACION_CURSO_NO_ENCONTRADA",
                        "No existe una asignacion de curso activa con id " + id));
    }

    /** Un curso de 1.er grado de 2027 solo se dicta en secciones de ese grado y anio. */
    private void validarMismoGrado(Curso curso, Seccion seccion) {
        var grado = curso.getGradoAcademico();
        if (grado != null && (grado.getNumero() != seccion.getGrado()
                || grado.getAnioAcademico().getAnio() != seccion.getAnioAcademico())) {
            throw new ConflictException(
                    "CURSO_DE_OTRO_GRADO",
                    "El curso es de " + grado.getNumero() + ".° grado " + grado.getAnioAcademico().getAnio()
                            + " y la sección es de " + seccion.getGrado() + ".° grado "
                            + seccion.getAnioAcademico());
        }
    }

    /** Periodo vigente: el año de la seccion no puede estar cerrado (borrador permite planificar). */
    private void validarAnioVigente(Seccion seccion) {
        anioRepository.findByAnio(seccion.getAnioAcademico())
                .filter(anio -> anio.getEstado() == EstadoAnioAcademico.CERRADO)
                .ifPresent(anio -> {
                    throw new BusinessRuleException(
                            "ANIO_NO_VIGENTE",
                            "El año académico " + anio.getAnio() + " está cerrado y no admite asignaciones");
                });
    }

    /** Al cambiar de docente, sus otros bloques no pueden cruzarse con los de esta asignacion. */
    private void validarSinCruces(Docente docente, List<Horario> horarios) {
        List<HorarioProgramadoAsignacion> cruces = horarios.stream()
                .filter(h -> !horarioRepository.buscarConflictos(
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
                + ",curso=" + asignacion.getCurso().getNombre()
                + ",seccionId=" + asignacion.getSeccion().getId()
                + ",seccion=" + asignacion.getSeccion().getGrado() + "° " + asignacion.getSeccion().getNombre()
                + ",anio=" + asignacion.getSeccion().getAnioAcademico()
                + ",activo=" + asignacion.isActivo();
    }

    private HorarioProgramadoAsignacion mapearHorario(Horario horario) {
        return new HorarioProgramadoAsignacion(
                horario.getId(), horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFin(),
                horario.getAula().getId(), horario.getAula().getNombre());
    }

    private AsignacionCursoResponse mapear(AsignacionCurso asignacion) {
        return new AsignacionCursoResponse(
                asignacion.getId(), asignacion.getCurso().getId(), asignacion.getCurso().getNombre(),
                asignacion.getSeccion().getId(), asignacion.getSeccion().getNombre(),
                asignacion.getSeccion().getAnioAcademico(), asignacion.getDocente().getId(),
                asignacion.getDocente().getPersona().nombreCompleto(), asignacion.isActivo(),
                asignacion.getVersion(), asignacion.getSeccion().getGrado(),
                asignacion.getId() == null
                        ? 0
                        : horarioRepository.countByAsignacionCursoIdAndActivoTrue(asignacion.getId()));
    }

    private void exigirVersion(long actual, Long recibida) {
        if (recibida == null || actual != recibida) {
            throw new ConflictException(
                    "VERSION_DESACTUALIZADA", "La asignacion fue modificada; vuelva a cargarla");
        }
    }
}
