package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenciaPedagogicaMigracionTest {

    @Test
    void agregaTablasReferencialesSobreUnEsquemaExistente() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:evidencia_migracion;MODE=MySQL", "sa", "");
             var sql = conexion.createStatement()) {
            sql.execute("CREATE TABLE AsignacionCurso (id_asignacion_curso BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Alumno (id_alumno BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Competencia (id_competencia BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE PeriodoAcademico (id_periodo_academico BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Persona (id_persona BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE ArchivoAdjunto (id_archivo BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE Evaluacion (id_evaluacion BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO AsignacionCurso VALUES (1)");
            sql.execute("INSERT INTO Alumno VALUES (1)");
            sql.execute("INSERT INTO Competencia VALUES (1)");
            sql.execute("INSERT INTO PeriodoAcademico VALUES (1)");
            sql.execute("INSERT INTO Persona VALUES (1)");
            sql.execute("INSERT INTO Evaluacion VALUES (1)");

            ScriptUtils.executeSqlScript(conexion,
                    new ClassPathResource("db/migration/V15__evidencias_pedagogicas.sql"));
            sql.executeUpdate("""
                    INSERT INTO EvidenciaPedagogica (id_evidencia_pedagogica, id_asignacion_curso,
                        id_alumno, id_competencia, id_periodo_academico, id_persona_autor,
                        descripcion, fecha_creacion, fecha_actualizacion, activo, version)
                    VALUES (1, 1, 1, 1, 1, 1, 'Descripción de prueba', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 0)
                    """);
            assertThat(sql.executeUpdate("INSERT INTO EvidenciaPedagogicaEvaluacion VALUES (1, 1)")).isEqualTo(1);
            assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO EvidenciaPedagogicaEvaluacion VALUES (1, 1)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO EvidenciaPedagogicaEvaluacion VALUES (1, 2)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM EvidenciaPedagogica WHERE id_evidencia_pedagogica = 1"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
