package com.asiscontrol.service;

import com.asiscontrol.dto.asistencia.AsistenciaDtos.AsistenciaAlumnoResponse;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsistenciaAlumnoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Consulta de asistencias de alumnos. La escritura se hace por sesión de clase
 * (HU14) para que borrador, finalización y corrección sigan sus reglas.
 */
@Service
@Transactional(readOnly = true)
public class AsistenciaAlumnoService {

    private final AsistenciaAlumnoRepository asistenciaRepository;

    public AsistenciaAlumnoService(AsistenciaAlumnoRepository asistenciaRepository) {
        this.asistenciaRepository = asistenciaRepository;
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
}
