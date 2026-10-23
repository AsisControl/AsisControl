package com.asiscontrol.security;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.repository.AccesoUsuarioRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AccessUserDetailsService implements UserDetailsService {

    private final AccesoUsuarioRepository usuarioRepository;

    public AccessUserDetailsService(AccesoUsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(noRollbackFor = org.springframework.security.core.AuthenticationException.class)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        AccesoUsuario usuario =
                usuarioRepository
                        .findActiveByIdentifier(identifier)
                        .orElseThrow(() -> new UsernameNotFoundException("Credenciales invalidas"));
        unlockIfExpired(usuario);
        return new AuthenticatedUser(usuario);
    }

    private void unlockIfExpired(AccesoUsuario usuario) {
        if (usuario.getEstado() == EstadoAcceso.BLOQUEADO_TEMPORALMENTE
                && usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isBefore(Instant.now())) {
            usuario.setEstado(EstadoAcceso.ACTIVO);
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
            usuarioRepository.save(usuario);
        }
    }
}
