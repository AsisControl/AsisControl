package com.asiscontrol.service;

import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.SesionAccesoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class SesionActividadService {

    private final SesionAccesoRepository sesiones;
    private final AccesoUsuarioRolRepository asignaciones;

    public SesionActividadService(
            SesionAccesoRepository sesiones, AccesoUsuarioRolRepository asignaciones) {
        this.sesiones = sesiones;
        this.asignaciones = asignaciones;
    }

    @Transactional
    public boolean validar(long sesionId, long version, Long usuarioId, String rol) {
        Instant now = Instant.now();
        if (sesiones.registrarActividad(sesionId, version, now, now.minus(Duration.ofMinutes(30))) != 1) {
            return false;
        }
        var sesion = sesiones.findById(sesionId).orElseThrow();
        if (!Objects.equals(usuarioId, sesion.getUsuario().getId())) return false;
        var activo = sesion.getRolActivo();
        if (activo == null) return rol == null;
        return activo.getNombre().equals(rol) && asignaciones.buscarDisponibles(usuarioId).stream()
                .anyMatch(disponible -> disponible.getId().equals(activo.getId()));
    }
}
