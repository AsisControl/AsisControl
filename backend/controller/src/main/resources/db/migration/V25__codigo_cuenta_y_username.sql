-- La cuenta conserva su código permanente (AAAANNNN) en una columna propia. El nombre de usuario
-- vuelve a ser un valor legible (nombre.apellido) que el sistema genera al arrancar para las
-- cuentas que todavía tienen el código en esa columna (RegeneracionIdentificadoresCuenta).
ALTER TABLE AccesoUsuario ADD COLUMN codigo VARCHAR(20) NULL;

UPDATE AccesoUsuario SET codigo = username;

ALTER TABLE AccesoUsuario MODIFY COLUMN codigo VARCHAR(20) NOT NULL;
ALTER TABLE AccesoUsuario ADD CONSTRAINT uk_acceso_usuario_codigo UNIQUE (codigo);
