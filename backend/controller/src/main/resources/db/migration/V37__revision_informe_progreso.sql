ALTER TABLE InformeProgreso ADD COLUMN revisado BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE DetalleInformeProgreso ADD COLUMN estado_evaluacion VARCHAR(20) NULL;
