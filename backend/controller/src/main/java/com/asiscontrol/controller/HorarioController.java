package com.asiscontrol.controller;

import com.asiscontrol.dto.academico.AcademicoDtos.ActualizarHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearHorarioRequest;
import com.asiscontrol.dto.academico.AcademicoDtos.HorarioResponse;
import com.asiscontrol.service.HorarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

@RestController
@Validated
@RequestMapping("/api/horarios")
@PreAuthorize("hasAuthority('ACADEMICO_LEER')")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public HorarioResponse crear(@Valid @RequestBody CrearHorarioRequest request) {
        return horarioService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public HorarioResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarHorarioRequest request) {
        return horarioService.actualizar(id, request);
    }

    @GetMapping("/{id}")
    public HorarioResponse obtener(@PathVariable Long id) {
        return horarioService.obtener(id);
    }

    @GetMapping
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
    @PreAuthorize("hasAuthority('ACADEMICO_ESCRIBIR')")
    public void eliminar(@PathVariable Long id) {
        horarioService.eliminar(id);
    }
}
