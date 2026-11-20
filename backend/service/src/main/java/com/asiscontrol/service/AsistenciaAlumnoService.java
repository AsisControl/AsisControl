package com.asiscontrol.service;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaAlumnoResponse;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.JustificacionAsistenciaRepository;
import com.asiscontrol.repository.planificacion.PeriodoAcademicoRepository;
import com.asiscontrol.service.asistencia.PlazoAsistenciaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consulta de asistencias de alumnos. La escritura se hace por sesión de clase
 * (HU14) para que borrador, finalización y corrección sigan sus reglas.
 */
@Service
@Transactional(readOnly = true)
public class AsistenciaAlumnoService {

    private final AsistenciaAlumnoRepository asistenciaRepository;
    private final JustificacionAsistenciaRepository justificacionRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final PlazoAsistenciaService plazoService;

    public AsistenciaAlumnoService(
            AsistenciaAlumnoRepository asistenciaRepository,
            JustificacionAsistenciaRepository justificacionRepository,
            PeriodoAcademicoRepository periodoRepository,
            PlazoAsistenciaService plazoService) {
        this.asistenciaRepository = asistenciaRepository;
        this.justificacionRepository = justificacionRepository;
        this.periodoRepository = periodoRepository;
        this.plazoService = plazoService;
    }

    public AsistenciaAlumnoResponse obtener(Long id) {
        return mapResponse(
                obtenerEntidad(id),
                justificacionRepository.findByAsistenciaAlumnoIdAndActivoTrue(id).orElse(null),
                new HashMap<>());
    }

    /** Antes de listar se finalizan los borradores vencidos, para que alumno y apoderado los vean. */
    @Transactional
    public Page<AsistenciaAlumnoResponse> listar(
            Long alumnoId,
            Long asignacionCursoId,
            LocalDate fechaRegistro,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable) {
        plazoService.cerrarVencidas();
        Page<AsistenciaAlumno> pagina = asistenciaRepository.buscarActivas(
                alumnoId,
                asignacionCursoId,
                fechaRegistro,
                personaDocenteId,
                personaAlumnoId,
                personaApoderadoId,
                pageable);
        Map<Long, JustificacionAsistencia> justificaciones = justificacionRepository
                .findAllByAsistenciaAlumnoIdInAndActivoTrue(pagina.map(AsistenciaAlumno::getId).getContent())
                .stream()
                .collect(Collectors.toMap(j -> j.getAsistenciaAlumno().getId(), Function.identity()));
        Map<Integer, List<PeriodoAcademico>> periodos = new HashMap<>();
        return pagina.map(asistencia -> mapResponse(asistencia, justificaciones.get(asistencia.getId()), periodos));
    }

    public AsistenciaAlumno obtenerEntidad(Long id) {
        return asistenciaRepository.buscarOficial(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASISTENCIA_ALUMNO_NO_ENCONTRADA", "No existe una asistencia activa con id " + id));
    }

    /** asistencia alumnos: período académico cuyas fechas contienen la del registro; null si no cae en ninguno. */
    private String periodoDe(AsistenciaAlumno asistencia, int anio, Map<Integer, List<PeriodoAcademico>> periodos) {
        LocalDate fecha = asistencia.getFechaRegistro();
        return periodos.computeIfAbsent(anio, periodoRepository::findByAnioAcademicoAnioOrderByOrden).stream()
                .filter(periodo -> !fecha.isBefore(periodo.getFechaInicio()) && !fecha.isAfter(periodo.getFechaFin()))
                .map(PeriodoAcademico::getNombre)
                .findFirst()
                .orElse(null);
    }

    private AsistenciaAlumnoResponse mapResponse(
            AsistenciaAlumno asistencia,
            JustificacionAsistencia justificacion,
            Map<Integer, List<PeriodoAcademico>> periodos) {
        int anio = asistencia.getAsignacionCurso().getSeccion().getAnioAcademico();
        return new AsistenciaAlumnoResponse(
                asistencia.getId(),
                asistencia.getAlumno().getId(),
                asistencia.getAsignacionCurso().getId(),
                asistencia.getDocenteRegistrador() == null ? null : asistencia.getDocenteRegistrador().getId(),
                asistencia.getFechaRegistro(),
                asistencia.getHoraRegistro(),
                asistencia.getEstadoAsistencia(),
                asistencia.getComentario(),
                asistencia.getVersion(),
                asistencia.getAsignacionCurso().getCurso().getNombre(),
                justificacion == null ? null : justificacion.getId(),
                justificacion == null ? null : justificacion.getEstado(),
                justificacion == null ? null : justificacion.getMotivo(),
                justificacion == null ? null : justificacion.getObservacionRevision(),
                asistencia.getSesionClase() == null ? null : asistencia.getSesionClase().getHoraInicio(),
                anio,
                periodoDe(asistencia, anio, periodos));
    }
}
