package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.service.CargaMasivaFamiliasService;
import com.asiscontrol.service.ImportacionAlumnoService;
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
@RequestMapping("/api/alumnos")
public class AlumnoController {

    private final PersonaService personaService;
    private final ImportacionAlumnoService importacionService;
    private final CargaMasivaFamiliasService cargaMasiva;

    public AlumnoController(
            PersonaService personaService,
            ImportacionAlumnoService importacionService,
            CargaMasivaFamiliasService cargaMasiva) {
        this.personaService = personaService;
        this.importacionService = importacionService;
        this.cargaMasiva = cargaMasiva;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','DOCENTE')")
    public PageResponse<PersonaDtos.AlumnoResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "persona.apellidoPaterno") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return personaService.listAlumnos(search, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederAlumno(#id)")
    public PersonaDtos.AlumnoResponse get(@PathVariable Long id) {
        return personaService.getAlumno(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.AlumnoResponse create(
            @Valid @RequestBody PersonaDtos.AlumnoCreateRequest request) {
        // El alta web no admite asignar códigos; la importación interna conserva su contrato
        // legado.
        return personaService.createAlumno(
                new PersonaDtos.AlumnoCreateRequest(request.persona(), null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.AlumnoResponse update(
            @PathVariable Long id, @Valid @RequestBody PersonaDtos.AlumnoUpdateRequest request) {
        return personaService.updateAlumno(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void delete(@PathVariable Long id) {
        personaService.deleteAlumno(id);
    }

    @PostMapping("/{id}/apoderados")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ApoderadoVinculadoResponse linkGuardian(
            @PathVariable Long id,
            @Valid @RequestBody PersonaDtos.VinculoApoderadoRequest request) {
        return personaService.linkGuardian(id, request);
    }

    @DeleteMapping("/{id}/apoderados/{apoderadoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public void unlinkGuardian(@PathVariable Long id, @PathVariable Long apoderadoId) {
        personaService.unlinkGuardian(id, apoderadoId);
    }

    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ImportResult importStudents(@RequestPart("archivo") MultipartFile file) {
        return importacionService.importStudents(file);
    }

    /**
     * Carga masiva de familias, todo o nada. Con {@code confirmar=false} solo valida los archivos;
     * con {@code confirmar=true} guarda todo si ningun archivo tiene errores, o nada si los tiene.
     */
    @PostMapping(value = "/importaciones/familias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.FamilyImportResult importFamilies(
            @RequestPart(value = "estudiantes", required = false) MultipartFile estudiantes,
            @RequestPart(value = "apoderados", required = false) MultipartFile apoderados,
            @RequestPart(value = "parentescos", required = false) MultipartFile parentescos,
            @RequestParam(defaultValue = "false") boolean confirmar) {
        return cargaMasiva.procesar(estudiantes, apoderados, parentescos, confirmar);
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public ResponseEntity<byte[]> template() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("plantilla_alumnos.xlsx").build());
        headers.setContentType(
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(importacionService.generateTemplate());
    }
}
