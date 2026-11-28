package com.asiscontrol.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "DevolucionTareaSinEntrega")
public class DevolucionTareaSinEntrega {
    @Embeddable
    public record Clave(@Column(name = "id_tarea") Long tarea,
                        @Column(name = "id_alumno") Long alumno) implements Serializable {}

    @EmbeddedId
    private Clave id;
    @Column(nullable = false, length = 2000)
    private String comentario;

    protected DevolucionTareaSinEntrega() {}

    public DevolucionTareaSinEntrega(Clave id, String comentario) {
        this.id = id;
        this.comentario = comentario;
    }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
}
