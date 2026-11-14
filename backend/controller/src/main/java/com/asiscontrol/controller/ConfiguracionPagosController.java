package com.asiscontrol.controller;

import com.asiscontrol.dto.ConfiguracionPagosDto.*;
import com.asiscontrol.service.ConfiguracionPagosService;
import com.asiscontrol.service.PlanPensionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/configuracion-pagos")
@PreAuthorize("hasAuthority('PAGOS_LEER') and hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','TESORERIA')")
public class ConfiguracionPagosController {
    private static final String WRITE="hasAuthority('PAGOS_ESCRIBIR') and hasAnyRole('ADMINISTRADOR','DIRECTOR','SECRETARIA','TESORERIA')";
    private final ConfiguracionPagosService configuracion;
    private final PlanPensionService planes;
    public ConfiguracionPagosController(ConfiguracionPagosService configuracion,PlanPensionService planes){this.configuracion=configuracion;this.planes=planes;}
    @GetMapping("/conceptos") public List<ConceptoResponse> conceptos(){return configuracion.conceptos();}
    @GetMapping("/anios") public List<Integer> anios(){return configuracion.anios();}
    @GetMapping("/estudiantes") public Page<EstudianteResponse> estudiantes(@RequestParam(required=false)String busqueda,@PageableDefault(size=10)Pageable pageable){return configuracion.estudiantes(busqueda,pageable);}
    @GetMapping("/tarifas") public List<TarifaResponse> tarifas(@RequestParam(required=false)Integer anioAcademico){return configuracion.tarifas(anioAcademico);}
    @GetMapping("/beneficios") public List<BeneficioResponse> beneficios(@RequestParam(required=false)Integer anioAcademico,@RequestParam(required=false)Long idAlumno){return configuracion.beneficios(anioAcademico,idAlumno);}
    @PostMapping("/conceptos") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED) public ConceptoResponse concepto(@Valid @RequestBody ConceptoRequest request){return configuracion.guardarConcepto(null,request);}
    @PutMapping("/conceptos/{id}") @PreAuthorize(WRITE) public ConceptoResponse concepto(@PathVariable Long id,@Valid @RequestBody ConceptoRequest request){return configuracion.guardarConcepto(id,request);}
    @DeleteMapping("/conceptos/{id}") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.NO_CONTENT) public void concepto(@PathVariable Long id,@RequestParam Long version,@RequestParam String motivo){configuracion.desactivarConcepto(id,version,motivo);}
    @PostMapping("/tarifas") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED) public TarifaResponse tarifa(@Valid @RequestBody TarifaRequest request){return configuracion.guardarTarifa(null,request);}
    @PutMapping("/tarifas/{id}") @PreAuthorize(WRITE) public TarifaResponse tarifa(@PathVariable Long id,@Valid @RequestBody TarifaRequest request){return configuracion.guardarTarifa(id,request);}
    @DeleteMapping("/tarifas/{id}") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.NO_CONTENT) public void tarifa(@PathVariable Long id,@RequestParam Long version,@RequestParam String motivo){configuracion.desactivarTarifa(id,version,motivo);}
    @PostMapping("/beneficios") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED) public BeneficioResponse beneficio(@Valid @RequestBody BeneficioRequest request){return configuracion.guardarBeneficio(null,request);}
    @PutMapping("/beneficios/{id}") @PreAuthorize(WRITE) public BeneficioResponse beneficio(@PathVariable Long id,@Valid @RequestBody BeneficioRequest request){return configuracion.guardarBeneficio(id,request);}
    @DeleteMapping("/beneficios/{id}") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.NO_CONTENT) public void beneficio(@PathVariable Long id,@RequestParam Long version,@RequestParam String motivo){configuracion.desactivarBeneficio(id,version,motivo);}
    @GetMapping("/beneficios/{id}/preview") public AjusteBeneficioResponse previewBeneficio(@PathVariable Long id){return configuracion.previewBeneficio(id);}
    @PostMapping("/beneficios/{id}/aplicar") @PreAuthorize(WRITE) public AjusteBeneficioResponse aplicar(@PathVariable Long id,@Valid @RequestBody AplicarBeneficioRequest request){return configuracion.aplicarBeneficio(id,request);}
    @GetMapping("/planes/solicitudes/{id}/preview") public PlanResponse solicitud(@PathVariable Long id){return planes.previewSolicitud(id);}
    @GetMapping("/planes/matriculas/{id}/preview") public PlanResponse matricula(@PathVariable Long id){return planes.previewMatricula(id);}
    @PostMapping("/planes/matriculas/{id}/generar") @PreAuthorize(WRITE) public PlanResponse generar(@PathVariable Long id,@Valid @RequestBody GenerarPlanRequest request){return planes.generar(id,request.firmaPlan());}
    @PostMapping("/planes/matriculas/{id}/vincular") @PreAuthorize(WRITE) public PlanResponse vincular(@PathVariable Long id,@Valid @RequestBody VincularRequest request){return planes.vincular(id,request);}
}
