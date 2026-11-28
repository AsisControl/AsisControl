-- Solo metadatos: los datos de exposición se cargan desde archivos externos.
ALTER TABLE CargaDemostracion ADD COLUMN hash_archivo VARCHAR(64) NULL;
ALTER TABLE CargaDemostracion ADD COLUMN indicaciones TEXT NULL;
