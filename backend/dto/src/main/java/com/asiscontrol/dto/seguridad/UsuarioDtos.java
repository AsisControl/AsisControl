package com.asiscontrol.dto.seguridad;

import com.asiscontrol.entity.enums.EstadoAcceso;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UsuarioDtos {

    private UsuarioDtos() {}

    public record CreateRequest(
            @NotNull(message = "La persona es obligatoria") Long personaId,
            @NotNull(message = "El rol es obligatorio") Long rolId,
            @NotBlank(message = "El nombre de usuario es obligatorio") @Size(min = 4, max = 80)
                    String username,
            @NotBlank(message = "El correo es obligatorio")
                    @Email(message = "El correo no tiene un formato valido")
                    String correo,
            @NotBlank(message = "La contrasena temporal es obligatoria") @Size(min = 8, max = 64)
                    String password,
            boolean requiereCambioContrasena,
            Boolean correoVerificado) {
        public CreateRequest(Long p, Long r, String u, String c, String password, boolean cambio) {
            this(p, r, u, c, password, cambio, false);
        }
    }

    public record ProvisionRequest(
            Long personaId,
            @jakarta.validation.Valid
                    com.asiscontrol.dto.persona.PersonaDtos.PersonaRequest persona,
            @NotNull Long rolId,
            @NotBlank @Size(min = 4, max = 80) String username,
            @NotBlank @Email @Size(max = 150) String correo,
            @NotBlank @Size(min = 8, max = 64) String password,
            @Size(max = 30) String codigoAlumno,
            @Size(max = 30) String codigoDocente,
            java.time.LocalDate fechaIngreso,
            com.asiscontrol.entity.enums.Especialidad especialidad,
            Set<Long> alumnoIds,
            Boolean correoVerificado) {
        public ProvisionRequest(
                Long p,
                com.asiscontrol.dto.persona.PersonaDtos.PersonaRequest persona,
                Long r,
                String u,
                String c,
                String password,
                String a,
                String d,
                java.time.LocalDate fecha,
                com.asiscontrol.entity.enums.Especialidad e,
                Set<Long> ids) {
            this(p, persona, r, u, c, password, a, d, fecha, e, ids, false);
        }
    }

    public record PersonaAccesoResponse(
            Long id,
            String nombreCompleto,
            String numeroDocumento,
            List<String> perfiles,
            List<String> rolesConAcceso) {}

    public record UpdateRequest(
            @NotNull(message = "El rol es obligatorio") Long rolId,
            @NotBlank(message = "El correo es obligatorio")
                    @Email(message = "El correo no tiene un formato valido")
                    String correo,
            @NotNull(message = "El estado es obligatorio") EstadoAcceso estado,
            Boolean correoVerificado,
            @Size(max = 64) String temporaryPassword,
            @Size(max = 700) String motivo) {
        public UpdateRequest(Long r, String c, EstadoAcceso e) {
            this(r, c, e, null, null, null);
        }
    }

    public record ResetPasswordRequest(
            @jakarta.validation.constraints.AssertTrue(
                            message = "Confirme que verificó la identidad del titular")
                    boolean identidadVerificada,
            @NotBlank @Size(max = 700) String motivo) {}

    public record ResetPasswordResponse(
            Long usuarioId, String temporaryPassword, Instant expiresAt) {
        @Override
        public String toString() {
            return "ResetPasswordResponse[credenciales=PROTEGIDAS]";
        }
    }

    public record Response(
            Long id,
            Long personaId,
            String username,
            String correo,
            String nombreCompleto,
            Long rolId,
            String rol,
            EstadoAcceso estado,
            boolean requiereCambioContrasena,
            int intentosFallidos,
            Instant bloqueadoHasta,
            Instant ultimoAcceso,
            Instant fechaCreacion,
            boolean correoVerificado,
            Instant contrasenaTemporalExpira) {}

    public record RolRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 50) String nombre,
            @Size(max = 250) String descripcion,
            Set<Long> permisoIds) {}

    public record RolResponse(
            Long id,
            String nombre,
            String descripcion,
            List<PermisoResponse> permisos,
            boolean activo) {}

    public record PermisoResponse(
            Long id, String codigo, String nombre, String descripcion, String categoria) {}
}
