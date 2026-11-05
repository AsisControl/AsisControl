package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompromisoPagoMigracionTest {

    @Test
    void migraHistorialSinInventarCuotasNiAlterarImportesYProtegeClavesActivas() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:us12_migration;MODE=MySQL", "sa", "")) {
            var sql = conexion.createStatement();
            sql.execute("""
                    CREATE TABLE CompromisoPago (
                        id_compromiso_pago BIGINT PRIMARY KEY,
                        concepto VARCHAR(160) NOT NULL,
                        monto_total DECIMAL(12,2) NOT NULL,
                        moneda VARCHAR(3) NOT NULL,
                        estado_compromiso VARCHAR(20) NOT NULL,
                        activo BOOLEAN NOT NULL,
                        fecha_actualizacion TIMESTAMP NOT NULL)
                    """);
            sql.execute("""
                    INSERT INTO CompromisoPago VALUES
                    (1, CONCAT(CHAR(9), ' Pensión   Escolar', CHAR(13)), 250.50, 'PEN', 'PENDIENTE', TRUE, CURRENT_TIMESTAMP),
                    (2, 'Inscripción', 180.00, 'USD', 'ANULADO', FALSE, CURRENT_TIMESTAMP)
                    """);
            ScriptUtils.executeSqlScript(conexion,
                    new ClassPathResource("db/migration/V6__compromisos_pago_administrativos.sql"));
            try (var filas = sql.executeQuery("SELECT * FROM CompromisoPago ORDER BY id_compromiso_pago")) {
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString("concepto_normalizado")).isEqualTo("pensión escolar");
                assertThat(filas.getObject("numero_cuota")).isNull();
                assertThat(filas.getBigDecimal("monto_total")).isEqualByComparingTo("250.50");
                assertThat(filas.getString("estado_administrativo")).isEqualTo("ACTIVO");
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString("estado_administrativo")).isEqualTo("ANULADO");
                assertThat(filas.getBoolean("activo")).isTrue();
                assertThat(filas.getString("moneda")).isEqualTo("USD");
                assertThat(filas.getTimestamp("fecha_anulacion")).isNotNull();
            }
            sql.executeUpdate("UPDATE CompromisoPago SET clave_obligacion='clave-de-prueba' WHERE id_compromiso_pago=1");
            assertThatThrownBy(() -> sql.executeUpdate(
                    "UPDATE CompromisoPago SET clave_obligacion='clave-de-prueba' WHERE id_compromiso_pago=2"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
