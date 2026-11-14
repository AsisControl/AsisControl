package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdenPagoMigracionTest {
    @Test
    void migraSinDuplicarObligacionesNiAlterarPagosLegadosYProtegeRelacionesEIdempotencia() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:us13_v11;MODE=MySQL", "sa", "")) {
            var sql = conexion.createStatement();
            sql.execute("CREATE TABLE Apoderado (id_apoderado BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE CompromisoPago (id_compromiso_pago BIGINT PRIMARY KEY, codigo_compromiso VARCHAR(50), monto_total DECIMAL(12,2))");
            sql.execute("CREATE TABLE TransaccionPago (id_transaccion_pago BIGINT PRIMARY KEY, medio_pago ENUM('EFECTIVO','YAPE') NOT NULL, monto DECIMAL(12,2))");
            sql.execute("INSERT INTO Apoderado VALUES (1)");
            sql.execute("INSERT INTO CompromisoPago VALUES (1,'CP-000001',350.00),(2,'CP-HISTORICO',175.25)");
            sql.execute("INSERT INTO TransaccionPago VALUES (1,'YAPE',100.00)");

            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V11__pagos_familia_simulados.sql"));

            try (var filas = sql.executeQuery("SELECT codigo_compromiso,monto_total FROM CompromisoPago ORDER BY id_compromiso_pago")) {
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString(1)).isEqualTo("CP-000001");
                assertThat(filas.getBigDecimal(2)).isEqualByComparingTo("350.00");
                assertThat(filas.next()).isTrue();
                assertThat(filas.getString(1)).isEqualTo("CP-HISTORICO");
                assertThat(filas.getBigDecimal(2)).isEqualByComparingTo("175.25");
                assertThat(filas.next()).isFalse();
            }
            try (var legado = sql.executeQuery("SELECT medio_pago,monto FROM TransaccionPago WHERE id_transaccion_pago=1")) {
                assertThat(legado.next()).isTrue();
                assertThat(legado.getString(1)).isEqualTo("YAPE");
                assertThat(legado.getBigDecimal(2)).isEqualByComparingTo("100.00");
            }
            // La ampliación de medios conserva Yape legado y admite los nuevos medios explícitamente simulados.
            sql.execute("INSERT INTO TransaccionPago VALUES (2,'TARJETA_SIMULADA',250.00),(3,'QR_SIMULADO',175.25)");
            String orden = """
                    INSERT INTO OrdenPago (id_orden_pago,activo,fecha_actualizacion,fecha_creacion,version,
                    codigo,id_apoderado,clave_idempotencia,seleccion_hash,metodo,estado,monto_base,mora,
                    monto_total,moneda,creada_en,expira_en)
                    VALUES (1,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'OP-PRUEBA',1,
                    '11111111-1111-1111-1111-111111111111','hash-prueba','QR','PENDIENTE',250.00,0.00,
                    250.00,'PEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """;
            sql.execute(orden);
            assertThatThrownBy(() -> sql.execute(orden.replace("(1,TRUE", "(2,TRUE").replace("OP-PRUEBA", "OP-OTRA")))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("UK_ORDEN_PAGO_IDEMPOTENCIA");
            sql.execute("""
                    INSERT INTO OrdenPagoDetalle (id_orden_pago_detalle,activo,fecha_actualizacion,fecha_creacion,
                    version,id_orden_pago,id_compromiso_pago,id_transaccion_pago,monto,codigo_compromiso,alumno,concepto,numero_cuota)
                    VALUES (1,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1,1,2,250.00,'CP-000001','Estudiante prueba','Pensión',1)
                    """);
            assertThatThrownBy(() -> sql.execute("""
                    INSERT INTO OrdenPagoDetalle (id_orden_pago_detalle,activo,fecha_actualizacion,fecha_creacion,
                    version,id_orden_pago,id_compromiso_pago,id_transaccion_pago,monto,codigo_compromiso,alumno,concepto,numero_cuota)
                    VALUES (2,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,999,2,3,175.25,'CP-HISTORICO','Estudiante prueba','Pensión',2)
                    """))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("FK_ORDEN_DETALLE_ORDEN");
            var columnas = new ArrayList<String>();
            try (var metadata = conexion.getMetaData().getColumns(null, null, "ORDENPAGO", "%")) {
                while (metadata.next()) columnas.add(metadata.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
            }
            assertThat(columnas).contains("monto_total", "clave_idempotencia", "payment_intent_id")
                    .doesNotContain("qr_token", "client_secret", "numero_tarjeta", "cvv");
        }
    }
}
