package com.asiscontrol.service;

import com.asiscontrol.config.ArchivoS3Configuration;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchivoS3ServiceTest {
    @TempDir Path root;
    private S3Client client;
    private ArchivoS3Service service;
    private static final String RELATIVA = "aa/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String UBICACION = "s3://asiscontrol-archivos/asiscontrol/adjuntos/" + RELATIVA;

    @BeforeEach void preparar() {
        client = mock(S3Client.class);
        service = new ArchivoS3Service(client, "asiscontrol-archivos", "asiscontrol/adjuntos");
    }

    @Test void enviaContenidoTipoHashYCifradoSinAclPublica() throws Exception {
        Path temporal = Files.write(root.resolve("carga.tmp"), new byte[]{1, 2, 3});
        assertThat(service.almacenar(temporal, RELATIVA, "application/pdf", "abc123")).isEqualTo(UBICACION);
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("asiscontrol-archivos");
        assertThat(request.getValue().key()).isEqualTo("asiscontrol/adjuntos/" + RELATIVA);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().metadata()).containsEntry("sha256", "abc123");
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().acl()).isNull();
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test void descargaPrivadaEntregaUnStreamCerrable() throws Exception {
        var bytes = new byte[]{1, 2, 3};
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(3L).build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes))));
        try (var input = service.abrir(UBICACION).getInputStream()) { assertThat(input.readAllBytes()).isEqualTo(bytes); }
        var request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("asiscontrol-archivos");
        assertThat(request.getValue().key()).isEqualTo("asiscontrol/adjuntos/" + RELATIVA);
    }

    @Test void diferenciaObjetoAusenteDeUnFalloDePermisosSinRevelarDetallesAws() {
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(() -> service.abrir(UBICACION)).isInstanceOf(ResourceNotFoundException.class);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(403).message("Detalle privado AWS").build());
        assertThatThrownBy(() -> service.abrir(UBICACION)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("No se pudo leer el archivo solicitado en S3.");
    }

    @Test void rechazaOtroBucketPrefijoORutaAntesDeConsultarAws() {
        for (String ruta : new String[]{"s3://otro-bucket/asiscontrol/adjuntos/" + RELATIVA,
                "s3://asiscontrol-archivos/otro/" + RELATIVA,
                "s3://asiscontrol-archivos/asiscontrol/adjuntos/../../secreto"}) {
            assertThatThrownBy(() -> service.abrir(ruta)).isInstanceOf(BusinessRuleException.class);
        }
        verifyNoInteractions(client);
    }

    @Test void compensaLaCargaEnLaMismaClaveSinBorrarOtrosObjetos() {
        service.eliminarCargaFallida(UBICACION);
        var request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("asiscontrol/adjuntos/" + RELATIVA);
    }

    @Test void faltaDeCredencialesSeInformaSinExponerElErrorInterno() throws Exception {
        Path temporal = Files.write(root.resolve("carga.tmp"), new byte[]{1});
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("Detalle privado de credenciales"));
        assertThatThrownBy(() -> service.almacenar(temporal, RELATIVA, "application/pdf", "hash"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("No se pudo almacenar el archivo en S3.");
    }

    @Test void validaConfiguracionSinNecesitarCredencialesReales() {
        assertThatThrownBy(() -> new ArchivoS3Service(client, "", "asiscontrol/adjuntos"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ArchivoS3Service(client, "asiscontrol-archivos", "../adjuntos"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ArchivoS3Configuration().archivoS3Client(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
