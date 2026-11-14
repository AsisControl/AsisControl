-- Referencias CP-000001, CP-000002... globales, persistentes y no reutilizables al anular.
-- No altera códigos existentes ni sus relaciones, transacciones o auditoría.
CREATE TABLE ContadorCodigoCompromiso (
    id_contador INTEGER NOT NULL PRIMARY KEY,
    ultimo_valor BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO ContadorCodigoCompromiso (id_contador, ultimo_valor) VALUES (1, 0);

-- La primera emisión inicializa el contador desde las referencias numéricas históricas
-- bajo bloqueo de esta fila; los UUID y códigos externos conservan su valor original.
