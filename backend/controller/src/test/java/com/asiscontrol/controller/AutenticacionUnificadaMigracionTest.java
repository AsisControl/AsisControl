package com.asiscontrol.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AutenticacionUnificadaMigracionTest {

    @Test
    void conservaLaCuentaAdministrativaLosRolesValidosYElHistorial() throws SQLException {
        try (Connection conexion = abrirEsquemaAnterior()) {
            registrarPersona(conexion, 1);
            registrarPersona(conexion, 2);
            registrarPersona(conexion, 3);
            registrarPersona(conexion, 4);
            registrarRol(conexion, 1, "ADMINISTRADOR");
            registrarRol(conexion, 2, "DOCENTE");
            registrarRol(conexion, 3, "APODERADO");
            registrarRol(conexion, 4, "TESORERIA");
            ejecutar(conexion, """
                    INSERT INTO Administrador (id_persona, activo, fecha_creacion,
                        fecha_actualizacion, version) VALUES (1, TRUE, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 0)
                    """);
            ejecutar(conexion, """
                    INSERT INTO Docente (id_persona, activo, fecha_creacion, fecha_actualizacion,
                        version, codigo_docente, especialidad, fecha_ingreso)
                    VALUES (1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'DOC20260001', 'MATEMATICA', '2026-02-01')
                    """);
            ejecutar(conexion, """
                    INSERT INTO Apoderado (id_persona, activo, fecha_creacion, fecha_actualizacion,
                        version) VALUES (1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """);
            registrarAcceso(conexion, 11, 1, 2, "docente", "docente@example.test", true,
                    "ACTIVO", "2027-01-01 04:59:59");
            registrarAcceso(conexion, 12, 1, 1, "administrador", "admin@example.test", false,
                    "ACTIVO", "2027-01-01 05:00:00");
            registrarAcceso(conexion, 13, 1, 3, "apoderado", "apoderado@example.test", true,
                    "DESACTIVADO", "2027-01-01 05:00:01");
            registrarAcceso(conexion, 21, 2, 4, "20260001", "tesoreria@example.test", false,
                    "ACTIVO", "2027-01-01 05:00:00");
            registrarAcceso(conexion, 31, 3, 2, "bloqueado", "bloqueado@example.test", true,
                    "BLOQUEADO_TEMPORALMENTE", "2027-01-01 05:00:01");
            registrarAcceso(conexion, 32, 3, 3, "sin.perfil", "perfil@example.test", true,
                    "ACTIVO", "2027-01-01 05:00:02");
            ejecutar(conexion, """
                    INSERT INTO Notificacion (id_notificacion, id_acceso_usuario, activo,
                        fecha_creacion, fecha_actualizacion, version, leida, mensaje,
                        tipo_notificacion, titulo)
                    VALUES (101, 13, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, FALSE,
                        'Detalle', 'ASISTENCIA', 'Aviso')
                    """);
            ejecutar(conexion, """
                    INSERT INTO SesionAcceso (id_sesion_acceso, estado_sesion, fecha_creacion,
                        fecha_expiracion, token_hash, id_acceso_usuario, ultima_actividad)
                    VALUES (201, 'VIGENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'anterior', 11,
                        CURRENT_TIMESTAMP)
                    """);
            ejecutar(conexion, """
                    INSERT INTO TokenRenovacion (id_sesion_acceso, token_hash, fecha_creacion)
                    VALUES (201, 'renovacion', CURRENT_TIMESTAMP)
                    """);
            ejecutar(conexion, """
                    INSERT INTO TokenRecuperacion (id_acceso_usuario, token_hash, creado, expira)
                    VALUES (13, 'recuperacion', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            ejecutar(conexion, """
                    INSERT INTO RegistroAuditoria (accion, actor_identificador, correlacion_id,
                        entidad, fecha_hora, modulo, resultado, id_rol_actor)
                    VALUES ('CREAR', 'docente', 'historial', 'Alumno', CURRENT_TIMESTAMP,
                        'MATRICULA', 'EXITOSO', 2)
                    """);

            ejecutarMigracion(conexion);

            assertThat(consultar(conexion, "SELECT COUNT(*) FROM AccesoUsuario")).isEqualTo("3");
            assertThat(consultar(conexion, "SELECT COUNT(*) FROM Persona")).isEqualTo("4");
            assertThat(consultar(conexion, "SELECT username FROM AccesoUsuario WHERE id_acceso_usuario = 12"))
                    .isEqualTo("20260002");
            assertThat(consultar(conexion, "SELECT contrasena_hash FROM AccesoUsuario WHERE id_acceso_usuario = 12"))
                    .isEqualTo("hash-legado-12");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuario
                    WHERE id_acceso_usuario = 12 AND correo = 'docente@example.test'
                      AND correo_verificado = TRUE AND estado_acceso = 'ACTIVO'
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuario
                    WHERE id_acceso_usuario = 32 AND correo = 'perfil@example.test'
                      AND correo_verificado = FALSE
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, "SELECT username FROM AccesoUsuario WHERE id_acceso_usuario = 21"))
                    .isEqualTo("20270001");
            assertThat(consultar(conexion, "SELECT username FROM AccesoUsuario WHERE id_acceso_usuario = 32"))
                    .isEqualTo("20270002");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuarioRol
                    WHERE id_acceso_usuario = 12 AND id_rol IN (1, 2) AND activo = TRUE
                    """)).isEqualTo("2");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuarioRol
                    WHERE id_acceso_usuario = 12 AND id_rol = 3 AND activo = FALSE
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuarioRol WHERE id_acceso_usuario IN (21, 32)
                    AND activo = TRUE
                    """)).isEqualTo("0");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuarioLegado
                    WHERE id_acceso_usuario = 12 AND id_acceso_legado IN (11, 12, 13)
                    """)).isEqualTo("3");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM AccesoUsuarioLegado
                    WHERE id_acceso_legado = 21 AND identificador_legado = '20260001'
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM SesionAcceso
                    WHERE id_sesion_acceso = 201 AND id_acceso_usuario = 12 AND id_rol_activo = 2
                        AND estado_sesion = 'REVOCADA' AND fecha_revocacion IS NOT NULL
                        AND version_contexto = 0
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM TokenRenovacion
                    WHERE id_sesion_acceso = 201 AND fecha_uso IS NOT NULL
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM TokenRecuperacion
                    WHERE id_acceso_usuario = 12 AND revocado IS NOT NULL
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM Notificacion
                    WHERE id_notificacion = 101 AND id_acceso_usuario = 12 AND id_rol_destinatario = 3
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM RegistroAuditoria WHERE actor_identificador = 'docente'
                    AND id_rol_actor = 2
                    """)).isEqualTo("1");
            assertThat(consultar(conexion, "SELECT codigo_docente FROM Docente WHERE id_persona = 1"))
                    .isEqualTo("DOC20260001");
            assertThat(consultar(conexion, """
                    SELECT ultimo_valor FROM ContadorCodigoInstitucional WHERE prefijo = 'CTA' AND anio = 2026
                    """)).isEqualTo("2");
            assertThat(consultar(conexion, "SELECT COUNT(*) FROM Rol WHERE nombre = 'TESORERIA' AND activo = FALSE"))
                    .isEqualTo("1");
            assertThatThrownBy(() -> ejecutar(conexion, """
                    INSERT INTO AccesoUsuarioRol (id_acceso_usuario, id_rol, activo) VALUES (12, 1, TRUE)
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> ejecutar(conexion, """
                    INSERT INTO AccesoUsuario (activo, fecha_actualizacion, fecha_creacion,
                        version, contrasena_hash, correo, estado_acceso, intentos_fallidos,
                        requiere_cambio_contrasena, username, id_persona)
                    VALUES (TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'hash-nuevo',
                        'otro@example.test', 'ACTIVO', 0, FALSE, '20260003', 1)
                    """)).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void agotarUnCorrelativoDetieneElCorteAntesDeCambiarLosAccesos() throws SQLException {
        try (Connection conexion = abrirEsquemaAnterior()) {
            registrarPersona(conexion, 1);
            registrarRol(conexion, 1, "ADMINISTRADOR");
            registrarAcceso(conexion, 11, 1, 1, "20269999", "admin@example.test", false,
                    "ACTIVO", "2026-05-01 12:00:00");

            assertThatThrownBy(() -> ejecutarMigracion(conexion))
                    .isInstanceOf(ScriptStatementFailedException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(SQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo("23502"));
            assertThat(consultar(conexion, "SELECT username FROM AccesoUsuario WHERE id_acceso_usuario = 11"))
                    .isEqualTo("20269999");
            assertThat(consultar(conexion, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE UPPER(TABLE_NAME) = 'ACCESOUSUARIOROL'
                    """)).isEqualTo("0");
        }
    }

    @Test
    void admiteUnaInstalacionSinDatos() throws SQLException {
        try (Connection conexion = abrirEsquemaAnterior()) {
            ejecutarMigracion(conexion);
            assertThat(consultar(conexion, "SELECT COUNT(*) FROM AccesoUsuario")).isEqualTo("0");
            assertThat(consultar(conexion, "SELECT COUNT(*) FROM AccesoUsuarioRol")).isEqualTo("0");
            assertThat(consultar(conexion, "SELECT COUNT(*) FROM AccesoUsuarioLegado")).isEqualTo("0");
        }
    }

    private static Connection abrirEsquemaAnterior() throws SQLException {
        Connection conexion = DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        try {
            ejecutar(conexion, """
                    CREATE ALIAS UTC_TIMESTAMP FOR
                    "%s.ahoraUtc"
                    """.formatted(AutenticacionUnificadaMigracionTest.class.getName()));
            ejecutarScript(conexion, "V1__baseline_schema.sql");
            // Equivalente de los cambios de seguridad previos de V1.5 para H2. Su ALTER TABLE
            // múltiple se ejecuta tal cual al instalar Flyway sobre MySQL.
            ejecutar(conexion, "ALTER TABLE AccesoUsuario ADD COLUMN correo_verificado BIT NOT NULL DEFAULT 0");
            ejecutar(conexion, "ALTER TABLE AccesoUsuario ADD COLUMN contrasena_temporal_expira DATETIME(6)");
            ejecutar(conexion, "ALTER TABLE AccesoUsuario DROP FOREIGN KEY FK6y4p4xu62s3o68ce3bo8r519o");
            ejecutar(conexion, "ALTER TABLE AccesoUsuario DROP CONSTRAINT uk_acceso_usuario_persona");
            ejecutar(conexion, """
                    CREATE UNIQUE INDEX uk_acceso_usuario_persona_rol ON AccesoUsuario (id_persona, id_rol)
                    """);
            ejecutar(conexion, """
                    ALTER TABLE AccesoUsuario ADD CONSTRAINT fk_cuenta_persona_test
                    FOREIGN KEY (id_persona) REFERENCES Persona (id_persona)
                    """);
            ejecutar(conexion, "ALTER TABLE SesionAcceso ADD COLUMN ultima_actividad DATETIME(6) NOT NULL");
            ejecutar(conexion, """
                    CREATE TABLE TokenRecuperacion (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        id_acceso_usuario BIGINT NOT NULL REFERENCES AccesoUsuario (id_acceso_usuario),
                        token_hash VARCHAR(64) NOT NULL UNIQUE,
                        creado DATETIME(6) NOT NULL,
                        expira DATETIME(6) NOT NULL,
                        usado DATETIME(6),
                        revocado DATETIME(6)
                    )
                    """);
            ejecutarScript(conexion, "V3__renovacion_segura_de_sesiones.sql");
            ejecutar(conexion, """
                    CREATE TABLE ContadorCodigoInstitucional (
                        prefijo VARCHAR(3) NOT NULL,
                        anio INTEGER NOT NULL,
                        ultimo_valor BIGINT NOT NULL,
                        PRIMARY KEY (prefijo, anio)
                    )
                    """);
            return conexion;
        } catch (RuntimeException | SQLException ex) {
            try {
                conexion.close();
            } catch (SQLException cerrar) {
                ex.addSuppressed(cerrar);
            }
            throw ex;
        }
    }

    public static LocalDateTime ahoraUtc(int precision) {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static void registrarPersona(Connection conexion, int id) throws SQLException {
        ejecutar(conexion, """
                INSERT INTO Persona (id_persona, activo, fecha_actualizacion, fecha_creacion,
                    version, apellido_materno, apellido_paterno, correo, fecha_nacimiento, nombres,
                    numero_documento, sexo, tipo_documento)
                VALUES (%d, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'Ramos', 'Torres',
                    'persona-%d@example.test', '1990-01-01', 'Nombre', '%08d', 'FEMENINO', 'DNI')
                """.formatted(id, id, id));
    }

    private static void registrarRol(Connection conexion, int id, String rol) throws SQLException {
        ejecutar(conexion, """
                INSERT INTO Rol (id_rol, activo, fecha_actualizacion, fecha_creacion, version, nombre)
                VALUES (%d, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '%s')
                """.formatted(id, rol));
    }

    private static void registrarAcceso(Connection conexion, int id, int persona, int rol,
            String usuario, String correo, boolean verificado, String estado, String creacion)
            throws SQLException {
        try (var sentencia = conexion.prepareStatement("""
                INSERT INTO AccesoUsuario (id_acceso_usuario, activo, fecha_actualizacion,
                    fecha_creacion, version, contrasena_hash, correo, estado_acceso, intentos_fallidos,
                    requiere_cambio_contrasena, username, id_persona, id_rol, correo_verificado)
                VALUES (?, TRUE, CURRENT_TIMESTAMP, ?, 0, ?, ?, ?, 0, FALSE, ?, ?, ?, ?)
                """)) {
            sentencia.setInt(1, id);
            sentencia.setString(2, creacion);
            sentencia.setString(3, "hash-legado-" + id);
            sentencia.setString(4, correo);
            sentencia.setString(5, estado);
            sentencia.setString(6, usuario);
            sentencia.setInt(7, persona);
            sentencia.setInt(8, rol);
            sentencia.setBoolean(9, verificado);
            sentencia.executeUpdate();
        }
    }

    private static String consultar(Connection conexion, String sql) throws SQLException {
        try (var sentencia = conexion.createStatement(); var resultado = sentencia.executeQuery(sql)) {
            assertThat(resultado.next()).isTrue();
            return resultado.getString(1);
        }
    }

    private static void ejecutar(Connection conexion, String sql) throws SQLException {
        try (var sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        }
    }

    private static void ejecutarMigracion(Connection conexion) {
        ejecutarScript(conexion, "V22__autenticacion_unificada.sql");
    }

    private static void ejecutarScript(Connection conexion, String nombre) {
        ScriptUtils.executeSqlScript(conexion,
                new ClassPathResource("db/migration/" + nombre));
    }
}
