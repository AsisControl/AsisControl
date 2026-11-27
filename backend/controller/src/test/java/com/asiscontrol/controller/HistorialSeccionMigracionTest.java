package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistorialSeccionMigracionTest {
    @Test void creaElHistorialLigadoALaMatriculaYLaSeccionQueDejo() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:historial_seccion_migracion;MODE=MySQL");
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE Matricula (id_matricula BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Seccion (id_seccion BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO Matricula VALUES (1)");
            sql.execute("INSERT INTO Seccion VALUES (2)");
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V30__historial_seccion_matricula.sql"));
            String insertar = "INSERT INTO HistorialSeccionMatricula (id_matricula, id_seccion_anterior, fecha_cambio,"
                    + " fecha_cambio_local, fecha_creacion, fecha_actualizacion) VALUES (1, %d, NOW(), NOW(), NOW(), NOW())";
            sql.execute(insertar.formatted(2));
            assertThatThrownBy(() -> sql.execute(insertar.formatted(99))).isInstanceOf(SQLException.class);
            try (var fila = sql.executeQuery("SELECT activo, version FROM HistorialSeccionMatricula")) {
                assertThat(fila.next()).isTrue();
                assertThat(fila.getBoolean(1)).isTrue();
                assertThat(fila.getLong(2)).isZero();
            }
        }
    }
}
