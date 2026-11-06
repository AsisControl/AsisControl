package com.asiscontrol.controller;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.service.CompromisoPagoService;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.service.ModuloAccessGuard;
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

@RestController
@RequestMapping("/api/compromisos-pago")
@PreAuthorize("hasAuthority('PAGOS_LEER') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA', 'APODERADO')")
public class CompromisoPagoController {

    private final CompromisoPagoService compromisoService;
    private final ModuloAccessGuard accessGuard;

    public CompromisoPagoController(CompromisoPagoService compromisoService, ModuloAccessGuard accessGuard) {
        this.compromisoService = compromisoService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public Page<PagoDto.CompromisoResponse> listar(
            @RequestParam(required = false) EstadoCompromisoPago estado,
            @RequestParam(required = false) Long idMatricula,
            @RequestParam(required = false) Long idAlumno,
            @RequestParam(required = false) Integer anioAcademico,
            @RequestParam(required = false) EstadoAdministrativoCompromiso estadoAdministrativo,
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "fechaVencimiento", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return compromisoService.listar(
                estado,
                estadoAdministrativo,
                idMatricula,
                idAlumno,
                anioAcademico,
                busqueda,
                accessGuard.restriccionApoderadoPersonaId(),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_LEER') and @moduloAccessGuard.puedeAccederCompromisoPago(#id)")
    public PagoDto.CompromisoResponse obtener(@PathVariable Long id) {
        return compromisoService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public PagoDto.CompromisoResponse crear(
            @Valid @RequestBody PagoDto.CompromisoRequest request
    ) {
        return compromisoService.emitir(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public PagoDto.CompromisoResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PagoDto.CompromisoActualizacionRequest request
    ) {
        return compromisoService.modificar(id, request);
    }

    @GetMapping("/matriculas")
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public Page<PagoDto.MatriculaCompromisoResponse> listarMatriculas(
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20, sort = "codigoMatricula") Pageable pageable) {
        return compromisoService.listarMatriculas(busqueda, pageable);
    }

    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public PagoDto.CompromisoResponse anular(
            @PathVariable Long id,
            @Valid @RequestBody PagoDto.CompromisoAnulacionRequest request) {
        return compromisoService.anular(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR', 'DIRECTOR', 'SECRETARIA', 'TESORERIA')")
    public void eliminar(@PathVariable Long id) {
        compromisoService.rechazarEliminacion();
    }
}
