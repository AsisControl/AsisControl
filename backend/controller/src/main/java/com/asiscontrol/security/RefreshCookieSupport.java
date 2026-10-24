package com.asiscontrol.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RefreshCookieSupport {
    public static final String NAME = "asiscontrol_refresh";
    private final boolean secure;
    private final String sameSite;
    private final Set<String> origins;

    public RefreshCookieSupport(
            @Value("${app.security.refresh-cookie.secure:true}") boolean secure,
            @Value("${app.security.refresh-cookie.same-site:Strict}") String sameSite,
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String origins) {
        if (!Set.of("Strict", "Lax", "None").contains(sameSite)
                || (sameSite.equals("None") && !secure)) {
            throw new IllegalArgumentException("SameSite=None requiere HTTPS; use Strict, Lax o None");
        }
        this.secure = secure;
        this.sameSite = sameSite;
        this.origins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*")).collect(Collectors.toUnmodifiableSet());
    }

    public ResponseCookie crear(String token, Instant expiry) {
        return cookie(token).maxAge(Math.max(0, Duration.between(Instant.now(), expiry).getSeconds())).build();
    }

    public ResponseCookie borrar() {
        return cookie("").maxAge(Duration.ZERO).build();
    }

    /** Los endpoints que aceptan cookies exigen Origin explícito y permitido. */
    public void validarOrigen(HttpServletRequest request, boolean obligatorio) {
        String origin = request.getHeader("Origin");
        if (origin == null && !obligatorio) return; // clientes no navegador, por ejemplo Swagger/curl
        String ownOrigin = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (!(request.getScheme().equals("https") && port == 443)
                && !(request.getScheme().equals("http") && port == 80)) ownOrigin += ":" + port;
        if (origin == null || (!origins.contains(origin) && !ownOrigin.equals(origin))) {
            throw new AccessDeniedException("Origen de la solicitud no permitido");
        }
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure)
                .sameSite(sameSite).path("/api/auth");
    }
}
