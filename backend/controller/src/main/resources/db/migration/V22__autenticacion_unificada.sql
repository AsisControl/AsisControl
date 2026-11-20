-- El corte de autenticación se ejecuta con las instancias anteriores detenidas.
-- Se conserva una credencial vigente por persona. Los identificadores y tokens anteriores
-- dejan de autenticar, pero las referencias históricas permanecen en la cuenta consolidada.
CREATE TEMPORARY TABLE CodigoAccesoReservado (
    anio INTEGER NOT NULL PRIMARY KEY,
    ultimo_valor BIGINT NOT NULL
);

-- Algún acceso legado puede tener un usuario numérico configurado manualmente.
-- No se asigna ese mismo identificador a otra cuenta ni se habilita como alias.
INSERT INTO CodigoAccesoReservado (anio, ultimo_valor)
SELECT CAST(LEFT(username, 4) AS DECIMAL(4, 0)),
       MAX(CAST(RIGHT(username, 4) AS DECIMAL(4, 0)))
FROM AccesoUsuario
WHERE username REGEXP '^[1-9][0-9]{7}$'
GROUP BY CAST(LEFT(username, 4) AS DECIMAL(4, 0));

CREATE TEMPORARY TABLE ConsolidacionAcceso (
    id_acceso_usuario BIGINT NOT NULL PRIMARY KEY,
    id_persona BIGINT NOT NULL UNIQUE,
    fecha_creacion DATETIME(6) NOT NULL,
    correo_verificado VARCHAR(150),
    anio INTEGER NOT NULL,
    correlativo BIGINT NOT NULL,
    codigo VARCHAR(8) NOT NULL UNIQUE
);

-- Las fechas de alta se guardan en UTC. Se toma el año de Lima del primer acceso
-- de la persona y se impide el corte si el correlativo no cabe en cuatro cifras.
INSERT INTO ConsolidacionAcceso (
    id_acceso_usuario, id_persona, fecha_creacion, correo_verificado, anio, correlativo, codigo
)
SELECT numerados.id_acceso_usuario, numerados.id_persona, numerados.fecha_creacion,
       numerados.correo_verificado, numerados.anio, numerados.correlativo,
       CASE WHEN numerados.anio BETWEEN 1000 AND 9999 AND numerados.correlativo BETWEEN 1 AND 9999
            THEN CONCAT(numerados.anio, LPAD(numerados.correlativo, 4, '0'))
       END
FROM (
    SELECT elegidos.id_acceso_usuario, elegidos.id_persona, historia.fecha_creacion,
           historia.correo_verificado, historia.anio,
           COALESCE(reserva.ultimo_valor, 0) + ROW_NUMBER() OVER (
               PARTITION BY historia.anio
               ORDER BY historia.fecha_creacion, elegidos.id_persona
           ) AS correlativo
    FROM (
        SELECT a.id_acceso_usuario, a.id_persona,
               ROW_NUMBER() OVER (PARTITION BY a.id_persona ORDER BY
                   CASE
                       WHEN a.activo = TRUE AND a.estado_acceso = 'ACTIVO'
                            AND (a.requiere_cambio_contrasena = FALSE
                                 OR a.contrasena_temporal_expira > UTC_TIMESTAMP(6)) THEN 0
                       WHEN a.activo = TRUE AND a.estado_acceso IN ('ACTIVO', 'BLOQUEADO_TEMPORALMENTE')
                            THEN 1
                       WHEN a.activo = TRUE AND a.estado_acceso = 'PENDIENTE' THEN 2
                       ELSE 3
                   END,
                   CASE WHEN r.nombre = 'ADMINISTRADOR' AND r.activo = TRUE
                                  AND administrador.activo = TRUE THEN 0
                        WHEN r.nombre IN ('DOCENTE', 'ALUMNO', 'APODERADO') AND r.activo = TRUE
                             THEN 1
                        ELSE 2
                   END,
                   CASE WHEN a.correo_verificado = TRUE THEN 0 ELSE 1 END,
                   a.fecha_creacion, a.id_acceso_usuario
               ) AS posicion
        FROM AccesoUsuario a
        JOIN Rol r ON r.id_rol = a.id_rol
        LEFT JOIN Administrador administrador ON administrador.id_persona = a.id_persona
    ) elegidos
    JOIN (
        SELECT id_persona, MIN(fecha_creacion) AS fecha_creacion,
               YEAR(TIMESTAMPADD(HOUR, -5, MIN(fecha_creacion))) AS anio,
               CASE WHEN COUNT(DISTINCT LOWER(correo_confirmado)) = 1 THEN MIN(correo_confirmado)
               END AS correo_verificado
        FROM (
            SELECT id_persona, fecha_creacion,
                   CASE WHEN correo_verificado = TRUE AND activo = TRUE
                                  AND estado_acceso IN ('ACTIVO', 'BLOQUEADO_TEMPORALMENTE')
                        THEN correo
                   END AS correo_confirmado
            FROM AccesoUsuario
        ) contactos
        GROUP BY id_persona
    ) historia ON historia.id_persona = elegidos.id_persona
    LEFT JOIN CodigoAccesoReservado reserva ON reserva.anio = historia.anio
    WHERE elegidos.posicion = 1
) numerados;

CREATE TABLE AccesoUsuarioRol (
    id_acceso_usuario_rol BIGINT NOT NULL AUTO_INCREMENT,
    id_acceso_usuario BIGINT NOT NULL,
    id_rol BIGINT NOT NULL,
    activo BOOLEAN NOT NULL,
    PRIMARY KEY (id_acceso_usuario_rol),
    CONSTRAINT uk_acceso_usuario_rol UNIQUE (id_acceso_usuario, id_rol),
    CONSTRAINT fk_acceso_usuario_rol_cuenta FOREIGN KEY (id_acceso_usuario)
        REFERENCES AccesoUsuario (id_acceso_usuario),
    CONSTRAINT fk_acceso_usuario_rol_rol FOREIGN KEY (id_rol) REFERENCES Rol (id_rol)
) ENGINE=InnoDB;

CREATE INDEX idx_acceso_usuario_rol_activo ON AccesoUsuarioRol (id_rol, activo, id_acceso_usuario);

-- Mapa de auditoría de los IDs y nombres anteriores. No almacena contraseñas,
-- tokens ni correos y no se consulta desde el mecanismo de inicio de sesión.
CREATE TABLE AccesoUsuarioLegado (
    id_acceso_legado BIGINT NOT NULL,
    identificador_legado VARCHAR(80) NOT NULL,
    id_acceso_usuario BIGINT NOT NULL,
    id_rol BIGINT NOT NULL,
    estado_acceso VARCHAR(35) NOT NULL,
    activo BOOLEAN NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    PRIMARY KEY (id_acceso_legado),
    CONSTRAINT uk_acceso_legado_identificador UNIQUE (identificador_legado),
    CONSTRAINT fk_acceso_legado_cuenta FOREIGN KEY (id_acceso_usuario)
        REFERENCES AccesoUsuario (id_acceso_usuario),
    CONSTRAINT fk_acceso_legado_rol FOREIGN KEY (id_rol) REFERENCES Rol (id_rol)
) ENGINE=InnoDB;

INSERT INTO AccesoUsuarioLegado (
    id_acceso_legado, identificador_legado, id_acceso_usuario, id_rol, estado_acceso, activo,
    fecha_creacion
)
SELECT a.id_acceso_usuario, a.username, c.id_acceso_usuario, a.id_rol, a.estado_acceso, a.activo,
       a.fecha_creacion
FROM AccesoUsuario a
JOIN ConsolidacionAcceso c ON c.id_persona = a.id_persona;

INSERT INTO AccesoUsuarioRol (id_acceso_usuario, id_rol, activo)
SELECT c.id_acceso_usuario, r.id_rol,
       CASE WHEN a.activo = TRUE AND p.activo = TRUE AND r.activo = TRUE
                     AND a.estado_acceso IN ('ACTIVO', 'BLOQUEADO_TEMPORALMENTE')
               THEN CASE r.nombre
                    WHEN 'ADMINISTRADOR' THEN COALESCE(administrador.activo, FALSE)
                    WHEN 'DOCENTE' THEN COALESCE(docente.activo, FALSE)
                    WHEN 'ALUMNO' THEN COALESCE(alumno.activo, FALSE)
                    WHEN 'APODERADO' THEN COALESCE(apoderado.activo, FALSE)
               END
            ELSE FALSE
       END
FROM AccesoUsuario a
JOIN ConsolidacionAcceso c ON c.id_persona = a.id_persona
JOIN Persona p ON p.id_persona = a.id_persona
JOIN Rol r ON r.id_rol = a.id_rol
LEFT JOIN Administrador administrador ON administrador.id_persona = a.id_persona
LEFT JOIN Docente docente ON docente.id_persona = a.id_persona
LEFT JOIN Alumno alumno ON alumno.id_persona = a.id_persona
LEFT JOIN Apoderado apoderado ON apoderado.id_persona = a.id_persona
WHERE r.nombre IN ('ADMINISTRADOR', 'DOCENTE', 'ALUMNO', 'APODERADO');

ALTER TABLE Notificacion ADD COLUMN id_rol_destinatario BIGINT NULL;
ALTER TABLE Notificacion ADD CONSTRAINT fk_notificacion_rol_destinatario
    FOREIGN KEY (id_rol_destinatario) REFERENCES Rol (id_rol);

UPDATE Notificacion
SET id_rol_destinatario = (
        SELECT l.id_rol FROM AccesoUsuarioLegado l
        WHERE l.id_acceso_legado = Notificacion.id_acceso_usuario
    ),
    id_acceso_usuario = (
        SELECT l.id_acceso_usuario FROM AccesoUsuarioLegado l
        WHERE l.id_acceso_legado = Notificacion.id_acceso_usuario
    );

ALTER TABLE SesionAcceso ADD COLUMN id_rol_activo BIGINT NULL;
ALTER TABLE SesionAcceso ADD COLUMN version_contexto BIGINT NOT NULL DEFAULT 0;
ALTER TABLE SesionAcceso ADD CONSTRAINT fk_sesion_rol_activo
    FOREIGN KEY (id_rol_activo) REFERENCES Rol (id_rol);

UPDATE SesionAcceso
SET id_rol_activo = (
        SELECT l.id_rol FROM AccesoUsuarioLegado l
        WHERE l.id_acceso_legado = SesionAcceso.id_acceso_usuario
    ),
    id_acceso_usuario = (
        SELECT l.id_acceso_usuario FROM AccesoUsuarioLegado l
        WHERE l.id_acceso_legado = SesionAcceso.id_acceso_usuario
    );

UPDATE SesionAcceso
SET estado_sesion = 'REVOCADA', fecha_revocacion = UTC_TIMESTAMP(6)
WHERE estado_sesion = 'VIGENTE';

UPDATE TokenRenovacion SET fecha_uso = UTC_TIMESTAMP(6) WHERE fecha_uso IS NULL;

UPDATE TokenRecuperacion
SET revocado = CASE WHEN usado IS NULL THEN COALESCE(revocado, UTC_TIMESTAMP(6)) ELSE revocado END,
    id_acceso_usuario = (
        SELECT l.id_acceso_usuario FROM AccesoUsuarioLegado l
        WHERE l.id_acceso_legado = TokenRecuperacion.id_acceso_usuario
    );

DELETE FROM AccesoUsuario
WHERE id_acceso_usuario NOT IN (SELECT id_acceso_usuario FROM ConsolidacionAcceso);

UPDATE AccesoUsuario
SET username = (
        SELECT c.codigo FROM ConsolidacionAcceso c
        WHERE c.id_acceso_usuario = AccesoUsuario.id_acceso_usuario
    ),
    fecha_actualizacion = UTC_TIMESTAMP(6),
    version = version + 1;

UPDATE AccesoUsuario
SET fecha_creacion = (
    SELECT c.fecha_creacion FROM ConsolidacionAcceso c
    WHERE c.id_acceso_usuario = AccesoUsuario.id_acceso_usuario
);

UPDATE AccesoUsuario
SET correo = COALESCE((
        SELECT c.correo_verificado FROM ConsolidacionAcceso c
        WHERE c.id_acceso_usuario = AccesoUsuario.id_acceso_usuario
    ), correo),
    correo_verificado = FALSE;

UPDATE AccesoUsuario
SET correo_verificado = TRUE
WHERE EXISTS (
    SELECT 1 FROM ConsolidacionAcceso c
    WHERE c.id_acceso_usuario = AccesoUsuario.id_acceso_usuario
      AND c.correo_verificado IS NOT NULL
);

ALTER TABLE AccesoUsuario ADD CONSTRAINT uk_acceso_usuario_persona UNIQUE (id_persona);
ALTER TABLE AccesoUsuario DROP INDEX uk_acceso_usuario_persona_rol;
ALTER TABLE AccesoUsuario DROP FOREIGN KEY FK4swherjbkoebn5tdkvfx1l8lt;
ALTER TABLE AccesoUsuario DROP COLUMN id_rol;

INSERT INTO ContadorCodigoInstitucional (prefijo, anio, ultimo_valor)
SELECT 'CTA', anio, MAX(correlativo)
FROM (
    SELECT anio, correlativo FROM ConsolidacionAcceso
    UNION ALL
    SELECT anio, ultimo_valor FROM CodigoAccesoReservado
) contadores
GROUP BY anio;

UPDATE Rol
SET activo = FALSE, fecha_actualizacion = UTC_TIMESTAMP(6), version = version + 1
WHERE nombre NOT IN ('ADMINISTRADOR', 'DOCENTE', 'ALUMNO', 'APODERADO') AND activo = TRUE;

DROP TABLE ConsolidacionAcceso;
DROP TABLE CodigoAccesoReservado;
