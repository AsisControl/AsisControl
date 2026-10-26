package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.academico.ConfiguracionDtos.*;
import com.asiscontrol.entity.planificacion.DiaNoLectivo;
import com.asiscontrol.service.ConfiguracionAcademicaService;
import com.asiscontrol.service.DiaNoLectivoService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracion-academica")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ConfiguracionAcademicaController {
    private final ConfiguracionAcademicaService configuracionService;
    private final DiaNoLectivoService diaNoLectivoService;

    public ConfiguracionAcademicaController(
            ConfiguracionAcademicaService configuracionService,
            DiaNoLectivoService diaNoLectivoService) {
        this.configuracionService = configuracionService;
        this.diaNoLectivoService = diaNoLectivoService;
    }

    @GetMapping("/anios")
    public PageResponse<AnioResponse> listar(
            @PageableDefault(size = 20, sort = "anio") Pageable page) {
        return configuracionService.listar(page);
    }

    @PostMapping("/anios")
    @ResponseStatus(HttpStatus.CREATED)
    public AnioResponse crear(@Valid @RequestBody CrearAnio request) {
        return configuracionService.crear(request);
    }

    @GetMapping("/anios/{anio}")
    public AnioResponse obtener(@PathVariable int anio) {
        return configuracionService.obtener(anio);
    }

    /** Solo años en borrador sin uso; ver ConfiguracionAcademicaService#eliminar. */
    @DeleteMapping("/anios/{anio}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int anio, @RequestParam Long version) {
        configuracionService.eliminar(anio, version);
    }

    @PutMapping("/anios/{anio}/estado")
    public AnioResponse estado(@PathVariable int anio, @Valid @RequestBody EstadoAnio request) {
        return configuracionService.estado(anio, request);
    }

    @PutMapping("/anios/{anio}/admision")
    public AnioResponse admision(@PathVariable int anio, @Valid @RequestBody AdmisionAnio request) {
        return configuracionService.admision(anio, request);
    }

    @PostMapping("/anios/{anio}/periodos")
    @ResponseStatus(HttpStatus.CREATED)
    public AnioResponse periodo(@PathVariable int anio, @Valid @RequestBody CrearPeriodo request) {
        return configuracionService.agregarPeriodo(anio, request);
    }

    @PutMapping("/periodos/{id}/estado")
    public AnioResponse estadoPeriodo(
            @PathVariable Long id, @Valid @RequestBody EstadoPeriodo request) {
        return configuracionService.estadoPeriodo(id, request);
    }

    @PostMapping("/anios/{anio}/dias-no-lectivos")
    @ResponseStatus(HttpStatus.CREATED)
    public DiaNoLectivoResponse crearDiaNoLectivo(
            @PathVariable int anio, @Valid @RequestBody CrearDiaNoLectivo request) {
        return mapear(
                diaNoLectivoService.crear(anio, request.fecha(), request.descripcion()), anio);
    }

    @GetMapping("/anios/{anio}/dias-no-lectivos")
    public List<DiaNoLectivoResponse> listarDiasNoLectivos(@PathVariable int anio) {
        return diaNoLectivoService.listar(anio).stream()
                .map(diaNoLectivo -> mapear(diaNoLectivo, anio))
                .toList();
    }

    @PatchMapping("/dias-no-lectivos/{id}")
    public DiaNoLectivoResponse actualizarDescripcionDiaNoLectivo(
            @PathVariable Long id, @Valid @RequestBody ActualizarDiaNoLectivo request) {
        return mapear(
                diaNoLectivoService.actualizarDescripcion(
                        id, request.descripcion(), request.version()));
    }

    @PostMapping("/anios/{anio}/grados")
    @ResponseStatus(HttpStatus.CREATED)
    public AnioResponse grado(@PathVariable int anio, @Valid @RequestBody CrearGrado request) {
        return configuracionService.agregarGrado(anio, request);
    }

    @PutMapping("/grados/{id}")
    public AnioResponse estadoGrado(
            @PathVariable Long id, @Valid @RequestBody EstadoGrado request) {
        return configuracionService.estadoGrado(id, request);
    }

    private DiaNoLectivoResponse mapear(DiaNoLectivo diaNoLectivo, int anio) {
        return new DiaNoLectivoResponse(
                diaNoLectivo.getId(),
                anio,
                diaNoLectivo.getFecha(),
                diaNoLectivo.getDescripcion(),
                diaNoLectivo.getFechaCreacion(),
                diaNoLectivo.getFechaActualizacion(),
                diaNoLectivo.getVersion());
    }

    private DiaNoLectivoResponse mapear(DiaNoLectivo diaNoLectivo) {
        return mapear(diaNoLectivo, diaNoLectivo.getAnioAcademico().getAnio());
    }
}
