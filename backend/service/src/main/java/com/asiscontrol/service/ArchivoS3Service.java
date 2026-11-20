package com.asiscontrol.service;

import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.nio.file.Path;
import java.util.Map;

/** Almacenamiento privado; el contenido se entrega a través del backend autorizado. */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class ArchivoS3Service {
    private final S3Client client;
    private final String bucket;
    private final String prefix;

    public ArchivoS3Service(S3Client client, @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.prefix:asiscontrol/adjuntos}") String prefix) {
        if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalStateException("Configura un nombre válido en FILE_STORAGE_S3_BUCKET.");
        if (prefix.length() > 200 || !prefix.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))
            throw new IllegalStateException("FILE_STORAGE_S3_PREFIX debe ser una ruta relativa sin barras al inicio o al final.");
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    public String almacenar(Path temporal, String relativa, String mime, String sha256) {
        validarRelativa(relativa);
        String key = prefix + "/" + relativa;
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime)
                    .metadata(Map.of("sha256", sha256)).serverSideEncryption(ServerSideEncryption.AES256).build(),
                    RequestBody.fromFile(temporal));
            return "s3://" + bucket + "/" + key;
        } catch (SdkException e) {
            throw new BusinessRuleException("ARCHIVO_ALMACENAMIENTO_FALLIDO", "No se pudo almacenar el archivo en S3.");
        }
    }

    public Resource abrir(String ubicacion) {
        String key = clave(ubicacion);
        try {
            return new InputStreamResource(client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new ResourceNotFoundException("ARCHIVO_FISICO_NO_ENCONTRADO", "El contenido solicitado no está disponible.");
            throw new BusinessRuleException("ARCHIVO_LECTURA_FALLIDA", "No se pudo leer el archivo solicitado en S3.");
        } catch (SdkException e) {
            throw new BusinessRuleException("ARCHIVO_LECTURA_FALLIDA", "No se pudo leer el archivo solicitado en S3.");
        }
    }

    /** Compensa únicamente cargas nuevas cuya persistencia ha fallado. */
    public void eliminarCargaFallida(String ubicacion) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave(ubicacion)).build());
    }

    private String clave(String ubicacion) {
        String inicio = "s3://" + bucket + "/" + prefix + "/";
        if (!ubicacion.startsWith(inicio)) throw rutaInvalida();
        String relativa = ubicacion.substring(inicio.length());
        validarRelativa(relativa);
        return prefix + "/" + relativa;
    }

    private void validarRelativa(String relativa) {
        if (!relativa.matches("[0-9a-f]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw rutaInvalida();
    }

    private BusinessRuleException rutaInvalida() {
        return new BusinessRuleException("ARCHIVO_RUTA_INVALIDA", "La ubicación del archivo no es válida.");
    }
}
