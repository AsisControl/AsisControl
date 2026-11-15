package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdenPagoReferenciaMigracionTest {
    @Test
    void v12ConservaReferenciaQrImportesYClavesDeIdempotenciaYNoTruncaNumerosLargos() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:us13_v12;MODE=MySQL", "sa", "")) {
            var sql = conexion.createStatement();
            sql.execute("CREATE TABLE OrdenPago(id_orden_pago BIGINT PRIMARY KEY,codigo VARCHAR(45) UNIQUE,monto_total DECIMAL(12,2),clave_idempotencia VARCHAR(36))");
            sql.execute("INSERT INTO OrdenPago VALUES (1,'OP-11111111111111111111111111111111',350.00,'clave-uno'),(1000000,'OP-22222222222222222222222222222222',750.25,'clave-dos')");
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V12__referencias_orden_pago.sql"));
            try (var filas = sql.executeQuery("SELECT codigo,qr_referencia,monto_total,clave_idempotencia FROM OrdenPago ORDER BY id_orden_pago")) {
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString(1)).isEqualTo("OP-000001");
                assertThat(filas.getString(2)).isEqualTo("OP-11111111111111111111111111111111");
                assertThat(filas.getBigDecimal(3)).isEqualByComparingTo("350.00");
                assertThat(filas.getString(4)).isEqualTo("clave-uno");
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString(1)).isEqualTo("OP-1000000");
                assertThat(filas.getString(2)).isEqualTo("OP-22222222222222222222222222222222");
                assertThat(filas.getBigDecimal(3)).isEqualByComparingTo("750.25");
                assertThat(filas.getString(4)).isEqualTo("clave-dos");
                assertThat(filas.next()).isFalse();
            }
            assertThatThrownBy(() -> sql.execute("UPDATE OrdenPago SET qr_referencia='OP-11111111111111111111111111111111' WHERE id_orden_pago=1000000"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
