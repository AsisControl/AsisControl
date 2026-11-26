package com.asiscontrol.controller;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.service.ImportacionAsignacionService;
import com.asiscontrol.service.ImportacionHorarioService;
import com.asiscontrol.service.ImportacionPlanEstudiosService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OBS-05 · ASISCTRL-230/231: carga masiva del plan de estudios, las asignaciones docentes y los
 * horarios. Con {@code confirmar=false} solo revisa el archivo; con {@code confirmar=true} registra
 * las filas válidas. Cada endpoint exige el mismo permiso que el registro manual.
 */
@RestController
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class CargaMasivaAcademicaController {

    private final ImportacionPlanEstudiosService planes;
    private final ImportacionAsignacionService asignaciones;
    private final ImportacionHorarioService horarios;

    public CargaMasivaAcademicaController(
            ImportacionPlanEstudiosService planes,
            ImportacionAsignacionService asignaciones,
            ImportacionHorarioService horarios) {
        this.planes = planes;
        this.asignaciones = asignaciones;
        this.horarios = horarios;
    }

    @PostMapping(value = "/api/planes-estudios/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public CargaMasivaDto.Resultado importarPlan(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return planes.importar(archivo, confirmar);
    }

    @GetMapping("/api/planes-estudios/importaciones/plantilla")
    @PreAuthorize("hasRole('ADMINISTRADOR') and hasAuthority('ACADEMICO_ESCRIBIR')")
    public ResponseEntity<byte[]> plantillaPlan() {
        return ExcelDescarga.adjunto("plantilla_plan_estudios.xlsx", planes.plantilla());
    }

    @PostMapping(value = "/api/asignaciones-cursos/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public CargaMasivaDto.Resultado importarAsignaciones(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return asignaciones.importar(archivo, confirmar);
    }

    @GetMapping("/api/asignaciones-cursos/importaciones/plantilla")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public ResponseEntity<byte[]> plantillaAsignaciones() {
        return ExcelDescarga.adjunto("plantilla_asignaciones_docentes.xlsx", asignaciones.plantilla());
    }

    @PostMapping(value = "/api/horarios/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR') and !hasRole('ALUMNO')")
    public CargaMasivaDto.Resultado importarHorarios(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return horarios.importar(archivo, confirmar);
    }

    @GetMapping("/api/horarios/importaciones/plantilla")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR') and !hasRole('ALUMNO')")
    public ResponseEntity<byte[]> plantillaHorarios() {
        return ExcelDescarga.adjunto("plantilla_horarios.xlsx", horarios.plantilla());
    }
}
