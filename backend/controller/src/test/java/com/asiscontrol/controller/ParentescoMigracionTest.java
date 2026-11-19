package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class ParentescoMigracionTest {
    @Test void admiteNoDeclaradoSinCambiarLosParentescosPrevios() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:parentesco_v16;MODE=MySQL", "sa", "");
             var sql = conexion.createStatement()) {
            sql.execute("CREATE TABLE AlumnoApoderado (id BIGINT PRIMARY KEY, parentesco ENUM('MADRE','OTRO_FAMILIAR','PADRE','TUTOR_LEGAL') NOT NULL)");
            sql.execute("INSERT INTO AlumnoApoderado VALUES (1,'MADRE'),(2,'PADRE'),(3,'TUTOR_LEGAL'),(4,'OTRO_FAMILIAR')");
            assertThatThrownBy(() -> sql.execute("INSERT INTO AlumnoApoderado VALUES (5,'NO_DECLARADO')"))
                    .isInstanceOf(java.sql.SQLException.class);
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V22__parentesco_no_declarado.sql"));
            sql.execute("INSERT INTO AlumnoApoderado VALUES (5,'NO_DECLARADO')");
            try (var filas = sql.executeQuery("SELECT parentesco FROM AlumnoApoderado ORDER BY id")) {
                var valores = new java.util.ArrayList<String>();
                while (filas.next()) valores.add(filas.getString(1));
                assertThat(valores).containsExactly("MADRE", "PADRE", "TUTOR_LEGAL", "OTRO_FAMILIAR", "NO_DECLARADO");
            }
        }
    }
}
