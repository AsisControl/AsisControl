package com.asiscontrol.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class SessionTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SESSION =
            new OAuth2Error("invalid_token", "La sesion fue revocada o expiro", null);

    private final com.asiscontrol.service.SesionActividadService actividad;

    public SessionTokenValidator(com.asiscontrol.service.SesionActividadService actividad) {
        this.actividad = actividad;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object sesion = token.getClaims().get("sesionId");
        Object version = token.getClaims().get("versionContexto");
        Object usuario = token.getClaims().get("usuarioId");
        if (sesion instanceof Number sid && version instanceof Number contexto
                && usuario instanceof Number cuenta
                && actividad.validar(sid.longValue(), contexto.longValue(), cuenta.longValue(),
                        token.getClaimAsString("rol"))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
    }
}
