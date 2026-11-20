package com.asiscontrol.controller;

import com.asiscontrol.entity.ArchivoAdjunto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Configuración S3 real de Spring, cliente AWS simulado y permisos HTTP completos. */
@SpringBootTest(properties = {"app.storage.provider=s3", "app.storage.s3.bucket=asiscontrol-archivos",
        "app.storage.s3.region=us-east-1", "app.storage.s3.prefix=asiscontrol/adjuntos"})
class ArchivoS3HttpTest extends ApiIntegrationSupport {
    @TempDir static Path root;
    @MockitoBean S3Client s3;
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("app.storage.path", () -> root.toString());
    }

    @Test void cargaYDescargaEnS3ConservanPermisosYNoExponenLaUbicacionPrivada() throws Exception {
        byte[] pdf = "%PDF-1.4\nPrueba S3".getBytes();
        var result = mvc.perform(multipart("/api/archivos")
                .file(new MockMultipartFile("archivo", "prueba.pdf", "application/pdf", pdf)).with(asAdmin()))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var body = json.readTree(result.getContentAsByteArray());
        long id = body.path("id").asLong();
        assertThat(body.has("rutaArchivo")).isFalse();
        assertThat(em.find(ArchivoAdjunto.class, id).getRutaArchivo()).startsWith("s3://asiscontrol-archivos/");
        mvc.perform(get("/api/archivos/" + id + "/descarga").with(jwt()
                .jwt(j -> j.subject("otro-estudiante").claim("personaId", student.getPersona().getId()).claim("rol", "ALUMNO"))
                .authorities(new SimpleGrantedAuthority("ROLE_ALUMNO"))))
                .andExpect(status().isForbidden());
        verify(s3, never()).getObject(any(GetObjectRequest.class));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) pdf.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(pdf))));
        mvc.perform(get("/api/archivos/" + id + "/descarga").with(asAdmin()))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test void activarS3NoImpideDescargarArchivosLocalesAnteriores() throws Exception {
        byte[] pdf = "%PDF-1.4\nAnterior".getBytes();
        Files.write(root.resolve("anterior.pdf"), pdf);
        var archivo = new ArchivoAdjunto();
        archivo.setNombreOriginal("anterior.pdf"); archivo.setNombreAlmacenado("anterior.pdf");
        archivo.setRutaArchivo("anterior.pdf"); archivo.setTipoMime("application/pdf");
        archivo.setTamanioBytes(pdf.length); archivo.setSha256("0".repeat(64)); archivo.setCargadoPor(admin.getPersona());
        em.persist(archivo); em.flush();
        mvc.perform(get("/api/archivos/" + archivo.getId() + "/descarga").with(asAdmin()))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf));
        verifyNoInteractions(s3);
    }
}
