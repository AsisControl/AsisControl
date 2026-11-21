package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdenPagoRestriccionH2Test {

    @Test
    void validaElMedioYEstadoDeUnaOrdenTrasCerrarLaConexionQueCreoLaTabla() throws SQLException {
        String url = "jdbc:h2:mem:ordenes_reconexion;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var esquema = DriverManager.getConnection(url, "sa", "");
             var ddl = esquema.createStatement()) {
            ddl.execute("""
                    CREATE TABLE OrdenPago (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        metodo VARCHAR(12) NOT NULL CHECK (metodo IN ('TARJETA', 'QR')),
                        estado VARCHAR(30) NOT NULL CHECK (estado IN ('PENDIENTE', 'EXPIRADO'))
                    )
                    """);
        }

        try (var conexion = DriverManager.getConnection(url, "sa", "");
             var sql = conexion.createStatement()) {
            sql.execute("SET DB_CLOSE_DELAY 0");
            assertThat(sql.executeUpdate("INSERT INTO OrdenPago(metodo, estado) VALUES ('TARJETA', 'PENDIENTE')"))
                    .isEqualTo(1);
            assertThat(sql.executeUpdate("UPDATE OrdenPago SET estado = 'EXPIRADO' WHERE metodo = 'TARJETA'"))
                    .isEqualTo(1);
            assertThat(sql.executeUpdate("INSERT INTO OrdenPago(metodo, estado) VALUES ('QR', 'PENDIENTE')"))
                    .isEqualTo(1);
            assertThatThrownBy(() -> sql.executeUpdate(
                    "INSERT INTO OrdenPago(metodo, estado) VALUES ('EFECTIVO', 'PENDIENTE')"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23513"));
        }
    }
}
