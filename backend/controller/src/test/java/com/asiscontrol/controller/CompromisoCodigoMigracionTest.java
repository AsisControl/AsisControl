package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class CompromisoCodigoMigracionTest {
    @Test
    void creaContadorSinRenumerarNiModificarCompromisosExistentes() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:us12_codigo_migration;MODE=MySQL", "sa", "")) {
            var sql = conexion.createStatement();
            sql.execute("""
                    CREATE TABLE CompromisoPago (
                        id_compromiso_pago BIGINT PRIMARY KEY,
                        codigo_compromiso VARCHAR(50),
                        monto_total DECIMAL(12,2),
                        estado_administrativo VARCHAR(20))
                    """);
            sql.execute("""
                    INSERT INTO CompromisoPago VALUES
                    (1, 'CP-69EE961C-900A-47D2-AA16-463368D6D962', 350.00, 'ACTIVO'),
                    (2, 'CP-000023', 250.50, 'ANULADO')
                    """);
            ScriptUtils.executeSqlScript(conexion,
                    new ClassPathResource("db/migration/V10__correlativo_codigo_compromiso.sql"));
            try (var filas = sql.executeQuery("SELECT * FROM CompromisoPago ORDER BY id_compromiso_pago")) {
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString("codigo_compromiso"))
                        .isEqualTo("CP-69EE961C-900A-47D2-AA16-463368D6D962");
                assertThat(filas.getBigDecimal("monto_total")).isEqualByComparingTo("350.00");
                assertThat(filas.getString("estado_administrativo")).isEqualTo("ACTIVO");
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString("codigo_compromiso")).isEqualTo("CP-000023");
                assertThat(filas.getBigDecimal("monto_total")).isEqualByComparingTo("250.50");
                assertThat(filas.getString("estado_administrativo")).isEqualTo("ANULADO");
            }
            try (var contador = sql.executeQuery("SELECT * FROM ContadorCodigoCompromiso")) {
                assertThat(contador.next()).isTrue();
                assertThat(contador.getInt("id_contador")).isEqualTo(1);
                assertThat(contador.getLong("ultimo_valor")).isZero();
                assertThat(contador.next()).isFalse();
            }
        }
    }
}
