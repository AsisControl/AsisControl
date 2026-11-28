package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipamientoAulaMigracionTest {
    @Test void agregaElEquipamientoNuloEnLasAulasExistentesConLimiteDe500() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:equipamiento_aula_migracion;MODE=MySQL");
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE Aula (id_aula BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO Aula VALUES (1)");
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V46__equipamiento_aula.sql"));
            try (var fila = sql.executeQuery("SELECT equipamiento FROM Aula WHERE id_aula = 1")) {
                assertThat(fila.next()).isTrue();
                assertThat(fila.getString(1)).isNull();
            }
            sql.execute("UPDATE Aula SET equipamiento = '" + "a".repeat(500) + "'");
            assertThatThrownBy(() -> sql.execute("UPDATE Aula SET equipamiento = '" + "a".repeat(501) + "'"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
