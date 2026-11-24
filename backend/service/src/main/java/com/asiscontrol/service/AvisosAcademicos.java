package com.asiscontrol.service;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.TipoNotificacion;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AvisosAcademicos {
    private final AccesoUsuarioRepository cuentas;
    private final AccesoUsuarioRolRepository roles;
    private final AlumnoApoderadoRepository vinculos;
    private final NotificacionService notificaciones;
    private final ApplicationEventPublisher eventos;

    public AvisosAcademicos(AccesoUsuarioRepository cuentas, AccesoUsuarioRolRepository roles,
            AlumnoApoderadoRepository vinculos, NotificacionService notificaciones, ApplicationEventPublisher eventos) {
        this.cuentas = cuentas;
        this.roles = roles;
        this.vinculos = vinculos;
        this.notificaciones = notificaciones;
        this.eventos = eventos;
    }

    public void enviar(Alumno alumno, String titulo, String evaluacion) {
        avisar(alumno.getPersona().getId(), "ALUMNO", titulo, "Consulta tu resultado de «" + evaluacion + "» en Progreso.");
        vinculos.findAllByAlumnoIdAndActivoTrue(alumno.getId()).stream()
                .filter(vinculo -> vinculo.getApoderado().isActivo() && vinculo.getApoderado().getPersona().isActivo())
                .map(vinculo -> vinculo.getApoderado().getPersona().getId()).distinct()
                .forEach(persona -> avisar(persona, "APODERADO", titulo,
                        "Hay una actualización en «" + evaluacion + "» para " + alumno.getPersona().nombreCompleto() + "."));
    }

    private void avisar(Long persona, String rol, String titulo, String mensaje) {
        for (var cuenta : cuentas.findAllByPersonaIdAndActivoTrue(persona)) {
            if (cuenta.getEstado() != EstadoAcceso.ACTIVO
                    || !roles.existsByUsuarioIdAndRolNombreAndActivoTrue(cuenta.getId(), rol)) continue;
            notificaciones.create(cuenta.getId(), titulo, mensaje, TipoNotificacion.ACADEMICA, "/progreso", rol);
            if (cuenta.isCorreoVerificado() && cuenta.getCorreo() != null && !cuenta.getCorreo().isBlank()) {
                eventos.publishEvent(new CorreoAcademico(cuenta.getCorreo(), titulo));
            }
        }
    }

    record CorreoAcademico(String correo, String titulo) {
        @Override public String toString() { return "CorreoAcademico"; }
    }
}
