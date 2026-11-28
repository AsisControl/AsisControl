package com.asiscontrol.controller;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.service.CalificacionService;
import com.asiscontrol.service.ModuloAccessGuard;
import com.asiscontrol.service.ReporteAvanceService;
import com.asiscontrol.service.GeneradorReporteAvance;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.HashSet;

@RestController
@RequestMapping("/api/calificaciones")
@PreAuthorize("hasAuthority('CALIFICACIONES_LEER')")
public class CalificacionController {

    private final CalificacionService calificacionService;
    private final ModuloAccessGuard accessGuard;
    private final ReporteAvanceService reporteAvanceService;
    private final GeneradorReporteAvance generadorReporteAvance;

    public CalificacionController(
            CalificacionService calificacionService,
            ModuloAccessGuard accessGuard,
            ReporteAvanceService reporteAvanceService,
            GeneradorReporteAvance generadorReporteAvance
    ) {
        this.calificacionService = calificacionService;
        this.accessGuard = accessGuard;
        this.reporteAvanceService = reporteAvanceService;
        this.generadorReporteAvance = generadorReporteAvance;
    }

    @GetMapping("/informes-disponibles")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE','ALUMNO','APODERADO')")
    public List<com.asiscontrol.dto.InformeDisponibleDto> informesDisponibles() {
        return reporteAvanceService.disponibles();
    }

    @GetMapping("/avance")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE','ALUMNO','APODERADO')")
    public com.asiscontrol.dto.ReporteAvanceDto consultarAvance(@RequestParam Long idAlumno,
            @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico) {
        var reporte = reporteAvanceService.preparar(idAlumno, idAsignacionCurso, idPeriodoAcademico);
        return consultaFamiliar() ? reporte.sinPromediosNumericos() : reporte;
    }

    @PostMapping("/informes/generar")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR') and hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public ReporteAvanceService.ResultadoPeriodo generarInformes(
            @Valid @RequestBody com.asiscontrol.dto.EvaluacionDto.PublicarPeriodoRequest request) {
        return reporteAvanceService.generarPeriodo(request.idAsignacionCurso(), request.idPeriodoAcademico());
    }

    @PostMapping("/informes/publicar")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR') and hasRole('DOCENTE')")
    public ReporteAvanceService.ResultadoPeriodo publicarInformes(
            @Valid @RequestBody com.asiscontrol.dto.EvaluacionDto.PublicarPeriodoRequest request) {
        return reporteAvanceService.publicarPeriodo(request.idAsignacionCurso(), request.idPeriodoAcademico());
    }

    @GetMapping("/informes/academicos")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_LEER') and hasAuthority('CALIFICACIONES_LEER')")
    public List<ReporteAvanceService.LibretaAcademica> libretasAcademicas(@RequestParam int anio,
            @RequestParam Long periodo, @RequestParam int grado, @RequestParam Long seccion, @RequestParam Long curso) {
        return reporteAvanceService.listarLibretas(anio, periodo, grado, seccion, curso);
    }

    @GetMapping("/informes/academicos/resumen")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_LEER') and hasAuthority('CALIFICACIONES_LEER')")
    public ReporteAvanceService.ResumenAcademico resumenAcademico(@RequestParam int anio,
            @RequestParam Long periodo, @RequestParam int grado, @RequestParam Long seccion, @RequestParam Long curso) {
        return reporteAvanceService.resumenAcademico(anio, periodo, grado, seccion, curso);
    }

    @GetMapping("/informes/academicos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_LEER') and hasAuthority('CALIFICACIONES_LEER')")
    public com.asiscontrol.dto.ReporteAvanceDto detalleLibreta(@PathVariable Long id) {
        return reporteAvanceService.detalleLibreta(id);
    }

    @GetMapping("/informes/estado")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public ReporteAvanceService.EstadoPeriodo estadoInformes(@RequestParam Long idAsignacionCurso,
            @RequestParam Long idPeriodoAcademico) {
        return reporteAvanceService.estadoPeriodo(idAsignacionCurso, idPeriodoAcademico);
    }

    @GetMapping("/informes/borrador")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public com.asiscontrol.dto.ReporteAvanceDto consultarBorrador(@RequestParam Long idAlumno,
            @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico) {
        return reporteAvanceService.prepararBorrador(idAlumno, idAsignacionCurso, idPeriodoAcademico);
    }

    @PutMapping("/informes/borrador/revision")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR') and hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public com.asiscontrol.dto.ReporteAvanceDto revisarInforme(
            @RequestParam Long idAlumno, @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico,
            @Valid @RequestBody ReporteAvanceService.RevisionRequest request) {
        return reporteAvanceService.revisarBorrador(idAlumno, idAsignacionCurso, idPeriodoAcademico, request);
    }

    @GetMapping("/informes/parcial/evaluaciones")
    @PreAuthorize("hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public List<ReporteAvanceService.EvaluacionDtoParcial> evaluacionesParciales(
            @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico) {
        return reporteAvanceService.evaluacionesParciales(idAsignacionCurso, idPeriodoAcademico);
    }

    @GetMapping("/informes/parcial")
    @PreAuthorize("hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public com.asiscontrol.dto.ReporteAvanceDto informeParcial(
            @RequestParam Long idAlumno, @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico,
            @RequestParam List<Long> idEvaluacion, @RequestParam(required = false) List<Long> idCompetencia) {
        return reporteAvanceService.prepararParcial(idAlumno, idAsignacionCurso, idPeriodoAcademico, idEvaluacion, idCompetencia).sinPromediosNumericos();
    }

    @GetMapping(value = "/informes/parcial/pdf", produces = "application/pdf")
    @PreAuthorize("hasAnyRole('DOCENTE','ADMINISTRADOR')")
    public ResponseEntity<byte[]> pdfParcial(
            @RequestParam Long idAlumno, @RequestParam Long idAsignacionCurso, @RequestParam Long idPeriodoAcademico,
            @RequestParam List<Long> idEvaluacion, @RequestParam(required = false) List<Long> idCompetencia) {
        var datos = reporteAvanceService.prepararParcial(idAlumno, idAsignacionCurso, idPeriodoAcademico, idEvaluacion, idCompetencia);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + nombrePdf(datos, true) + "\"")
                .cacheControl(CacheControl.noStore())
                .body(generadorReporteAvance.generarParcialVigente(datos));
    }

    /** US-20: el PDF utiliza exclusivamente los detalles de la ultima version publicada. */
    @GetMapping(value = "/reporte-avance", produces = "application/pdf")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DOCENTE','ALUMNO','APODERADO')")
    public ResponseEntity<byte[]> descargarReporteAvance(
            @RequestParam Long idAlumno,
            @RequestParam Long idAsignacionCurso,
            @RequestParam Long idPeriodoAcademico,
            @RequestParam(required = false) List<Long> idEvaluacion,
            @RequestParam(required = false) List<Long> idCompetencia) {
        var reporte = reporteAvanceService.preparar(idAlumno, idAsignacionCurso, idPeriodoAcademico);
        var datos = reporte.sinPromediosNumericos();
        var seleccionadas = idEvaluacion == null ? null : new HashSet<>(idEvaluacion);
        var competencias = idCompetencia == null ? null : new HashSet<>(idCompetencia);
        if (competencias != null && (seleccionadas == null || competencias.isEmpty())) {
            throw new BusinessRuleException("SELECCION_PDF_INVALIDA", "Selecciona evaluaciones y competencias de esta libreta.");
        }
        if (seleccionadas != null) {
            var filas = datos.filas().stream().filter(fila -> seleccionadas.contains(fila.idEvaluacion()))
                    .filter(fila -> competencias == null || competencias.contains(fila.idCompetencia())).toList();
            var disponibles = filas.stream().map(com.asiscontrol.dto.ReporteAvanceDto.Fila::idEvaluacion)
                    .collect(java.util.stream.Collectors.toSet());
            if (seleccionadas.isEmpty() || !disponibles.containsAll(seleccionadas) || (competencias != null && !filas.stream()
                    .map(com.asiscontrol.dto.ReporteAvanceDto.Fila::idCompetencia).collect(java.util.stream.Collectors.toSet()).containsAll(competencias))) {
                throw new BusinessRuleException("SELECCION_PDF_INVALIDA", "Selecciona al menos una evaluación de esta libreta para descargar el reporte.");
            }
        }
        byte[] pdf = seleccionadas == null ? generadorReporteAvance.generar(datos) : generadorReporteAvance.generar(datos, seleccionadas, competencias);
        String nombre = nombrePdf(datos, seleccionadas != null);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + nombre + "\"")
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }

    private static String nombrePdf(com.asiscontrol.dto.ReporteAvanceDto datos, boolean parcial) {
        var version = java.util.regex.Pattern.compile(" \\| Version ([0-9]+) \\| PUBLICADA$").matcher(datos.periodo());
        boolean publicada = version.find();
        String periodo = publicada ? datos.periodo().substring(0, version.start()) : datos.periodo();
        String base = String.join("_", datos.alumno(), datos.curso(), periodo);
        String limpio = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return (parcial ? "Informe_parcial_" : "Informe_completo_")
                + limpio.substring(0, Math.min(limpio.length(), 160))
                + (publicada && !"1".equals(version.group(1)) ? "_V" + version.group(1) : "") + ".pdf";
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DIRECTOR','DOCENTE')")
    public Page<CalificacionDto.Response> listar(
            @RequestParam(required = false) Long idEvaluacion,
            @RequestParam(required = false) Long idAlumno,
            @RequestParam(required = false) Long idCompetencia,
            @PageableDefault(size = 20, sort = "fechaRegistro", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return calificacionService.listar(
                idEvaluacion,
                idAlumno,
                idCompetencia,
                accessGuard.restriccionDocentePersonaId(),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederCalificacion(#id)")
    public CalificacionDto.Response obtener(@PathVariable Long id) {
        return calificacionService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public CalificacionDto.Response registrar(@Valid @RequestBody CalificacionDto.Request request) {
        return calificacionService.registrar(request);
    }

    @PutMapping("/lote")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public List<CalificacionDto.Response> registrarLote(
            @Valid @RequestBody CalificacionDto.CargaLoteRequest request
    ) {
        return calificacionService.registrarLote(request);
    }

    /** US-50: corrige niveles publicados con un motivo comun. */
    @PostMapping("/correcciones")
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public java.util.List<CalificacionDto.Response> corregir(
            @Valid @RequestBody CalificacionDto.CorreccionRequest request) {
        return calificacionService.corregir(request);
    }

    /** US-50: trazabilidad de una calificacion; alumnos y apoderados no la consultan. */
    @GetMapping("/{id}/correcciones")
    @PreAuthorize("hasAuthority('CALIFICACIONES_LEER') and hasAnyRole('ADMINISTRADOR','DIRECTOR','DOCENTE')"
            + " and @moduloAccessGuard.puedeAccederCalificacion(#id)")
    public java.util.List<CalificacionDto.CorreccionResponse> correcciones(@PathVariable Long id) {
        return calificacionService.correcciones(id);
    }

    @GetMapping("/consolidados")
    @PreAuthorize("@moduloAccessGuard.puedeAccederAlumno(#idAlumno)")
    public List<CalificacionDto.ConsolidadoResponse> obtenerConsolidado(
            @RequestParam Long idAlumno,
            @RequestParam(required = false) Long idAsignacionCurso,
            @RequestParam(required = false) Long idPeriodoAcademico
    ) {
        if (idPeriodoAcademico != null && idAsignacionCurso == null) {
            throw new BusinessRuleException("ASIGNACION_REQUERIDA", "Selecciona el curso y sección del período.");
        }
        var resultados = idPeriodoAcademico == null ? calificacionService.obtenerConsolidado(idAlumno, idAsignacionCurso)
                : consultaFamiliar() ? reporteAvanceService.preparar(idAlumno, idAsignacionCurso, idPeriodoAcademico).consolidado()
                : reporteAvanceService.consultarConsolidado(idAlumno, idAsignacionCurso, idPeriodoAcademico);
        return consultaFamiliar() ? resultados.stream().map(CalificacionDto.ConsolidadoResponse::sinPromedioNumerico).toList()
                : resultados;
    }

    private boolean consultaFamiliar() {
        return accessGuard.restriccionAlumnoPersonaId() != null || accessGuard.restriccionApoderadoPersonaId() != null;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CALIFICACIONES_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        calificacionService.eliminar(id);
    }
}
