package com.asiscontrol.service;

import com.asiscontrol.entity.enums.SeveridadError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CorreoAcademicoListener {
    private final JavaMailSender sender;
    private final AuditoriaService auditoria;
    private final boolean habilitado;
    private final String remitente;
    private final String frontend;

    public CorreoAcademicoListener(JavaMailSender sender, AuditoriaService auditoria,
            @Value("${app.mail.enabled:false}") boolean habilitado,
            @Value("${app.mail.from:}") String remitente,
            @Value("${app.frontend-url:http://localhost:3000}") String frontend) {
        this.sender = sender;
        this.auditoria = auditoria;
        this.habilitado = habilitado;
        this.remitente = remitente;
        this.frontend = frontend.replaceAll("/+$", "");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(AvisosAcademicos.CorreoAcademico evento) {
        if (!habilitado) return;
        var correo = new SimpleMailMessage();
        correo.setFrom(remitente);
        correo.setTo(evento.correo());
        correo.setSubject("AsisControl · " + evento.titulo());
        correo.setText("Tienes una actualización académica. Ingresa a AsisControl para ver el detalle.\n\n"
                + frontend + "/progreso");
        try {
            sender.send(correo);
        } catch (org.springframework.mail.MailException | IllegalArgumentException ex) {
            auditoria.registrarError(SeveridadError.ERROR, "CorreoAcademico", "No se pudo enviar un aviso académico",
                    "Revisa la configuración SMTP. El aviso continúa disponible en AsisControl.");
        }
    }
}
