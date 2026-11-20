package com.asiscontrol.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "El usuario o correo es obligatorio") @Size(max = 150)
                    String identifier,
            @NotBlank(message = "La contrasena es obligatoria") @Size(max = 72) String password) {}

    public record AuthResponse(
            String tokenType, String accessToken, Instant expiresAt, UserSummary user) {}

    public record UserSummary(
            Long id,
            Long personaId,
            String username,
            String correo,
            String nombreCompleto,
            String rol,
            List<String> permisos,
            boolean requiereCambioContrasena,
            List<String> rolesDisponibles,
            Long sesionId,
            long versionContexto) {}

    public record RolActivoRequest(
            @NotBlank(message = "Selecciona un rol válido.") @Size(max = 30) String rol) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "La contrasena actual es obligatoria") String currentPassword,
            @NotBlank(message = "La nueva contrasena es obligatoria")
                    @Size(
                            min = 8,
                            max = 64,
                            message = "La nueva contraseña debe tener entre 8 y 64 caracteres")
                    String newPassword) {}

    public record RecoveryRequest(@NotBlank @Size(max = 150) String identifier) {}

    public record RecoveryTokenRequest(@NotBlank @Size(min = 43, max = 43) String token) {}

    public record ResetRecoveryRequest(
            @NotBlank @Size(min = 43, max = 43) String token,
            @NotBlank @Size(min = 8, max = 64) String newPassword,
            @NotBlank @Size(min = 8, max = 64) String confirmPassword) {}

    public record MessageResponse(String message) {}
}
