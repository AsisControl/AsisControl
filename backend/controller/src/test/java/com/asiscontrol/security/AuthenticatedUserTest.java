package com.asiscontrol.security;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.EstadoAcceso;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserTest {

    @Test
    void desbloqueoTemporalVencidoPermiteAutenticarNuevamente() {
        AccesoUsuario usuario = usuarioBase();
        usuario.setEstado(EstadoAcceso.BLOQUEADO_TEMPORALMENTE);
        usuario.setBloqueadoHasta(Instant.now().minusSeconds(1));

        AuthenticatedUser principal = new AuthenticatedUser(usuario);

        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void bloqueoTemporalVigenteImpideAutenticacion() {
        AccesoUsuario usuario = usuarioBase();
        usuario.setEstado(EstadoAcceso.BLOQUEADO_TEMPORALMENTE);
        usuario.setBloqueadoHasta(Instant.now().plusSeconds(60));

        AuthenticatedUser principal = new AuthenticatedUser(usuario);

        assertThat(principal.isAccountNonLocked()).isFalse();
        assertThat(principal.isEnabled()).isFalse();
    }

    private AccesoUsuario usuarioBase() {
        Rol rol = new Rol();
        rol.setNombre("ALUMNO");
        AccesoUsuario usuario = new AccesoUsuario();
        usuario.setActivo(true);
        usuario.setRol(rol);
        usuario.setPersona(new com.asiscontrol.entity.Persona());
        usuario.setEstado(EstadoAcceso.ACTIVO);
        usuario.setContrasenaHash("hash");
        usuario.setUsername("alumno");
        return usuario;
    }
}
