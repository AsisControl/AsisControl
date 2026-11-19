package com.asiscontrol.security;

import com.asiscontrol.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_PERMITIDAS = Set.of(
            "/api/auth/change-password",
            "/api/auth/logout",
            "/api/auth/me"
    );

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (requiereCambio(authentication) && !RUTAS_PERMITIDAS.contains(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiError error = new ApiError(
                    Instant.now(),
                    HttpServletResponse.SC_FORBIDDEN,
                    "CAMBIO_CONTRASENA_REQUERIDO",
                    "Debe cambiar la contraseña temporal antes de continuar",
                    SafeRequestPath.redact(request.getRequestURI()),
                    MDC.get("correlationId"),
                    List.of()
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean requiereCambio(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Boolean.TRUE.equals(jwt.getClaimAsBoolean("requiereCambioContrasena"));
        }
        return false;
    }
}
