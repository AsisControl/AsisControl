package com.asiscontrol.service;


import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.dto.archivo.ArchivoDtos.ArchivoResponse;
import com.asiscontrol.entity.ArchivoAdjunto;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.ArchivoAdjuntoRepository;
import com.asiscontrol.repository.EvidenciaPedagogicaRepository;
import com.asiscontrol.repository.PersonaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ArchivoStorageService {

    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final ArchivoAdjuntoRepository archivoRepository;
    private final EvidenciaPedagogicaRepository evidenciaRepository;
    private final PersonaRepository personaRepository;
    private final ModuloAccessGuard accessGuard;
    private final Path directorioRaiz;
    private final long tamanioMaximoBytes;
    private final boolean usarS3;
    private final ObjectProvider<ArchivoS3Service> s3;

    public ArchivoStorageService(
            ArchivoAdjuntoRepository archivoRepository,
            PersonaRepository personaRepository,
            ModuloAccessGuard accessGuard,
            EvidenciaPedagogicaRepository evidenciaRepository,
            @Value("${app.storage.path:./storage}") String directorio,
            @Value("${app.storage.tamanio-maximo-bytes:10485760}") long tamanioMaximoBytes,
            @Value("${app.storage.provider:local}") String proveedor,
            ObjectProvider<ArchivoS3Service> s3) {
        if (!Set.of("local", "s3").contains(proveedor))
            throw new IllegalStateException("FILE_STORAGE_PROVIDER debe ser local o s3.");
        this.archivoRepository = archivoRepository;
        this.evidenciaRepository = evidenciaRepository;
        this.personaRepository = personaRepository;
        this.accessGuard = accessGuard;
        this.directorioRaiz = Path.of(directorio).toAbsolutePath().normalize();
        this.tamanioMaximoBytes = tamanioMaximoBytes;
        this.usarS3 = "s3".equals(proveedor);
        this.s3 = s3;
    }

    @Transactional
    public ArchivoResponse almacenar(MultipartFile archivo) {
        validarArchivoBasico(archivo);
        var persona = personaRepository.findByIdAndActivoTrue(accessGuard.personaIdActual())
                .orElseThrow(() -> new ResourceNotFoundException("PERSONA_AUTENTICADA_NO_ENCONTRADA",
                        "No existe el perfil de la persona autenticada"));

        String nombreOriginal = limpiarNombre(archivo.getOriginalFilename());
        String identificador = UUID.randomUUID().toString();
        Path directorioDestino = directorioRaiz.resolve(identificador.substring(0, 2)).normalize();
        Path destino = directorioDestino.resolve(identificador).normalize();
        verificarRutaSegura(destino);

        Path temporal = null;
        try {
            Files.createDirectories(directorioDestino);
            temporal = Files.createTempFile(directorioDestino, ".carga-", ".tmp");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String tipoMime;
            try (BufferedInputStream entrada = new BufferedInputStream(archivo.getInputStream())) {
                tipoMime = detectarTipoMime(entrada, archivo.getContentType(), nombreOriginal);
                try (DigestInputStream entradaConHash = new DigestInputStream(entrada, digest)) {
                    Files.copy(entradaConHash, temporal, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            long tamanioReal = Files.size(temporal);
            if (tamanioReal <= 0 || tamanioReal > tamanioMaximoBytes) {
                throw new BusinessRuleException(
                        "ARCHIVO_TAMANIO_INVALIDO",
                        "El archivo debe tener contenido y no exceder " + tamanioMaximoBytes + " bytes");
            }

            String hash = HexFormat.of().formatHex(digest.digest());
            String relativa = directorioRaiz.relativize(destino).toString().replace('\\', '/');
            String ubicacion;
            if (usarS3) {
                ubicacion = servicioS3().almacenar(temporal, relativa, tipoMime, hash);
            } else {
                moverAtomico(temporal, destino);
                temporal = null;
                ubicacion = relativa;
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) limpiarCargaFallida(ubicacion);
                    }
                });
            }

            ArchivoAdjunto entidad = new ArchivoAdjunto();
            entidad.setNombreOriginal(nombreOriginal);
            entidad.setNombreAlmacenado(identificador);
            entidad.setRutaArchivo(ubicacion);
            entidad.setTipoMime(tipoMime);
            entidad.setTamanioBytes(tamanioReal);
            entidad.setSha256(hash);
            entidad.setCargadoPor(persona);
            entidad.setActivo(true);

            try {
                return mapResponse(archivoRepository.save(entidad));
            } catch (RuntimeException ex) {
                limpiarCargaFallida(ubicacion);
                throw ex;
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("ARCHIVO_ALMACENAMIENTO_FALLIDO", "No se pudo almacenar el archivo");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignored) {
                    // La limpieza del temporal no debe ocultar la causa original.
                }
            }
        }
    }

    public ArchivoResponse obtener(Long id) {
        return mapResponse(obtenerEntidad(id));
    }

    public DescargaArchivo descargar(Long id) {
        ArchivoAdjunto archivo = obtenerEntidad(id);
        if (archivo.getRutaArchivo().startsWith("s3://")) {
            return new DescargaArchivo(servicioS3().abrir(archivo.getRutaArchivo()), archivo.getNombreOriginal(),
                    archivo.getTipoMime(), archivo.getTamanioBytes());
        }
        Path ruta = directorioRaiz.resolve(archivo.getRutaArchivo()).normalize();
        verificarRutaSegura(ruta);
        if (!Files.isRegularFile(ruta)) {
            throw new ResourceNotFoundException("ARCHIVO_FISICO_NO_ENCONTRADO", "El contenido solicitado no está disponible");
        }
        try {
            return new DescargaArchivo(new UrlResource(ruta.toUri()), archivo.getNombreOriginal(), archivo.getTipoMime(),
                    archivo.getTamanioBytes());
        } catch (IOException ex) {
            throw new BusinessRuleException("ARCHIVO_LECTURA_FALLIDA", "No se pudo leer el archivo solicitado");
        }
    }

    @Transactional
    public void eliminarLogicamente(Long id) {
        ArchivoAdjunto archivo = obtenerActivoParaVincular(id);
        if (evidenciaRepository.existsByArchivoIdAndActivoTrue(id)) {
            throw new ConflictException("ARCHIVO_EN_EVIDENCIA_PEDAGOGICA",
                    "El archivo está asociado a una evidencia pedagógica activa");
        }
        archivo.setActivo(false);
        archivoRepository.save(archivo);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ArchivoAdjunto obtenerActivoParaVincular(Long id) {
        return archivoRepository.buscarActivoParaVincularOEliminar(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ARCHIVO_NO_ENCONTRADO", "No existe un archivo activo con id " + id));
    }

    public ArchivoAdjunto obtenerEntidad(Long id) {
        return archivoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ARCHIVO_NO_ENCONTRADO", "No existe un archivo activo con id " + id));
    }

    public Set<ArchivoAdjunto> obtenerActivos(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessRuleException("ARCHIVO_ID_INVALIDO", "La lista de archivos contiene un identificador vacío");
        }
        Set<Long> idsUnicos = new LinkedHashSet<>(ids);
        List<ArchivoAdjunto> encontrados = archivoRepository.findAllByIdInAndActivoTrue(idsUnicos);
        if (encontrados.size() != idsUnicos.size()) {
            throw new ResourceNotFoundException(
                    "ARCHIVO_NO_ENCONTRADO", "Uno o más archivos no existen o están inactivos");
        }
        return new LinkedHashSet<>(encontrados);
    }

    public ArchivoResponse mapResponse(ArchivoAdjunto archivo) {
        return new ArchivoResponse(
                archivo.getId(),
                archivo.getNombreOriginal(),
                archivo.getTipoMime(),
                archivo.getTamanioBytes(),
                archivo.getSha256(),
                archivo.getCargadoPor().getId(),
                archivo.getFechaCreacion());
    }

    private void validarArchivoBasico(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessRuleException("ARCHIVO_VACIO", "Debe seleccionar un archivo con contenido");
        }
        if (archivo.getSize() > tamanioMaximoBytes) {
            throw new BusinessRuleException(
                    "ARCHIVO_DEMASIADO_GRANDE", "El archivo excede el límite de " + tamanioMaximoBytes + " bytes");
        }
    }

    private String detectarTipoMime(BufferedInputStream entrada, String declarado, String nombre) throws IOException {
        entrada.mark(64);
        byte[] cabecera = entrada.readNBytes(16);
        entrada.reset();

        String normalizado = declarado == null ? "" : declarado.toLowerCase(Locale.ROOT).trim();
        String detectado = null;
        if (empiezaCon(cabecera, new int[]{0x25, 0x50, 0x44, 0x46})) {
            detectado = "application/pdf";
        } else if (empiezaCon(cabecera, new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            detectado = "image/png";
        } else if (empiezaCon(cabecera, new int[]{0xff, 0xd8, 0xff})) {
            detectado = "image/jpeg";
        } else if (empiezaCon(cabecera, new int[]{0x50, 0x4b})) {
            if (normalizado.startsWith("application/vnd.openxmlformats-officedocument")) {
                detectado = normalizado;
            }
        } else if (empiezaCon(cabecera, new int[]{0xd0, 0xcf, 0x11, 0xe0})) {
            if (Set.of("application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint")
                    .contains(normalizado)) {
                detectado = normalizado;
            }
        }

        if (detectado == null || !TIPOS_PERMITIDOS.contains(detectado)) {
            throw new BusinessRuleException(
                    "ARCHIVO_TIPO_NO_PERMITIDO",
                    "El formato real del archivo " + nombre + " no está permitido");
        }
        return detectado;
    }

    private boolean empiezaCon(byte[] bytes, int[] firma) {
        if (bytes.length < firma.length) {
            return false;
        }
        for (int indice = 0; indice < firma.length; indice++) {
            if ((bytes[indice] & 0xff) != firma[indice]) {
                return false;
            }
        }
        return true;
    }

    private String limpiarNombre(String nombre) {
        String seguro = nombre == null ? "archivo" : nombre.replace('\\', '/');
        seguro = seguro.substring(seguro.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (seguro.isBlank() || ".".equals(seguro) || "..".equals(seguro)) {
            seguro = "archivo";
        }
        return seguro.length() > 255 ? seguro.substring(0, 255) : seguro;
    }

    private void verificarRutaSegura(Path ruta) {
        if (!ruta.startsWith(directorioRaiz)) {
            throw new BusinessRuleException("ARCHIVO_RUTA_INVALIDA", "La ruta de almacenamiento no es válida");
        }
    }

    private void moverAtomico(Path origen, Path destino) throws IOException {
        try {
            Files.move(origen, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ArchivoS3Service servicioS3() {
        ArchivoS3Service servicio = s3.getIfAvailable();
        if (servicio == null) throw new BusinessRuleException("ARCHIVO_S3_NO_CONFIGURADO", "El almacenamiento S3 no está configurado en este entorno.");
        return servicio;
    }

    private void limpiarCargaFallida(String ubicacion) {
        try {
            if (ubicacion.startsWith("s3://")) servicioS3().eliminarCargaFallida(ubicacion);
            else {
                Path ruta = directorioRaiz.resolve(ubicacion).normalize();
                verificarRutaSegura(ruta);
                Files.deleteIfExists(ruta);
            }
        } catch (RuntimeException | IOException ex) {
            org.slf4j.LoggerFactory.getLogger(ArchivoStorageService.class)
                    .warn("No se pudo limpiar una carga fallida; revisar el almacenamiento de adjuntos.");
        }
    }

    public record DescargaArchivo(Resource recurso, String nombreOriginal, String tipoMime, long tamanioBytes) {
    }
}
