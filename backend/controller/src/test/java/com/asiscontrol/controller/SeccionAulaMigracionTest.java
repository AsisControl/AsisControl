package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeccionAulaMigracionTest {
    @Test void agregaElAulaOpcionalYCompartibleConClaveForanea() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:seccion_aula_migracion;MODE=MySQL");
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE Aula (id_aula BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Seccion (id_seccion BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO Aula VALUES (7)");
            sql.execute("INSERT INTO Seccion VALUES (1)");
            sql.execute("INSERT INTO Seccion VALUES (2)");
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V47__aula_seccion.sql"));
            try (var fila = sql.executeQuery("SELECT id_aula FROM Seccion WHERE id_seccion = 1")) {
                assertThat(fila.next()).isTrue();
                assertThat(fila.getObject(1)).isNull();
            }
            assertThat(sql.executeUpdate("UPDATE Seccion SET id_aula = 7")).isEqualTo(2);
            assertThatThrownBy(() -> sql.execute("UPDATE Seccion SET id_aula = 99 WHERE id_seccion = 1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("DELETE FROM Aula WHERE id_aula = 7"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
