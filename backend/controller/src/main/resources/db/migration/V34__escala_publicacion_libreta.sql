ALTER TABLE InformeProgreso ADD COLUMN id_escala BIGINT NULL;
ALTER TABLE InformeProgreso ADD CONSTRAINT fk_informe_escala FOREIGN KEY (id_escala) REFERENCES EscalaInstitucional (id_escala);
ALTER TABLE DetalleInformeProgreso ADD COLUMN nota_numerica DECIMAL(12,10) NULL;
