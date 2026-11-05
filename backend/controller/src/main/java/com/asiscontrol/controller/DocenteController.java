package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.service.ImportacionDocenteService;
import com.asiscontrol.service.PersonaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/docentes")
public class DocenteController {

    private final PersonaService personaService;
    private final ImportacionDocenteService importacion;

    public DocenteController(
            PersonaService personaService, ImportacionDocenteService importacion) {
        this.personaService = personaService;
        this.importacion = importacion;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA')")
    public PageResponse<PersonaDtos.DocenteResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Especialidad especialidad,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "persona.apellidoPaterno") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return personaService.listDocentes(search, especialidad, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederDocente(#id)")
    public PersonaDtos.DocenteResponse get(@PathVariable Long id) {
        return personaService.getDocente(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.DocenteResponse create(
            @Valid @RequestBody PersonaDtos.DocenteCreateRequest request
    ) {
        return personaService.createDocente(request);
    }

    /**
     * US-51: con {@code confirmar=false} solo analiza el archivo y no guarda nada; con
     * {@code confirmar=true} crea las filas validas y rechaza las demas con su motivo.
     */
    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ImportResult importar(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return importacion.importar(archivo, confirmar);
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public ResponseEntity<byte[]> plantilla() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentDisposition(
                ContentDisposition.attachment().filename("plantilla_docentes.xlsx").build());
        cabeceras.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(cabeceras).body(importacion.generarPlantilla());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.DocenteResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PersonaDtos.DocenteUpdateRequest request
    ) {
        return personaService.updateDocente(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void delete(@PathVariable Long id) {
        personaService.deleteDocente(id);
    }
}
