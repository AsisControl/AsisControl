CREATE TABLE DevolucionTareaSinEntrega (
    id_tarea BIGINT NOT NULL,
    id_alumno BIGINT NOT NULL,
    comentario VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id_tarea, id_alumno),
    FOREIGN KEY (id_tarea) REFERENCES Tarea(id_tarea),
    FOREIGN KEY (id_alumno) REFERENCES Alumno(id_alumno)
);
