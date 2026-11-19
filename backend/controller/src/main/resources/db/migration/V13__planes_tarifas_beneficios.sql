-- Configuración escolar persistente. No define importes por defecto.
CREATE TABLE ConceptoCobro (
 id_concepto_cobro BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 activo BIT NOT NULL, fecha_actualizacion DATETIME(6) NOT NULL, fecha_creacion DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 codigo VARCHAR(40) NOT NULL, nombre VARCHAR(160) NOT NULL, descripcion VARCHAR(600), periodicidad VARCHAR(12) NOT NULL,
 es_pension BIT NOT NULL, monto_sugerido DECIMAL(12,2), requiere_aceptacion BIT NOT NULL,
 CONSTRAINT uk_concepto_cobro_codigo UNIQUE(codigo)
) ENGINE=InnoDB;
INSERT INTO ConceptoCobro(activo,fecha_actualizacion,fecha_creacion,version,codigo,nombre,periodicidad,es_pension,requiere_aceptacion)
VALUES(1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),0,'PENSION','Pensión','MENSUAL',1,0);
CREATE TABLE TarifaPension (
 id_tarifa_pension BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 activo BIT NOT NULL, fecha_actualizacion DATETIME(6) NOT NULL, fecha_creacion DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 id_concepto_cobro BIGINT NOT NULL, anio_academico INT NOT NULL, grado INT NOT NULL, monto_mensual DECIMAL(12,2) NOT NULL,
 CONSTRAINT uk_tarifa_pension_anio_grado UNIQUE(id_concepto_cobro,anio_academico,grado),
 CONSTRAINT fk_tarifa_concepto FOREIGN KEY(id_concepto_cobro) REFERENCES ConceptoCobro(id_concepto_cobro)
) ENGINE=InnoDB;
CREATE TABLE BeneficioEstudiante (
 id_beneficio_estudiante BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 activo BIT NOT NULL, fecha_actualizacion DATETIME(6) NOT NULL, fecha_creacion DATETIME(6) NOT NULL, version BIGINT NOT NULL,
 id_alumno BIGINT NOT NULL, id_concepto_cobro BIGINT NOT NULL, anio_academico INT NOT NULL, nombre VARCHAR(160) NOT NULL,
 tipo VARCHAR(20) NOT NULL, valor DECIMAL(12,2) NOT NULL, mes_inicio INT NOT NULL, mes_fin INT NOT NULL, motivo VARCHAR(500) NOT NULL,
 CONSTRAINT fk_beneficio_alumno FOREIGN KEY(id_alumno) REFERENCES Alumno(id_alumno),
 CONSTRAINT fk_beneficio_concepto FOREIGN KEY(id_concepto_cobro) REFERENCES ConceptoCobro(id_concepto_cobro)
) ENGINE=InnoDB;
CREATE INDEX ix_beneficio_vigencia ON BeneficioEstudiante(id_alumno,anio_academico,id_concepto_cobro,mes_inicio,mes_fin);
ALTER TABLE CompromisoPago MODIFY COLUMN estado_compromiso VARCHAR(20) NOT NULL;
ALTER TABLE CompromisoPago ADD COLUMN id_concepto_cobro BIGINT;
ALTER TABLE CompromisoPago ADD COLUMN mes_pension INT;
ALTER TABLE CompromisoPago ADD COLUMN referencia_cobro VARCHAR(100);
ALTER TABLE CompromisoPago ADD COLUMN monto_base DECIMAL(12,2);
ALTER TABLE CompromisoPago ADD COLUMN monto_descuento DECIMAL(12,2);
ALTER TABLE CompromisoPago ADD COLUMN beneficio_nombre VARCHAR(160);
ALTER TABLE CompromisoPago ADD COLUMN id_beneficio BIGINT;
ALTER TABLE CompromisoPago ADD COLUMN id_tarifa BIGINT;
ALTER TABLE CompromisoPago ADD COLUMN tarifa_version BIGINT;
UPDATE CompromisoPago SET monto_base=monto_total,monto_descuento=0;
ALTER TABLE CompromisoPago ADD CONSTRAINT fk_compromiso_concepto FOREIGN KEY(id_concepto_cobro) REFERENCES ConceptoCobro(id_concepto_cobro);
ALTER TABLE CompromisoPago ADD CONSTRAINT fk_compromiso_beneficio FOREIGN KEY(id_beneficio) REFERENCES BeneficioEstudiante(id_beneficio_estudiante);
ALTER TABLE CompromisoPago ADD CONSTRAINT fk_compromiso_tarifa FOREIGN KEY(id_tarifa) REFERENCES TarifaPension(id_tarifa_pension);
