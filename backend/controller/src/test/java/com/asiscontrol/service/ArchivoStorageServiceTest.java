package com.asiscontrol.service;

import com.asiscontrol.entity.*;
import com.asiscontrol.exception.*;
import com.asiscontrol.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchivoStorageServiceTest {
    @TempDir Path root;
    private ArchivoAdjuntoRepository repo;
    private PersonaRepository personas;
    private ModuloAccessGuard guard;
    private ArchivoStorageService service;
    private ArchivoAdjunto saved;
    private ObjectProvider<ArchivoS3Service> proveedorS3;

    @BeforeEach void setup() {
        repo = mock(ArchivoAdjuntoRepository.class);
        personas = mock(PersonaRepository.class);
        guard = mock(ModuloAccessGuard.class);
        proveedorS3 = mock(ObjectProvider.class);
        service = new ArchivoStorageService(repo, personas, guard, mock(EvidenciaPedagogicaRepository.class),
                root.toString(), 1024, "local", proveedorS3);
        var persona = new Persona(); persona.setId(7L);
        when(guard.personaIdActual()).thenReturn(7L);
        when(personas.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(persona));
        when(repo.save(any())).thenAnswer(inv -> {
            saved = inv.getArgument(0); saved.setId(1L); return saved;
        });
    }

    @AfterEach void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void cargaEnS3ConservaMetadatosYDescargaSinCopiaPermanenteEnDisco() throws Exception {
        var s3 = mock(ArchivoS3Service.class);
        when(proveedorS3.getIfAvailable()).thenReturn(s3);
        service = new ArchivoStorageService(repo, personas, guard, mock(EvidenciaPedagogicaRepository.class),
                root.toString(), 1024, "s3", proveedorS3);
        var bytes = new byte[]{0x25,0x50,0x44,0x46};
        when(s3.almacenar(any(), anyString(), eq("application/pdf"), anyString()))
                .thenAnswer(inv -> {
                    assertThat(Files.readAllBytes(inv.getArgument(0, Path.class))).isEqualTo(bytes);
                    return "s3://asiscontrol-archivos/asiscontrol/adjuntos/" + inv.getArgument(1, String.class);
                });
        service.almacenar(new MockMultipartFile("file", "x.pdf", "text/plain", bytes));
        assertThat(saved.getRutaArchivo()).startsWith("s3://asiscontrol-archivos/");
        assertThat(saved.getSha256()).hasSize(64);
        when(repo.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(saved));
        when(s3.abrir(saved.getRutaArchivo())).thenReturn(new ByteArrayResource(bytes));
        assertThat(service.descargar(1L).recurso().getContentAsByteArray()).isEqualTo(bytes);
        try (var paths = Files.walk(root)) { assertThat(paths.filter(Files::isRegularFile).count()).isZero(); }
    }

    @Test void falloDeS3NoGuardaMetadatosNiRecurreSilenciosamenteAlDisco() throws Exception {
        var s3 = mock(ArchivoS3Service.class);
        when(proveedorS3.getIfAvailable()).thenReturn(s3);
        service = new ArchivoStorageService(repo, personas, guard, mock(EvidenciaPedagogicaRepository.class),
                root.toString(), 1024, "s3", proveedorS3);
        when(s3.almacenar(any(), anyString(), anyString(), anyString())).thenThrow(new BusinessRuleException("S3_ERROR", "Fallo S3"));
        assertThatThrownBy(() -> service.almacenar(new MockMultipartFile("file", "x.pdf", null, new byte[]{0x25,0x50,0x44,0x46})))
                .hasMessage("Fallo S3");
        verify(repo, never()).save(any());
        try (var paths = Files.walk(root)) { assertThat(paths.filter(Files::isRegularFile).count()).isZero(); }
    }

    @Test void rollbackPosteriorAlSaveLimpiaLaCargaNuevaEnS3() {
        var s3 = mock(ArchivoS3Service.class);
        when(proveedorS3.getIfAvailable()).thenReturn(s3);
        service = new ArchivoStorageService(repo, personas, guard, mock(EvidenciaPedagogicaRepository.class),
                root.toString(), 1024, "s3", proveedorS3);
        String ubicacion = "s3://asiscontrol-archivos/asiscontrol/adjuntos/aa/00000000-0000-0000-0000-000000000000";
        when(s3.almacenar(any(), anyString(), anyString(), anyString())).thenReturn(ubicacion);
        TransactionSynchronizationManager.initSynchronization();
        service.almacenar(new MockMultipartFile("file", "x.pdf", null, new byte[]{0x25,0x50,0x44,0x46}));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(s3).eliminarCargaFallida(ubicacion);
    }

    @Test void modoS3TodaviaPuedeLeerUnAdjuntoLocalAnterior() throws Exception {
        var bytes = new byte[]{0x25,0x50,0x44,0x46};
        service.almacenar(new MockMultipartFile("file", "x.pdf", null, bytes));
        when(repo.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(saved));
        service = new ArchivoStorageService(repo, personas, guard, mock(EvidenciaPedagogicaRepository.class),
                root.toString(), 1024, "s3", proveedorS3);
        assertThat(service.descargar(1L).recurso().getContentAsByteArray()).isEqualTo(bytes);
        verifyNoInteractions(proveedorS3);
    }

    @Test void falloInmediatoDeBdLimpiaS3SinOcultarElErrorOriginal() {
        var s3 = mock(ArchivoS3Service.class);
        when(proveedorS3.getIfAvailable()).thenReturn(s3);
        service = new ArchivoStorageService(repo, personas, guard, mock(EvidenciaPedagogicaRepository.class),
                root.toString(), 1024, "s3", proveedorS3);
        String ubicacion = "s3://asiscontrol-archivos/asiscontrol/adjuntos/aa/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        when(s3.almacenar(any(), anyString(), anyString(), anyString())).thenReturn(ubicacion);
        doThrow(new IllegalStateException("Fallo BD")).when(repo).save(any());
        doThrow(new IllegalStateException("Fallo limpieza")).when(s3).eliminarCargaFallida(ubicacion);
        assertThatThrownBy(() -> service.almacenar(new MockMultipartFile("file", "x.pdf", null, new byte[]{0x25,0x50,0x44,0x46})))
                .hasMessage("Fallo BD");
        verify(s3).eliminarCargaFallida(ubicacion);
    }

    static Stream<Object[]> firmas() {
        return Stream.of(
                new Object[]{"archivo.pdf", "text/plain", new byte[]{0x25,0x50,0x44,0x46}},
                new Object[]{"archivo.png", "image/png", new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a}},
                new Object[]{"archivo.jpg", "image/jpeg", new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff}},
                new Object[]{"archivo.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{0x50,0x4b}},
                new Object[]{"archivo.doc", "application/msword", new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0}});
    }

    @ParameterizedTest @MethodSource("firmas")
    void almacenaDetectaElFormatoCalculaHashYDescarga(String name, String mime, byte[] bytes) throws Exception {
        var response = service.almacenar(new MockMultipartFile("file", "../../" + name, mime, bytes));
        assertThat(response.nombreOriginal()).isEqualTo(name);
        assertThat(response.sha256()).hasSize(64);
        assertThat(response.tamanioBytes()).isEqualTo(bytes.length);
        when(repo.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(saved));
        when(repo.buscarActivoParaVincularOEliminar(1L)).thenReturn(Optional.of(saved));
        var download = service.descargar(1L);
        assertThat(download.recurso().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(service.obtener(1L).id()).isEqualTo(1L);
        service.eliminarLogicamente(1L);
        assertThat(saved.isActivo()).isFalse();
    }

    @Test void rechazaEjecutableAunqueDeclareSerPdfYTamaniosInvalidos() {
        assertThatThrownBy(() -> service.almacenar(
                new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{'M','Z',1,2})))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.almacenar(null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.almacenar(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.almacenar(new MockMultipartFile("file", new byte[1025])))
                .isInstanceOf(BusinessRuleException.class);
        verify(repo, never()).save(any());
    }

    @Test void impideDescargaFueraDeLaCarpetaYReportaArchivoFisicoAusente() {
        var a = new ArchivoAdjunto(); a.setId(1L); a.setRutaArchivo("../secreto");
        when(repo.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.descargar(1L)).isInstanceOf(BusinessRuleException.class);
        a.setRutaArchivo("inexistente");
        assertThatThrownBy(() -> service.descargar(1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.obtener(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void validaIdsAdjuntosYEliminaDuplicados() {
        assertThat(service.obtenerActivos(null)).isEmpty();
        assertThat(service.obtenerActivos(Set.of())).isEmpty();
        assertThatThrownBy(() -> service.obtenerActivos(Arrays.asList(1L, null)))
                .isInstanceOf(BusinessRuleException.class);
        var file = new ArchivoAdjunto(); file.setId(1L);
        when(repo.findAllByIdInAndActivoTrue(Set.of(1L))).thenReturn(List.of(file));
        assertThat(service.obtenerActivos(List.of(1L, 1L))).containsExactly(file);
        assertThatThrownBy(() -> service.obtenerActivos(List.of(2L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void limpiaElArchivoFisicoSiLaPersistenciaFalla() throws Exception {
        doThrow(new IllegalStateException("Fallo BD")).when(repo).save(any());
        assertThatThrownBy(() -> service.almacenar(
                new MockMultipartFile("file", "x.pdf", null, new byte[]{0x25,0x50,0x44,0x46})))
                .hasMessage("Fallo BD");
        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(Files::isRegularFile).count()).isZero();
        }
    }

    @Test void normalizaNombreVacioYLargoYManejaErrorDeLectura() throws Exception {
        var bytes = new byte[]{0x25,0x50,0x44,0x46};
        assertThat(service.almacenar(new MockMultipartFile("file", "..", null, bytes)).nombreOriginal())
                .isEqualTo("archivo");
        assertThat(service.almacenar(new MockMultipartFile("file", "a".repeat(300), null, bytes)).nombreOriginal())
                .hasSize(255);
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getInputStream()).thenThrow(new IOException("lectura"));
        assertThatThrownBy(() -> service.almacenar(broken)).isInstanceOf(BusinessRuleException.class);
    }
}
