package com.asiscontrol.service;

import com.asiscontrol.dto.SustentoInformeDto;
import com.asiscontrol.entity.DetalleInformeProgreso;
import com.asiscontrol.entity.EvidenciaPedagogica;
import com.asiscontrol.entity.enums.EstadoInformeProgreso;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.EvidenciaPedagogicaRepository;
import com.asiscontrol.repository.MatriculaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
public class SustentoInformeService {
    private final EntityManager em;
    private final ModuloAccessGuard acceso;
    private final MatriculaRepository matriculas;
    private final EvidenciaPedagogicaRepository evidencias;
    private final ArchivoStorageService archivos;
    private final ReporteAvanceService informes;

    public SustentoInformeService(EntityManager em, ModuloAccessGuard acceso,
            MatriculaRepository matriculas, EvidenciaPedagogicaRepository evidencias, ArchivoStorageService archivos,
            ReporteAvanceService informes) {
        this.em = em;
        this.acceso = acceso;
        this.matriculas = matriculas;
        this.evidencias = evidencias;
        this.archivos = archivos;
        this.informes = informes;
    }

    public record FilaParcial(Long alumno, Long curso, Long periodo, Long evaluacion, Long competencia) {}

    public java.util.List<SustentoInformeDto.Evidencia> listarParcial(FilaParcial fila) {
        String ruta = "/api/calificaciones/informes/parcial/evidencias/";
        String contexto = "?idAlumno=" + fila.alumno() + "&idAsignacionCurso=" + fila.curso()
                + "&idPeriodoAcademico=" + fila.periodo() + "&idEvaluacion=" + fila.evaluacion()
                + "&idCompetencia=" + fila.competencia();
        return buscarParcial(fila, null).stream()
                .map(e -> respuesta(e, ruta + e.getId() + "/archivo" + contexto)).toList();
    }

    public ArchivoStorageService.DescargaArchivo descargarParcial(FilaParcial fila, Long evidenciaId) {
        var evidencia = buscarParcial(fila, evidenciaId).stream().findFirst().orElseThrow(() ->
                new ResourceNotFoundException("SUSTENTO_NO_ENCONTRADO", "La evidencia no está disponible para esta fila"));
        return descargarArchivo(evidencia);
    }

    private java.util.List<EvidenciaPedagogica> buscarParcial(FilaParcial fila, Long evidenciaId) {
        informes.validarFilaParcial(fila.alumno(), fila.curso(), fila.periodo(), fila.evaluacion(), fila.competencia());
        return evidencias.buscarSustento(fila.curso(), fila.alumno(), fila.competencia(), fila.periodo(), fila.evaluacion(), evidenciaId);
    }

    public SustentoInformeDto listar(Long informeId, Long detalleId) {
        var d = detalleAutorizado(informeId, detalleId);
        var i = d.getInforme();
        var lista = buscar(d, null).stream().map(e -> respuesta(d, e)).toList();
        return new SustentoInformeDto(i.getId(), d.getId(), i.getNumeroVersion(), i.getAlumno().getId(),
                i.getAsignacionCurso().getId(), i.getPeriodoAcademico().getId(), d.getEvaluacion().getId(),
                d.getCompetencia().getId(), d.getCalificacion() == null ? null : d.getCalificacion().getId(), lista);
    }

    public SustentoInformeDto.Evidencia obtener(Long informeId, Long detalleId, Long evidenciaId) {
        var d = detalleAutorizado(informeId, detalleId);
        return respuesta(d, evidenciaAutorizada(d, evidenciaId));
    }

    public ArchivoStorageService.DescargaArchivo descargar(Long informeId, Long detalleId, Long evidenciaId) {
        var d = detalleAutorizado(informeId, detalleId);
        return descargarArchivo(evidenciaAutorizada(d, evidenciaId));
    }

    private ArchivoStorageService.DescargaArchivo descargarArchivo(EvidenciaPedagogica e) {
        if (e.getArchivo() == null || !e.getArchivo().isActivo()) {
            throw new ResourceNotFoundException("SUSTENTO_SIN_ARCHIVO", "La evidencia no tiene un adjunto disponible");
        }
        // El ID del archivo se obtiene de la evidencia autorizada, nunca del cliente.
        return archivos.descargar(e.getArchivo().getId());
    }

    private DetalleInformeProgreso detalleAutorizado(Long informeId, Long detalleId) {
        var d = em.find(DetalleInformeProgreso.class, detalleId);
        if (d == null || !d.isActivo() || !d.getInforme().isActivo() || !d.getInforme().getId().equals(informeId)) {
            throw new ResourceNotFoundException("FILA_INFORME_NO_ENCONTRADA", "No existe esa fila en el informe indicado");
        }
        var i = d.getInforme();
        var a = i.getAsignacionCurso();
        var p = i.getPeriodoAcademico();
        acceso.verificarConsultaInforme(a, i.getAlumno().getId());
        if (i.getEstado() != EstadoInformeProgreso.PUBLICADA) acceso.verificarAsignacionDocente(a);
        if (!a.isActivo() || !a.getDocente().isActivo() || !i.getAlumno().isActivo()
                || !p.isActivo() || !p.getAnioAcademico().isActivo()
                || p.getAnioAcademico().getAnio() != a.getSeccion().getAnioAcademico()
                || !matriculas.estuvoEnSeccion(i.getAlumno().getId(), a.getSeccion().getId())) {
            throw new AccessDeniedException("El contexto academico del informe ya no permite consultar su sustento");
        }
        return d;
    }

    private java.util.List<EvidenciaPedagogica> buscar(DetalleInformeProgreso d, Long evidenciaId) {
        var i = d.getInforme();
        return evidencias.buscarSustento(i.getAsignacionCurso().getId(), i.getAlumno().getId(),
                d.getCompetencia().getId(), i.getPeriodoAcademico().getId(), d.getEvaluacion().getId(), evidenciaId);
    }

    private EvidenciaPedagogica evidenciaAutorizada(DetalleInformeProgreso d, Long evidenciaId) {
        return buscar(d, evidenciaId).stream().findFirst().orElseThrow(() ->
                new ResourceNotFoundException("SUSTENTO_NO_ENCONTRADO", "La evidencia no esta disponible para esta fila"));
    }

    private SustentoInformeDto.Evidencia respuesta(DetalleInformeProgreso d, EvidenciaPedagogica e) {
        return respuesta(e, "/api/calificaciones/informes/" + d.getInforme().getId() + "/filas/" + d.getId()
                + "/evidencias/" + e.getId() + "/archivo");
    }

    private SustentoInformeDto.Evidencia respuesta(EvidenciaPedagogica e, String url) {
        var a = e.getArchivo();
        var adjunto = a == null || !a.isActivo() ? null : new SustentoInformeDto.Adjunto(a.getId(),
                a.getNombreOriginal(), a.getTipoMime(), a.getTamanioBytes(),
                url);
        return new SustentoInformeDto.Evidencia(e.getId(), e.getDescripcion(), adjunto);
    }
}
