package com.asiscontrol.security;

import com.asiscontrol.entity.AccesoUsuario;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class AuthenticatedUser implements UserDetails {

    private final AccesoUsuario usuario;
    private final Set<GrantedAuthority> authorities;

    public AuthenticatedUser(AccesoUsuario usuario) {
        this.usuario = usuario;
        this.authorities = buildAuthorities(usuario);
    }

    public AccesoUsuario getUsuario() {
        return usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return usuario.getContrasenaHash();
    }

    @Override
    public String getUsername() {
        return usuario.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return usuario.getBloqueadoHasta() == null
                || usuario.getBloqueadoHasta().isBefore(java.time.Instant.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !usuario.isRequiereCambioContrasena()
                || (usuario.getContrasenaTemporalExpira() != null
                        && usuario.getContrasenaTemporalExpira().isAfter(java.time.Instant.now()));
    }

    @Override
    public boolean isEnabled() {
        if (!usuario.isActivo()
                || usuario.getPersona() == null || !usuario.getPersona().isActivo()
                || !usuario.getRol().isActivo()
                || !RolesInstitucionales.NOMBRES.contains(usuario.getRol().getNombre())) {
            return false;
        }
        if (usuario.getEstado() == com.asiscontrol.entity.enums.EstadoAcceso.ACTIVO) {
            return true;
        }
        return usuario.getEstado()
                        == com.asiscontrol.entity.enums.EstadoAcceso.BLOQUEADO_TEMPORALMENTE
                && usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isBefore(java.time.Instant.now());
    }

    private Set<GrantedAuthority> buildAuthorities(AccesoUsuario accesoUsuario) {
        Set<GrantedAuthority> result = new LinkedHashSet<>();
        result.add(
                new SimpleGrantedAuthority(
                        "ROLE_" + accesoUsuario.getRol().getNombre().toUpperCase()));
        accesoUsuario.getRol().getPermisos().stream()
                .filter(permission -> permission.isActivo())
                .map(permission -> new SimpleGrantedAuthority(permission.getCodigo()))
                .forEach(result::add);
        return Set.copyOf(result);
    }
}
