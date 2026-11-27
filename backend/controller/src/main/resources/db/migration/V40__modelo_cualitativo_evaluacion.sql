ALTER TABLE Evaluacion ADD COLUMN modelo_cualitativo BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE Tarea ADD COLUMN modelo_cualitativo BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE EntregaTarea ADD COLUMN nivel_logro VARCHAR(2) NULL;
ALTER TABLE NotaTareaSinEntrega MODIFY COLUMN nota DECIMAL(14,10) NULL;
ALTER TABLE NotaTareaSinEntrega ADD COLUMN nivel_logro VARCHAR(2) NULL;
ALTER TABLE NotaTareaSinEntrega ADD CONSTRAINT ck_nota_tarea_sin_entrega_modelo CHECK (
    (nota IS NOT NULL AND nivel_logro IS NULL)
    OR (nota IS NULL AND nivel_logro IS NOT NULL AND nivel_logro IN ('AD','A','B','C'))
);
