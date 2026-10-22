package com.asiscontrol.controller;

import com.asiscontrol.dto.auth.AuthDtos;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.asiscontrol.service.RecuperacionService recuperacion;
    private final com.asiscontrol.service.RenovacionSesionService renovacion;
    private final com.asiscontrol.security.RefreshCookieSupport cookies;

    public AuthController(
            AuthService authService, com.asiscontrol.service.RecuperacionService recuperacion,
            com.asiscontrol.service.RenovacionSesionService renovacion,
            com.asiscontrol.security.RefreshCookieSupport cookies) {
        this.authService = authService;
        this.recuperacion = recuperacion;
        this.renovacion = renovacion;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @io.swagger.v3.oas.annotations.Operation(summary = "Iniciar sesión y emitir cookie HttpOnly de renovación")
    public org.springframework.http.ResponseEntity<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        cookies.validarOrigen(http, false);
        return respuesta(renovacion.login(request));
    }

    @PostMapping("/refresh")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @io.swagger.v3.oas.annotations.Operation(summary = "Renovar usando la cookie y un Origin permitido; sin Bearer")
    public org.springframework.http.ResponseEntity<AuthDtos.AuthResponse> refresh(
            @org.springframework.web.bind.annotation.CookieValue(
                    name = com.asiscontrol.security.RefreshCookieSupport.NAME, required = false)
                    String refreshToken,
            jakarta.servlet.http.HttpServletRequest http,
            jakarta.servlet.http.HttpServletResponse response) {
        cookies.validarOrigen(http, true);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            return respuesta(renovacion.renovar(refreshToken));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.borrar().toString());
            throw ex;
        }
    }

    @PostMapping("/logout")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @io.swagger.v3.oas.annotations.Operation(summary = "Revocar la sesión y eliminar la cookie de renovación")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @org.springframework.web.bind.annotation.CookieValue(
                    name = com.asiscontrol.security.RefreshCookieSupport.NAME, required = false)
                    String refreshToken,
            jakarta.servlet.http.HttpServletRequest http,
            jakarta.servlet.http.HttpServletResponse response) {
        cookies.validarOrigen(http, refreshToken != null);
        renovacion.cerrar(refreshToken, authorization == null ? null : extractBearerToken(authorization));
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.borrar().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/me")
    public AuthDtos.UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getCurrentUser(jwt);
    }

    @PostMapping("/change-password")
    public AuthDtos.MessageResponse changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(jwt, request);
        return new AuthDtos.MessageResponse(
                "La contrasena fue actualizada; inicie sesion nuevamente");
    }

    @PostMapping("/recovery/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.MessageResponse solicitar(
            @Valid @RequestBody AuthDtos.RecoveryRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        // Solo IP del transporte: no confiar en X-Forwarded-For suministrado por el cliente.
        recuperacion.solicitar(request.identifier(), http.getRemoteAddr());
        return new AuthDtos.MessageResponse(com.asiscontrol.service.RecuperacionService.RESPUESTA);
    }

    @PostMapping("/recovery/validate")
    public AuthDtos.MessageResponse validar(
            @Valid @RequestBody AuthDtos.RecoveryTokenRequest request) {
        recuperacion.validar(request.token());
        return new AuthDtos.MessageResponse("Enlace válido");
    }

    @PostMapping("/recovery/reset")
    public AuthDtos.MessageResponse recuperar(
            @Valid @RequestBody AuthDtos.ResetRecoveryRequest request) {
        recuperacion.confirmar(request);
        return new AuthDtos.MessageResponse("Contraseña actualizada. Inicie sesión nuevamente");
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessRuleException("TOKEN_FALTANTE", "Debe enviar un token Bearer valido");
        }
        return authorization.substring(7);
    }

    private org.springframework.http.ResponseEntity<AuthDtos.AuthResponse> respuesta(
            com.asiscontrol.service.RenovacionSesionService.Credenciales credenciales) {
        return org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.crear(credenciales.refreshToken(), credenciales.expiresAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(credenciales.response());
    }
}
