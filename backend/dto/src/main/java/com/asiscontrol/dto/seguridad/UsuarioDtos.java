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

    /** Datos internos de un alta con una contraseña temporal ya entregada al operador. */
    public record CreateRequest(
            @NotNull(message = "La persona es obligatoria") Long personaId,
            @NotNull(message = "El rol es obligatorio") Long rolId,
            @Size(min = 4, max = 80) String username,
            @Email(message = "El correo no tiene un formato valido") @Size(max = 150)
                    String correo,
            @NotBlank(message = "La contrasena temporal es obligatoria") @Size(min = 8, max = 64)
                    String password,
            boolean requiereCambioContrasena,
            Boolean correoVerificado) {
        public CreateRequest(Long p, Long r, String u, String c, String password, boolean cambio) {
            this(p, r, u, c, password, cambio, false);
        }

        public CreateRequest(Long personaId, Long rolId, String password) {
            this(personaId, rolId, null, null, password, true, false);
        }

    }

    /** Usuario, correo y códigos de perfil los genera el sistema. */
    public record ProvisionRequest(
            Long personaId,
            @jakarta.validation.Valid
                    com.asiscontrol.dto.persona.PersonaDtos.PersonaRequest persona,
            @NotNull Long rolId,
            @NotBlank @Size(min = 8, max = 64) String password,
            java.time.LocalDate fechaIngreso,
            com.asiscontrol.entity.enums.Especialidad especialidad,
            Set<Long> alumnoIds,
            Boolean correoVerificado) {
        public ProvisionRequest(
                Long p,
                com.asiscontrol.dto.persona.PersonaDtos.PersonaRequest persona,
                Long r,
                String password,
                java.time.LocalDate fecha,
                com.asiscontrol.entity.enums.Especialidad e,
                Set<Long> ids) {
            this(p, persona, r, password, fecha, e, ids, false);
        }
    }

    public record PersonaAccesoResponse(
            Long id,
            String nombreCompleto,
            String numeroDocumento,
            List<String> perfiles,
            List<String> rolesConAcceso) {}

    public record RegistroAdministradorRequest(
            Long personaId,
            @jakarta.validation.Valid
                    com.asiscontrol.dto.persona.PersonaDtos.PersonaRequest persona) {}

    public record RegistroAdministradorResponse(
            Long id,
            String username,
            String correo,
            String nombreCompleto,
            String contrasenaTemporal,
            Instant contrasenaTemporalExpira) {
        @Override
        public String toString() {
            return "RegistroAdministradorResponse[id=" + id + ", username=" + username + "]";
        }
    }

    public record CambiarRolRequest(
            @NotNull(message = "Indica el estado del rol") Boolean activo,
            @NotBlank(message = "Escribe el motivo del cambio") @Size(max = 700) String motivo) {}

    public record UpdateRequest(
            Long rolId,
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
            @NotBlank(message = "La contrasena temporal es obligatoria") @Size(min = 8, max = 64)
                    String temporaryPassword,
            @jakarta.validation.constraints.AssertTrue(
                            message = "Confirme que verificó la identidad del titular")
                    boolean identidadVerificada,
            @NotBlank @Size(max = 700) String motivo) {
        public ResetPasswordRequest(String password) {
            this(password, false, null);
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
            Instant contrasenaTemporalExpira,
            String codigoPerfil,
            List<RolAsignadoResponse> roles) {}

    public record RolAsignadoResponse(Long id, String nombre, boolean activo, String codigoPerfil) {}

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
