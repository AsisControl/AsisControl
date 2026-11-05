// asistencia alumnos
package com.asiscontrol.dto.asistencia;

import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoJustificacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class AsistenciaSesionDtos {

    private AsistenciaSesionDtos() {
    }

    /** Estado asignado a un estudiante; solo PRESENTE, TARDANZA o INASISTENCIA (R03). */
    public record MarcaAsistenciaRequest(
            @NotNull Long alumnoId,
            @NotNull EstadoAsistencia estado
    ) {
    }

    /** Las marcas reemplazan a las del borrador: un estudiante que no se envía queda sin estado. */
    public record GuardarBorradorRequest(
            @NotNull @Size(max = 200) List<@Valid MarcaAsistenciaRequest> marcas
    ) {
    }

    public record FinalizarAsistenciaRequest(
            @NotNull @Size(max = 200) List<@Valid MarcaAsistenciaRequest> marcas,
            @NotNull Long version
    ) {
    }

    public record CorregirAsistenciaRequest(
            @NotEmpty @Size(max = 200) List<@Valid MarcaAsistenciaRequest> marcas,
            @NotBlank @Size(max = 500) String motivo,
            @NotNull Long version
    ) {
    }

    /**
     * Estudiante de la sesión. El estado es PRESENTE, TARDANZA o INASISTENCIA; la justificación se
     * informa aparte y sus datos se consultan por justificacionId.
     */
    public record AlumnoAsistenciaResponse(
            Long alumnoId,
            String codigoAlumno,
            String nombreCompleto,
            EstadoAsistencia estado,
            Long asistenciaId,
            Long justificacionId,
            EstadoJustificacion estadoJustificacion
    ) {
    }

    public record SesionAsistenciaResponse(
            Long id,
            Long asignacionCursoId,
            String curso,
            String seccion,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            EstadoSesionClase estado,
            boolean puedeTomar,
            boolean puedeEditar,
            int pendientes,
            List<AlumnoAsistenciaResponse> alumnos,
            String registradoPor,
            LocalDateTime registradoEn,
            long version
    ) {
    }

    public record CambioAsistenciaResponse(
            Long alumnoId,
            String nombreCompleto,
            EstadoAsistencia estadoAnterior,
            EstadoAsistencia estadoNuevo
    ) {
    }

    public record CorreccionAsistenciaResponse(
            Long id,
            LocalDateTime fechaHora,
            Long docenteId,
            String docente,
            String motivo,
            List<CambioAsistenciaResponse> cambios
    ) {
    }
}
