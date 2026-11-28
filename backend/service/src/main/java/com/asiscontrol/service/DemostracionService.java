package com.asiscontrol.service;

import com.asiscontrol.dto.DemostracionDtos.*;
import com.asiscontrol.entity.CargaDemostracion;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.RolRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/** Ejecutor de archivos externos: no contiene personas, años, importes ni escenarios de ejemplo. */
@Service
@ConditionalOnProperty(name = "app.demostracion.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class DemostracionService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DemostracionService.class);
    private static final long MAX_BYTES = 1024 * 1024;
    private final EntityManager em;
    private final RolRepository roles;
    private final AuditoriaService auditoria;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;
    private final String manifestPath;
    public DemostracionService(EntityManager em, RolRepository roles, AuditoriaService auditoria,
            NamedParameterJdbcTemplate jdbc, Clock reloj,
            @Value("${app.demostracion.sql-manifest:}") String manifestPath) {
        this.em = em; this.roles = roles; this.auditoria = auditoria;
        this.jdbc = jdbc; this.reloj = reloj; this.manifestPath = manifestPath;
    }
    @Transactional(readOnly = true)
    public Estado estado() {
        String paquete = manifest().getProperty("paquete");
        return new Estado(paquete, cargas(paquete).stream().map(c -> respuesta(c, true)).toList());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado preparar(Etapa etapa, MultipartFile archivo) {
        if (etapa == null || archivo == null || archivo.isEmpty() || archivo.getSize() > MAX_BYTES)
            throw conflicto("Seleccione un archivo SQL de hasta 1 MB y su etapa");
        var manifest = manifest();
        String paquete = manifest.getProperty("paquete");
        byte[] bytes;
        try { bytes = archivo.getBytes(); }
        catch (IOException e) { throw conflicto("No se pudo leer el archivo SQL"); }
        String hash = sha256(bytes);
        if (!Objects.equals(manifest.getProperty(etapa.name() + ".archivo"), archivo.getOriginalFilename())
                || !hash.equalsIgnoreCase(manifest.getProperty(etapa.name() + ".sha256", "")))
            throw conflicto("El archivo SQL no coincide con el paquete autorizado. Seleccione el archivo original o actualice el manifiesto del servidor si cambió el paquete");
        var sentencias = SentenciasDemostracion.separar(new String(bytes, StandardCharsets.UTF_8));
        roles.bloquearAdministrador().orElseThrow(() -> conflicto("Falta el rol administrador"));
        var anteriores = cargas(paquete);
        var existente = anteriores.stream().filter(c -> c.getEtapa().equals(etapa.name())).findFirst();
        if (existente.isPresent()) {
            if (!hash.equals(existente.get().getHashArchivo()))
                throw conflicto("Esta etapa ya se ejecutó con otro archivo. No se sobrescribirán sus datos");
            return respuesta(existente.get(), true);
        }
        if (etapa.ordinal() > 0 && anteriores.stream().noneMatch(c -> c.getEtapa().equals(Etapa.values()[etapa.ordinal() - 1].name())))
            throw conflicto("Ejecute primero la etapa anterior y sus Excel correspondientes");
        var carga = new CargaDemostracion();
        carga.setPaquete(paquete); carga.setEtapa(etapa.name()); carga.setHashArchivo(hash);
        carga.setFechaReferencia(LocalDate.now(reloj)); carga.setFechaEjecucion(reloj.instant());
        var indicaciones = new ArrayList<String>();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> parametros = Map.of("fecha_referencia", java.sql.Date.valueOf(carga.getFechaReferencia()),
                "instante_carga", Timestamp.from(reloj.instant()), "actor", auth == null ? "SISTEMA" : auth.getName());
        em.flush();
        try {
            for (var sql : sentencias) {
                if (sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
                    jdbc.query(sql, parametros, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                        var columnas = new HashSet<String>();
                        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                            columnas.add(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT));
                        if (columnas.contains("validacion")) {
                            if (!rs.getBoolean("validacion")) throw conflicto(rs.getString("mensaje"));
                        } else if (columnas.containsAll(Set.of("clave", "id_recurso"))) {
                            String clave = rs.getString("clave"); long id = rs.getLong("id_recurso");
                            if (clave == null || clave.isBlank() || clave.length() > 100 || rs.wasNull())
                                throw conflicto("El SQL devolvió una referencia incompleta");
                            carga.getRecursos().put(clave, id);
                        } else if (columnas.contains("indicacion")) {
                            String texto = rs.getString("indicacion");
                            if (texto != null && !texto.isBlank()) indicaciones.add(texto);
                        } else throw conflicto("Las consultas del SQL deben devolver validaciones, referencias o indicaciones");
                    });
                } else jdbc.update(sql, parametros);
            }
        } catch (DataAccessException e) {
            LOG.warn("Fallo del SQL externo {}: {}", archivo.getOriginalFilename(), e.getMostSpecificCause().getMessage());
            throw conflicto("No se pudo aplicar el SQL. Se revirtió esta etapa; revise sus dependencias y el esquema de la base");
        }
        em.clear(); carga.setIndicaciones(String.join("\n", indicaciones)); em.persist(carga); em.flush();
        auditoria.registrar("IMPORTAR_SQL_DEMOSTRACION", ModuloAuditoria.CONFIGURACION_ACADEMICA,
                "CargaDemostracion", carga.getId().toString(), ResultadoAuditoria.EXITOSO,
                "Archivo externo: " + archivo.getOriginalFilename(), null, "sha256=" + hash);
        return respuesta(carga, false);
    }
    private Properties manifest() {
        if (manifestPath.isBlank()) throw conflicto("Configure DEMO_SQL_MANIFEST con la ruta del manifiesto externo del paquete SQL");
        var props = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(manifestPath), StandardCharsets.UTF_8)) { props.load(reader); }
        catch (IOException | RuntimeException e) { throw conflicto("No se pudo leer el manifiesto externo de los SQL"); }
        String paquete = props.getProperty("paquete", "");
        if (paquete.isBlank() || paquete.length() > 50) throw conflicto("El manifiesto debe indicar un paquete válido");
        return props;
    }
    private List<CargaDemostracion> cargas(String paquete) {
        return em.createQuery("select c from CargaDemostracion c where c.paquete = :paquete order by c.id", CargaDemostracion.class)
                .setParameter("paquete", paquete).getResultList();
    }
    private Resultado respuesta(CargaDemostracion carga, boolean repetida) {
        return new Resultado(carga.getPaquete(), Etapa.valueOf(carga.getEtapa()), repetida, carga.getFechaReferencia(),
                Map.copyOf(carga.getRecursos()), carga.getIndicaciones() == null ? List.of() : carga.getIndicaciones().lines().toList());
    }
    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static ConflictException conflicto(String mensaje) { return new ConflictException("DEMO_SQL_INVALIDO", mensaje); }
}
