package com.asiscontrol.service;

import com.asiscontrol.exception.ConflictException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SentenciasDemostracionTest {
    @Test void separaSinRomperTextosComentariosNiComillasEscapadas() {
        assertThat(SentenciasDemostracion.separar("\uFEFF-- cabecera\nSELECT 'no; dividir', 'Mía''s'; /* comentario; */ INSERT INTO t VALUES (1); # fin\nUPDATE t SET a = \"texto;\""))
                .containsExactly("SELECT 'no; dividir', 'Mía''s'", "INSERT INTO t VALUES (1)", "UPDATE t SET a = \"texto;\"");
        assertThat(SentenciasDemostracion.separar("SELECT `campo;con;comillas` FROM t;")).hasSize(1);
    }
    @Test void rechazaDDLTransaccionesBorradosYFormatosAmbiguos() {
        for (String sql : new String[]{"DROP TABLE t", "DELETE FROM t", "COMMIT", "CALL demo()", "DELIMITER $$", "SELECT 'sin cerrar", "/* sin cerrar", "/*! SELECT 1 */", "SELECT 'a\\b'", "-- vacío", "SELECT 1;".repeat(2001)})
            assertThatThrownBy(() -> SentenciasDemostracion.separar(sql)).isInstanceOf(ConflictException.class);
    }
}
