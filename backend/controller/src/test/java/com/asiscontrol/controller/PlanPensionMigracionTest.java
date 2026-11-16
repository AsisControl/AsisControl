package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanPensionMigracionTest {
    @Test void migracionConservaPagosLegadosYProtegeTarifasYReferencias()throws Exception{
        try(var conexion=DriverManager.getConnection("jdbc:h2:mem:planes_v13;MODE=MySQL","sa","")){
            var sql=conexion.createStatement();
            sql.execute("CREATE TABLE Alumno(id_alumno BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO Alumno VALUES(1)");
            sql.execute("CREATE TABLE CompromisoPago(id_compromiso_pago BIGINT PRIMARY KEY,codigo_compromiso VARCHAR(50),monto_total DECIMAL(12,2),monto_pagado DECIMAL(12,2),estado_compromiso ENUM('PENDIENTE','PAGADO') NOT NULL)");
            sql.execute("INSERT INTO CompromisoPago VALUES(1,'CP-LEGADO',350.00,350.00,'PAGADO')");
            ScriptUtils.executeSqlScript(conexion,new ClassPathResource("db/migration/V13__planes_tarifas_beneficios.sql"));
            try(var row=sql.executeQuery("SELECT codigo_compromiso,monto_total,monto_pagado,estado_compromiso,monto_base,monto_descuento FROM CompromisoPago WHERE id_compromiso_pago=1")){
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("CP-LEGADO");
                assertThat(row.getBigDecimal(2)).isEqualByComparingTo("350.00");assertThat(row.getBigDecimal(3)).isEqualByComparingTo("350.00");
                assertThat(row.getString(4)).isEqualTo("PAGADO");assertThat(row.getBigDecimal(5)).isEqualByComparingTo("350.00");assertThat(row.getBigDecimal(6)).isZero();
            }
            try(var row=sql.executeQuery("SELECT codigo,nombre,monto_sugerido FROM ConceptoCobro")){
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("PENSION");assertThat(row.getBigDecimal(3)).isNull();assertThat(row.next()).isFalse();
            }
            String tarifa="INSERT INTO TarifaPension(activo,fecha_actualizacion,fecha_creacion,version,id_concepto_cobro,anio_academico,grado,monto_mensual) VALUES(1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,1,2090,1,400.00)";
            sql.execute(tarifa);assertThatThrownBy(()->sql.execute(tarifa)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("UK_TARIFA_PENSION_ANIO_GRADO");
            sql.execute("INSERT INTO CompromisoPago(id_compromiso_pago,codigo_compromiso,monto_total,monto_pagado,estado_compromiso,id_concepto_cobro,id_tarifa,monto_base,monto_descuento) VALUES(2,'CP-EXONERADO',0,0,'EXONERADO',1,1,400,400)");
            assertThatThrownBy(()->sql.execute("UPDATE CompromisoPago SET id_beneficio=999 WHERE id_compromiso_pago=1")).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("FK_COMPROMISO_BENEFICIO");
            assertThatThrownBy(()->sql.execute("UPDATE CompromisoPago SET id_tarifa=999 WHERE id_compromiso_pago=1")).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("FK_COMPROMISO_TARIFA");
        }
    }
}
