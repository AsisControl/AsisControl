package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaCalificacionMigracionTest {
    @Test void migraSinReinterpretarLasLibretasAnteriores() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:reglas_migracion;MODE=MySQL");
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE Curso (id_curso BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE GradoAcademico (id_grado_academico BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE InformeProgreso (id_informe_progreso BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO InformeProgreso (id_informe_progreso) VALUES (1)");
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V29__reglas_calificacion.sql"));
            try (var fila = sql.executeQuery("SELECT id_regla_calificacion, consolidado_regla FROM InformeProgreso")) {
                assertThat(fila.next()).isTrue();
                assertThat(fila.getObject(1)).isNull();
                assertThat(fila.getString(2)).isNull();
            }
        }
    }
}
