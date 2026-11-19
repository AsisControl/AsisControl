package com.asiscontrol.controller;

import com.asiscontrol.dto.ConfiguracionPagosDto.PlanResponse;
import com.asiscontrol.dto.matricula.RenovacionDtos.*;
import com.asiscontrol.entity.enums.EstadoRenovacion;
import com.asiscontrol.service.RenovacionMatriculaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/renovaciones")
@PreAuthorize(RenovacionMatriculaController.LECTURA)
public class RenovacionMatriculaController {
    static final String LECTURA = "hasAuthority('MATRICULA_LEER') and hasAnyRole('ADMINISTRADOR','APODERADO')";
    private static final String FAMILIA = "hasRole('APODERADO') and hasAuthority('MATRICULA_LEER') and hasAuthority('SOLICITUDES_ESCRIBIR')";
    private static final String ADMIN = "hasRole('ADMINISTRADOR') and hasAuthority('MATRICULA_LEER')";
    private static final String DECIDIR = ADMIN + " and hasAuthority('MATRICULA_ESCRIBIR')";
    private final RenovacionMatriculaService service;

    public RenovacionMatriculaController(RenovacionMatriculaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Response> listar(@RequestParam(required = false) EstadoRenovacion estado,
            @PageableDefault(sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pagina) {
        return service.listar(estado, pagina);
    }

    @GetMapping("/opciones")
    public List<Opcion> opciones() {
        return service.opciones();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(FAMILIA)
    public Response enviar(@Valid @RequestBody CrearRequest request) {
        return service.enviar(request);
    }

    @GetMapping("/{id}")
    public Response obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/{id}/plan")
    @PreAuthorize(ADMIN)
    public PlanResponse plan(@PathVariable Long id) {
        return service.plan(id);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize(DECIDIR)
    public Response aprobar(@PathVariable Long id, @Valid @RequestBody AprobarRequest request) {
        return service.aprobar(id, request);
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize(DECIDIR)
    public Response rechazar(@PathVariable Long id, @Valid @RequestBody RechazarRequest request) {
        return service.rechazar(id, request);
    }

    @GetMapping("/promociones")
    @PreAuthorize(ADMIN)
    public Page<PromocionResponse> promociones(@RequestParam(required = false) String busqueda,
            @PageableDefault Pageable pagina) {
        return service.promociones(busqueda, pagina);
    }

    @PutMapping("/promociones/{id}")
    @PreAuthorize(DECIDIR)
    public PromocionResponse promocion(@PathVariable Long id, @Valid @RequestBody PromocionRequest request) {
        return service.guardarPromocion(id, request);
    }
}
