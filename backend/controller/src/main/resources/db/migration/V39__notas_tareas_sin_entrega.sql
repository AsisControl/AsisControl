CREATE TABLE NotaTareaSinEntrega (
    id_nota_tarea_sin_entrega BIGINT NOT NULL AUTO_INCREMENT,
    id_tarea BIGINT NOT NULL,
    id_alumno BIGINT NOT NULL,
    nota DECIMAL(14,10) NOT NULL,
    PRIMARY KEY (id_nota_tarea_sin_entrega),
    CONSTRAINT uk_nota_tarea_sin_entrega UNIQUE (id_tarea, id_alumno),
    CONSTRAINT fk_nota_tarea_sin_entrega_tarea FOREIGN KEY (id_tarea) REFERENCES Tarea(id_tarea),
    CONSTRAINT fk_nota_tarea_sin_entrega_alumno FOREIGN KEY (id_alumno) REFERENCES Alumno(id_alumno)
);
