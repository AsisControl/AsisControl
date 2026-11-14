package com.asiscontrol.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class EvidenciaPedagogicaDto {

    private EvidenciaPedagogicaDto() {}

    public record Crear(
            @NotNull @Positive Long asignacionId,
            @NotNull @Positive Long alumnoId,
            @NotNull @Positive Long competenciaId,
            @NotNull @Positive Long periodoId,
            @Size(max = 1000) String descripcion,
            @Positive Long archivoId) {}

    public record Editar(
            @Size(max = 1000) String descripcion,
            @Positive Long archivoId) {}

    public record Respuesta(
            Long id, Long asignacionId, Long alumnoId, Long cursoId, Long competenciaId,
            Long periodoId, Long autorId, String descripcion, Adjunto archivo,
            Instant fechaCreacion, Instant fechaActualizacion) {}

    public record Adjunto(Long id, String nombreOriginal, String tipoMime, long tamanioBytes) {}
}
