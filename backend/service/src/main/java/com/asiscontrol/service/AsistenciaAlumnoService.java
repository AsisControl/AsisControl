package com.asiscontrol.service;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaAlumnoResponse;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.JustificacionAsistencia;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import com.asiscontrol.repository.JustificacionAsistenciaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public AsistenciaAlumnoService(
            AsistenciaAlumnoRepository asistenciaRepository,
            JustificacionAsistenciaRepository justificacionRepository) {
        this.asistenciaRepository = asistenciaRepository;
        this.justificacionRepository = justificacionRepository;
    }

    public AsistenciaAlumnoResponse obtener(Long id) {
        return mapResponse(obtenerEntidad(id), justificacionRepository.findByAsistenciaAlumnoIdAndActivoTrue(id).orElse(null));
    }

    public Page<AsistenciaAlumnoResponse> listar(
            Long alumnoId,
            Long asignacionCursoId,
            LocalDate fechaRegistro,
            Long personaDocenteId,
            Long personaAlumnoId,
            Long personaApoderadoId,
            Pageable pageable) {
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
        return pagina.map(asistencia -> mapResponse(asistencia, justificaciones.get(asistencia.getId())));
    }

    public AsistenciaAlumno obtenerEntidad(Long id) {
        return asistenciaRepository.buscarOficial(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASISTENCIA_ALUMNO_NO_ENCONTRADA", "No existe una asistencia activa con id " + id));
    }

    private AsistenciaAlumnoResponse mapResponse(AsistenciaAlumno asistencia, JustificacionAsistencia justificacion) {
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
                justificacion == null ? null : justificacion.getObservacionRevision());
    }
}
