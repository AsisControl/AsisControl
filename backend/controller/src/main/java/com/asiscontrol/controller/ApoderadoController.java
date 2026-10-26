package com.asiscontrol.controller;

import com.asiscontrol.dto.PageResponse;
import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.service.ImportacionFamiliaService;
import com.asiscontrol.service.PersonaService;
import com.asiscontrol.service.PlantillaFamiliaService;
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
@RequestMapping("/api/apoderados")
public class ApoderadoController {

    private final PersonaService personaService;
    private final PlantillaFamiliaService plantillas;
    private final ImportacionFamiliaService importacion;

    public ApoderadoController(
            PersonaService personaService,
            PlantillaFamiliaService plantillas,
            ImportacionFamiliaService importacion) {
        this.personaService = personaService;
        this.plantillas = plantillas;
        this.importacion = importacion;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA')")
    public PageResponse<PersonaDtos.ApoderadoResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "persona.apellidoPaterno") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return personaService.listApoderados(search, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@moduloAccessGuard.puedeAccederApoderado(#id)")
    public PersonaDtos.ApoderadoResponse get(@PathVariable Long id) {
        return personaService.getApoderado(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ApoderadoResponse create(
            @Valid @RequestBody PersonaDtos.ApoderadoCreateRequest request
    ) {
        return personaService.createApoderado(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ApoderadoResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PersonaDtos.ApoderadoUpdateRequest request
    ) {
        return personaService.updateApoderado(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void delete(@PathVariable Long id) {
        personaService.deleteApoderado(id);
    }

    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ImportResult importGuardians(@RequestPart("archivo") MultipartFile file) {
        return importacion.importGuardians(file);
    }

    @PostMapping(
            value = "/parentescos/importaciones",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public PersonaDtos.ImportResult importRelationships(@RequestPart("archivo") MultipartFile file) {
        return importacion.importRelationships(file);
    }

    @GetMapping("/importaciones/plantilla")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public ResponseEntity<byte[]> guardianTemplate() {
        return excel("plantilla_apoderados.xlsx", plantillas.generateGuardianTemplate());
    }

    /** Plantilla de parentescos: une el codigo del alumno con el documento del apoderado. */
    @GetMapping("/parentescos/importaciones/plantilla")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SECRETARIA')")
    public ResponseEntity<byte[]> relationshipTemplate() {
        return excel("plantilla_parentescos.xlsx", plantillas.generateRelationshipTemplate());
    }

    private ResponseEntity<byte[]> excel(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
