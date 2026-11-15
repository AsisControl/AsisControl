package com.asiscontrol.security;

/** Redacta la capacidad temporal QR en errores, sin cambiar las rutas de otros módulos. */
public final class SafeRequestPath {
    private SafeRequestPath() {}

    public static String redact(String uri) {
        if (uri != null && uri.startsWith("/api/pagos/qr/")) {
            return "/api/pagos/qr/[enlace]" + (uri.endsWith("/confirmar") ? "/confirmar" : "");
        }
        return uri;
    }
}
