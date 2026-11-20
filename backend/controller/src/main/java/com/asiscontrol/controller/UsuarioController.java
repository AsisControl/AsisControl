package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.seguridad.UsuarioDtos;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public PageResponse<UsuarioDtos.Response> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EstadoAcceso estado,
            @RequestParam(required = false) Long rolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "fechaCreacion") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        return usuarioService.list(search, estado, rolId, page, size, sortBy, direction);
    }

    @GetMapping("/personas")
    public PageResponse<UsuarioDtos.PersonaAccesoResponse> personas(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return usuarioService.listPersonas(search, page, size);
    }

    @PostMapping("/administradores")
    public ResponseEntity<UsuarioDtos.RegistroAdministradorResponse> registrarAdministrador(
            @Valid @RequestBody UsuarioDtos.RegistroAdministradorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(usuarioService.registrarAdministrador(request));
    }

    @GetMapping("/{id}")
    public UsuarioDtos.Response get(@PathVariable Long id) {
        return usuarioService.get(id);
    }

    @PutMapping("/{id}")
    public UsuarioDtos.Response update(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioDtos.UpdateRequest request
    ) {
        return usuarioService.update(id, request);
    }

    @PutMapping("/{id}/roles/{rolId}")
    public UsuarioDtos.Response cambiarRol(
            @PathVariable Long id, @PathVariable Long rolId,
            @Valid @RequestBody UsuarioDtos.CambiarRolRequest request) {
        return usuarioService.cambiarRol(id, rolId, request);
    }

    @PutMapping("/{id}/contrasena-temporal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioDtos.ResetPasswordRequest request
    ) {
        usuarioService.resetPassword(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        usuarioService.delete(id);
    }
}
