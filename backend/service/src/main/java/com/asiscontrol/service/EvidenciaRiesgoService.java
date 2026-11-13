package com.asiscontrol.service;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Proyeccion de la auditoria existente: no agrega entidades ni modifica el esquema. */
@Service
@Transactional(readOnly = true)
public class EvidenciaRiesgoService {
    public static final String ACCION = "EVALUAR_CRITERIO_RIESGO";
    private final EntityManager em;
    private final AuditoriaService auditoria;
    private final ObjectMapper json = new ObjectMapper();

    public EvidenciaRiesgoService(EntityManager em, AuditoriaService auditoria) {
        this.em = em; this.auditoria = auditoria;
    }

    public record Instantanea(int version, Long idAlumno, Long idPeriodo, String periodo,
            Long idSeccion, int grado, String seccion, RiesgoDto.ResultadoRegla evaluacion) {}

    public int minimoDatos(Long idCriterio) {
        var filas = registros("CriterioRiesgo", idCriterio.toString(), "CONFIGURAR_MINIMO_RIESGO");
        return filas.isEmpty() ? 1 : Integer.parseInt(filas.getLast().getNuevoValor());
    }

    @Transactional
    public void configurarMinimo(Long id, Integer minimo) {
        if (minimo == null) return;
        if (minimo < 1) throw new IllegalArgumentException("El minimo de datos debe ser positivo");
        auditoria.registrar("CONFIGURAR_MINIMO_RIESGO", ModuloAuditoria.RIESGO, "CriterioRiesgo",
                id.toString(), ResultadoAuditoria.EXITOSO, "Cantidad minima de registros",
                Integer.toString(minimoDatos(id)), minimo.toString());
    }

    @Transactional
    public void guardar(Instantanea instantanea) {
        String contenido;
        try { contenido = json.writeValueAsString(instantanea); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo guardar la evidencia", e); }
        // TEXT de MySQL admite 65535 bytes. Fragmentar sin truncar ni cambiar la entidad.
        if (contenido.length() > 12000) {
            String clave = UUID.randomUUID().toString();
            for (int inicio = 0; inicio < contenido.length();) {
                int fin = Math.min(inicio + 12000, contenido.length());
                if (fin < contenido.length() && Character.isHighSurrogate(contenido.charAt(fin - 1))) fin--;
                auditoria.registrar("RIESGO_EVIDENCIA_PARTE", ModuloAuditoria.RIESGO, "AlertaRiesgo",
                        clave, ResultadoAuditoria.EXITOSO, "Fragmento de evidencia", null, contenido.substring(inicio, fin));
                inicio = fin;
            }
            contenido = "RIESGO_PARTES:" + clave;
        }
        Long idAlerta = instantanea.evaluacion().idAlerta();
        auditoria.registrar(ACCION, ModuloAuditoria.RIESGO, idAlerta == null ? "Alumno" : "AlertaRiesgo",
                (idAlerta == null ? instantanea.idAlumno() : idAlerta).toString(), ResultadoAuditoria.EXITOSO,
                instantanea.evaluacion().resultado().name(), null, contenido);
    }

    public List<RegistroAuditoria> registrosAlerta(Long id) {
        return registros("AlertaRiesgo", id.toString(), ACCION);
    }

    public Instantanea ultima(Long id) {
        var filas = registrosAlerta(id);
        return filas.isEmpty() ? null : leer(filas.getLast());
    }

    public Instantanea ultimaDeteccion(Long id) {
        var filas = registrosAlerta(id);
        for (int i = filas.size() - 1; i >= 0; i--) {
            if ("EN_RIESGO".equals(filas.get(i).getMotivo())) return leer(filas.get(i));
        }
        return null;
    }

    public Instantanea leer(RegistroAuditoria registro) {
        String contenido = registro.getNuevoValor();
        if (contenido.startsWith("RIESGO_PARTES:")) {
            StringBuilder partes = new StringBuilder();
            registros("AlertaRiesgo", contenido.substring("RIESGO_PARTES:".length()), "RIESGO_EVIDENCIA_PARTE")
                    .forEach(r -> partes.append(r.getNuevoValor()));
            contenido = partes.toString();
        }
        try {
            Instantanea resultado = json.readValue(contenido, Instantanea.class);
            if (resultado.version() != 1) throw new IllegalStateException("Version de evidencia no soportada");
            return resultado;
        } catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo leer la evidencia", e); }
    }

    private List<RegistroAuditoria> registros(String entidad, String id, String accion) {
        return em.createQuery("""
                from RegistroAuditoria r where r.modulo = :modulo and r.entidad = :entidad
                and r.registroId = :id and r.accion = :accion order by r.id
                """, RegistroAuditoria.class).setParameter("modulo", ModuloAuditoria.RIESGO)
                .setParameter("entidad", entidad).setParameter("id", id).setParameter("accion", accion).getResultList();
    }
}
