-- El folio visible es secuencial; la capacidad QR conserva su referencia firmada original.
ALTER TABLE OrdenPago ADD COLUMN qr_referencia VARCHAR(45);
UPDATE OrdenPago SET qr_referencia = codigo WHERE qr_referencia IS NULL;
ALTER TABLE OrdenPago ADD CONSTRAINT uk_orden_pago_qr_referencia UNIQUE (qr_referencia);
UPDATE OrdenPago
SET codigo = CONCAT('OP-', LPAD(CONCAT('', id_orden_pago), GREATEST(6, CHAR_LENGTH(CONCAT('', id_orden_pago))), '0'));
