package com.asiscontrol.dto.matricula;

import com.asiscontrol.entity.enums.EstadoRenovacion;
import com.asiscontrol.entity.enums.ResultadoPromocion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class RenovacionDtos {
    private RenovacionDtos() {}

    public record CrearRequest(@NotNull @Positive Long idMatriculaAnterior, @NotNull @Positive Long idSeccion) {}
    /** {@code idSeccion} es opcional: si falta, se aprueba en la sección solicitada. */
    public record AprobarRequest(@NotNull @PositiveOrZero Long version, @Size(max = 64) String firmaPlan,
                                 @Positive Long idSeccion) {}
    public record RechazarRequest(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 700) String motivo) {}
    public record PromocionRequest(@NotNull ResultadoPromocion resultado, @NotBlank @Size(max = 700) String motivo,
                                   @NotNull @PositiveOrZero Long version) {}

    public record SeccionOpcion(Long id, String nombre, long vacantes) {}
    public record Opcion(Long idAlumno, String alumno, String codigoAlumno, Long idMatriculaAnterior,
                         int anioAnterior, int gradoAnterior, int anioDestino, int gradoDestino,
                         ResultadoPromocion promocion, List<SeccionOpcion> secciones) {}
    public record Response(Long id, Long idAlumno, String alumno, String codigoAlumno, Long idMatriculaAnterior,
                           int anioAnterior, int gradoAnterior, int anioDestino, int gradoDestino,
                           Long idSeccion, String seccion, EstadoRenovacion estado, String codigoMotivo,
                           String motivo, Long idMatricula, Instant fechaCreacion, Instant fechaDecision, long version) {}
    public record PromocionResponse(Long idMatriculaAnterior, Long idAlumno, String alumno, String codigoAlumno,
                                    int anioAnterior, int gradoAnterior, ResultadoPromocion promocion,
                                    String motivo, long version) {}
}
