package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenovacionMatriculaMigracionTest {
    @Test
    void conservaMatriculaPreviaYPermiteReintentarSoloTrasUnRechazo() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:migracion_renovacion;MODE=MySQL", "sa", "");
             var sql = db.createStatement()) {
            sql.execute("CREATE TABLE Alumno(id_alumno BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Seccion(id_seccion BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Matricula(id_matricula BIGINT PRIMARY KEY, codigo VARCHAR(50))");
            sql.execute("INSERT INTO Alumno VALUES (1)");
            sql.execute("INSERT INTO Seccion VALUES (1)");
            sql.execute("INSERT INTO Matricula VALUES (1,'MAT-HISTORICA')");
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V17__renovacion_matricula.sql"));
            try (var row = sql.executeQuery("SELECT codigo, resultado_promocion FROM Matricula WHERE id_matricula = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("MAT-HISTORICA");
                assertThat(row.getString(2)).isNull();
            }
            String solicitud = """
                    INSERT INTO RenovacionMatricula(id_matricula_anterior,id_alumno,id_seccion_destino,
                        anio_destino,grado_destino,estado,clave_vigente,fecha_creacion,fecha_actualizacion)
                    VALUES (1,1,1,2091,2,'EN_REVISION',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """;
            sql.executeUpdate(solicitud);
            assertThatThrownBy(() -> sql.executeUpdate(solicitud)).isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("UK_RENOVACION_VIGENTE");
            sql.executeUpdate("UPDATE RenovacionMatricula SET estado = 'RECHAZADA', clave_vigente = NULL");
            assertThat(sql.executeUpdate(solicitud)).isOne();
        }
    }
}
