package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfertaCobroMigracionTest {
    @Test
    void v14ConservaDatosPreviosYProtegeReferenciasDeOfertasSinCrearDeuda() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:ofertas_v14;MODE=MySQL", "sa", "")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE Matricula(id_matricula BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE ConceptoCobro(id_concepto_cobro BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE CompromisoPago(id_compromiso_pago BIGINT PRIMARY KEY,monto_total DECIMAL(12,2))");
            sql.execute("CREATE TABLE Apoderado(id_apoderado BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO Matricula VALUES(1)"); sql.execute("INSERT INTO ConceptoCobro VALUES(1)");
            sql.execute("INSERT INTO Apoderado VALUES(1)"); sql.execute("INSERT INTO CompromisoPago VALUES(1,350.00)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V14__ofertas_cobro_opcionales.sql"));
            String insert = """
                    INSERT INTO OfertaCobro(id_matricula,id_concepto_cobro,concepto_nombre,referencia,
                    clave_oferta,titulo,monto_base,fecha_vencimiento,fecha_limite_aceptacion,estado,
                    fecha_creacion,fecha_actualizacion)
                    VALUES(1,1,'Actividad opcional','ACT-2090','clave-unica','Actividad',400.00,
                    '2090-08-31','2090-08-30','PROPUESTA',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """;
            sql.execute(insert);
            assertThatThrownBy(() -> sql.execute(insert)).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.execute(insert.replace("'clave-unica'", "'otra-clave'").replace("VALUES(1,1,", "VALUES(999,1,")))
                    .isInstanceOf(java.sql.SQLException.class);
            try (var previous = sql.executeQuery("SELECT COUNT(*),SUM(monto_total) FROM CompromisoPago")) {
                previous.next(); assertThat(previous.getInt(1)).isEqualTo(1);
                assertThat(previous.getBigDecimal(2)).isEqualByComparingTo("350.00");
            }
            try (var offer = sql.executeQuery("SELECT estado,id_compromiso_pago FROM OfertaCobro")) {
                assertThat(offer.next()).isTrue(); assertThat(offer.getString(1)).isEqualTo("PROPUESTA");
                assertThat(offer.getObject(2)).isNull(); assertThat(offer.next()).isFalse();
            }
            sql.execute("UPDATE OfertaCobro SET id_compromiso_pago=1,estado='ACEPTADA',monto_aceptado=300.00,descuento_aceptado=100.00 WHERE id_oferta_cobro=1");
            sql.execute(insert.replace("'clave-unica'", "'segunda-clave'"));
            assertThatThrownBy(() -> sql.execute("UPDATE OfertaCobro SET id_compromiso_pago=1 WHERE clave_oferta='segunda-clave'"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
