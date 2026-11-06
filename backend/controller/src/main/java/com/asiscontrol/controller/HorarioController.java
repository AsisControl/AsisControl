package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioResponse;
import com.asiscontrol.dto.academico.HistorialHorarioDtos.FiltroHistorialHorario;
import com.asiscontrol.dto.academico.HistorialHorarioDtos.HistorialHorarioResponse;
import com.asiscontrol.service.HistorialHorarioService;
import com.asiscontrol.service.HorarioService;
import com.asiscontrol.service.ModuloAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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

import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/horarios")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class HorarioController {

    private final HorarioService horarioService;
    private final HistorialHorarioService historialService;
    private final ModuloAccessGuard accessGuard;

    public HorarioController(
            HorarioService horarioService, HistorialHorarioService historialService,
            ModuloAccessGuard accessGuard) {
        this.horarioService = horarioService;
        this.historialService = historialService;
        this.accessGuard = accessGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR') and !hasRole('ALUMNO')")
    public HorarioResponse crear(@Valid @RequestBody CrearHorarioRequest request) {
        return horarioService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR') and !hasRole('ALUMNO')")
    public HorarioResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarHorarioRequest request) {
        return horarioService.actualizar(id, request);
    }

    /** US-27: solo lectura; muestra quien hizo cada cambio, por eso exige tambien leer auditoria. */
    @GetMapping("/historial")
    @PreAuthorize("hasAuthority('ACADEMICO_LEER') and hasAuthority('AUDITORIA_LEER') and !hasRole('ALUMNO')")
    public PageResponse<HistorialHorarioResponse> historial(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer anioAcademico,
            @RequestParam(required = false) @Positive Long seccionId,
            @RequestParam(required = false) @Positive Long docenteId,
            @RequestParam(required = false) @Positive Long aulaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return historialService.listar(
                new FiltroHistorialHorario(anioAcademico, seccionId, docenteId, aulaId, desde, hasta),
                page,
                size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_LEER') and !hasRole('ALUMNO')")
    public HorarioResponse obtener(@PathVariable Long id) {
        return horarioService.obtener(id);
    }

    @GetMapping("/mio")
    @PreAuthorize("hasRole('ALUMNO') and hasAuthority('ACADEMICO_LEER')")
    public java.util.List<HorarioResponse> mio(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer anioAcademico) {
        return horarioService.listarDelAlumno(
                accessGuard.restriccionAlumnoPersonaId(), anioAcademico);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACADEMICO_LEER') and !hasRole('ALUMNO')")
    public Page<HorarioResponse> listar(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer anioAcademico,
            @RequestParam(required = false) @Positive Long seccionId,
            @RequestParam(required = false) @Positive Long docenteId,
            @RequestParam(required = false) @Positive Long aulaId,
            @RequestParam(required = false) @Positive Long cursoId,
            @PageableDefault(size = 20, sort = {"diaSemana", "horaInicio"}) Pageable pageable) {
        return horarioService.listar(
                anioAcademico, seccionId, docenteId, aulaId, cursoId, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR') and !hasRole('ALUMNO')")
    public void eliminar(@PathVariable Long id) {
        horarioService.eliminar(id);
    }
}
