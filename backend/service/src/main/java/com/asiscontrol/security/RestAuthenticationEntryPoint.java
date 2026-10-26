package com.asiscontrol.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Peticion sin sesion o con una sesion vencida; el login usa su propio mensaje. */
    public static final String MENSAJE_SESION =
            "Tu sesión expiró o no es válida. Inicia sesión nuevamente.";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Sin esto el writer usa ISO-8859-1 y las tildes del mensaje llegan mal.
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"timestamp":"%s","status":401,"code":"AUTENTICACION_REQUERIDA",\
                "message":"%s",\
                "path":"%s","correlationId":"%s","fieldErrors":[]}
                """.formatted(
                Instant.now(),
                MENSAJE_SESION,
                escape(request.getRequestURI()),
                escape(MDC.get("correlationId"))
        ).replace("\n", ""));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
