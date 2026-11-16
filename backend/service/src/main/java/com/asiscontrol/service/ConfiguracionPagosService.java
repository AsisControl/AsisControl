package com.asiscontrol.service;

import com.asiscontrol.dto.ConfiguracionPagosDto.*;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.*;
import com.asiscontrol.repository.*;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service @Transactional(readOnly=true)
public class ConfiguracionPagosService {
    private final ConceptoCobroRepository conceptos;
    private final TarifaPensionRepository tarifas;
    private final BeneficioEstudianteRepository beneficios;
    private final AlumnoRepository alumnos;
    private final AnioAcademicoRepository anios;
    private final CompromisoPagoRepository compromisos;
    private final OrdenPagoRepository ordenes;
    private final BeneficioCalculoService calculo;
    private final CompromisoPagoService compromisoService;
    private final EntityManager em;
    private final AuditoriaService auditoria;
    public ConfiguracionPagosService(ConceptoCobroRepository conceptos,TarifaPensionRepository tarifas,
            BeneficioEstudianteRepository beneficios,AlumnoRepository alumnos,AnioAcademicoRepository anios,
            CompromisoPagoRepository compromisos,OrdenPagoRepository ordenes,BeneficioCalculoService calculo,
            CompromisoPagoService compromisoService,EntityManager em,AuditoriaService auditoria) {
        this.conceptos=conceptos;this.tarifas=tarifas;this.beneficios=beneficios;this.alumnos=alumnos;this.anios=anios;
        this.compromisos=compromisos;this.ordenes=ordenes;this.calculo=calculo;this.compromisoService=compromisoService;
        this.em=em;this.auditoria=auditoria;
    }
    public List<ConceptoResponse> conceptos(){return conceptos.findAllByOrderByNombreAsc().stream().map(this::respuesta).toList();}
    public List<Integer> anios(){return anios.findAll().stream().filter(AuditableEntity::isActivo).map(a->a.getAnio()).sorted(java.util.Comparator.reverseOrder()).toList();}
    public Page<EstudianteResponse> estudiantes(String busqueda,Pageable pageable){return alumnos.searchActive(limpiar(busqueda),pageable)
            .map(a->new EstudianteResponse(a.getId(),a.getCodigoAlumno(),a.getPersona().nombreCompleto()));}
    public List<TarifaResponse> tarifas(Integer anio){return tarifas.findAllByOrderByAnioAcademicoDescGradoAsc().stream()
            .filter(t->anio==null||t.getAnioAcademico()==anio).map(this::respuesta).toList();}
    public List<BeneficioResponse> beneficios(Integer anio,Long alumno){return beneficios.findAllByOrderByAnioAcademicoDescIdDesc().stream()
            .filter(b->(anio==null||b.getAnioAcademico()==anio)&&(alumno==null||b.getAlumno().getId().equals(alumno))).map(this::respuesta).toList();}
    public BeneficioCalculoService.CalculoBeneficio calcularBeneficio(Long alumno,int anio,Long concepto,int mes,BigDecimal base){return calculo.calcular(alumno,anio,concepto,mes,base);}

    @Transactional
    public ConceptoResponse guardarConcepto(Long id,ConceptoRequest r){
        ConceptoCobro c=id==null?new ConceptoCobro():conceptos.findById(id).orElseThrow(()->no("concepto"));
        if(id!=null){em.lock(c,LockModeType.PESSIMISTIC_WRITE);version(c,r.version());}
        String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);
        if(id==null&&(r.esPension()||codigo.equals("PENSION")))throw regla("CONCEPTO_RESERVADO","La pensión ya tiene un concepto propio. Configura su tarifa por año y grado.");
        if(id!=null&&!codigo.equals(c.getCodigo()))throw regla("CODIGO_CONCEPTO_INMUTABLE","El código del concepto no puede cambiarse.");
        if(conceptos.findByCodigoIgnoreCase(codigo).filter(otro->!Objects.equals(otro.getId(),id)).isPresent())throw new ConflictException("CONCEPTO_DUPLICADO","Ese código de concepto ya está registrado.");
        if((c.isEsPension()||r.esPension())&&(r.periodicidad()!=PeriodicidadCobro.MENSUAL||r.requiereAceptacion()||r.montoSugerido()!=null))throw regla("CONFIGURACION_PENSION_INVALIDA","La pensión es mensual y su importe se configura mediante tarifas.");
        if(id!=null&&r.esPension()!=c.isEsPension())throw regla("TIPO_CONCEPTO_INMUTABLE","La función del concepto no puede cambiarse.");
        c.setCodigo(codigo);c.setNombre(r.nombre().trim());c.setDescripcion(limpiar(r.descripcion()));
        c.setPeriodicidad(r.periodicidad());c.setMontoSugerido(r.montoSugerido());c.setRequiereAceptacion(r.requiereAceptacion());
        c.setActivo(true);conceptos.saveAndFlush(c);audit("GUARDAR_CONCEPTO",c,"Configuración de concepto");return respuesta(c);
    }
    @Transactional
    public void desactivarConcepto(Long id,Long version,String motivo){var c=conceptos.findById(id).orElseThrow(()->no("concepto"));
        em.lock(c,LockModeType.PESSIMISTIC_WRITE);version(c,version);exigirMotivo(motivo);
        if(c.isEsPension())throw regla("PENSION_NO_DESACTIVABLE","El concepto de pensión se conserva para configurar los planes anuales.");
        c.setActivo(false);em.flush();audit("DESACTIVAR_CONCEPTO",c,motivo);}
    @Transactional
    public TarifaResponse guardarTarifa(Long id,TarifaRequest r){
        exigirAnio(r.anioAcademico());var c=concepto(r.idConcepto());
        if(!c.isEsPension())throw regla("TARIFA_NO_PENSION","Selecciona el concepto de pensión para configurar la tarifa.");
        em.lock(c,LockModeType.PESSIMISTIC_WRITE);
        var anterior=tarifas.findByConceptoIdAndAnioAcademicoAndGrado(c.getId(),r.anioAcademico(),r.grado());
        if(anterior.isPresent()&&!Objects.equals(anterior.get().getId(),id))throw new ConflictException("TARIFA_DUPLICADA","Ya existe una tarifa para ese año y grado. Edita la tarifa registrada.");
        var t=id==null?new TarifaPension():tarifas.findById(id).orElseThrow(()->no("tarifa"));
        if(id!=null){em.lock(t,LockModeType.PESSIMISTIC_WRITE);version(t,r.version());
            if(t.getAnioAcademico()!=r.anioAcademico()||t.getGrado()!=r.grado()||!t.getConcepto().getId().equals(c.getId()))throw regla("IDENTIDAD_TARIFA_INMUTABLE","El año, grado y concepto de una tarifa registrada se conservan.");}
        t.setConcepto(c);t.setAnioAcademico(r.anioAcademico());t.setGrado(r.grado());t.setMontoMensual(r.montoMensual().setScale(2));t.setActivo(true);
        tarifas.saveAndFlush(t);audit("GUARDAR_TARIFA",t,"Configuración de tarifa; no modifica compromisos emitidos");return respuesta(t);
    }
    @Transactional
    public void desactivarTarifa(Long id,Long version,String motivo){var t=tarifas.findById(id).orElseThrow(()->no("tarifa"));
        em.lock(t,LockModeType.PESSIMISTIC_WRITE);version(t,version);exigirMotivo(motivo);exigirAnio(t.getAnioAcademico());
        t.setActivo(false);em.flush();audit("DESACTIVAR_TARIFA",t,motivo);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BeneficioResponse guardarBeneficio(Long id,BeneficioRequest r){
        exigirAnio(r.anioAcademico());var a=alumnoBloqueado(r.idAlumno());var c=concepto(r.idConcepto());
        if(r.mesInicio()>r.mesFin())throw regla("VIGENCIA_BENEFICIO_INVALIDA","El mes inicial no puede ser posterior al mes final.");
        if(r.tipo()==TipoBeneficio.PORCENTAJE&&r.valor().compareTo(new BigDecimal("100"))>0)throw regla("PORCENTAJE_INVALIDO","El porcentaje debe ser mayor que cero y no superar cien.");
        if(beneficios.superpuestos(a.getId(),c.getId(),r.anioAcademico(),r.mesInicio(),r.mesFin()).stream().anyMatch(b->!Objects.equals(b.getId(),id)))throw new ConflictException("BENEFICIO_SUPERPUESTO","El estudiante ya tiene un beneficio para ese concepto durante parte de esos meses.");
        var b=id==null?new BeneficioEstudiante():beneficios.findById(id).orElseThrow(()->no("beneficio"));
        if(id!=null){em.lock(b,LockModeType.PESSIMISTIC_WRITE);version(b,r.version());
            if(!b.getAlumno().getId().equals(a.getId())||b.getAnioAcademico()!=r.anioAcademico()||!b.getConcepto().getId().equals(c.getId()))throw regla("IDENTIDAD_BENEFICIO_INMUTABLE","El estudiante, año y concepto de un beneficio registrado se conservan.");}
        b.setAlumno(a);b.setConcepto(c);b.setAnioAcademico(r.anioAcademico());b.setNombre(r.nombre().trim());b.setTipo(r.tipo());b.setValor(r.valor().setScale(2));
        b.setMesInicio(r.mesInicio());b.setMesFin(r.mesFin());b.setMotivo(r.motivo().trim());b.setActivo(true);
        beneficios.saveAndFlush(b);audit("GUARDAR_BENEFICIO",b,r.motivo());return respuesta(b);
    }
    @Transactional
    public void desactivarBeneficio(Long id,Long version,String motivo){var b=beneficios.findById(id).orElseThrow(()->no("beneficio"));
        alumnoBloqueado(b.getAlumno().getId());em.lock(b,LockModeType.PESSIMISTIC_WRITE);version(b,version);exigirMotivo(motivo);exigirAnio(b.getAnioAcademico());
        b.setActivo(false);em.flush();audit("DESACTIVAR_BENEFICIO",b,motivo);}

    public AjusteBeneficioResponse previewBeneficio(Long id){var b=beneficios.findById(id).orElseThrow(()->no("beneficio"));return ajuste(b,false,null);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AjusteBeneficioResponse aplicarBeneficio(Long id,AplicarBeneficioRequest request){var b=beneficios.findById(id).orElseThrow(()->no("beneficio"));
        alumnoBloqueado(b.getAlumno().getId());em.refresh(b,LockModeType.PESSIMISTIC_WRITE);version(b,request.version());exigirAnio(b.getAnioAcademico());exigirMotivo(request.motivo());
        var result=ajuste(b,true,request.firmaAjuste());audit("APLICAR_BENEFICIO",b,request.motivo());return result;}
    private AjusteBeneficioResponse ajuste(BeneficioEstudiante b,boolean aplicar,String firmaEsperada){
        var candidatos=em.createQuery("select c from CompromisoPago c where c.activo=true and c.matricula.alumno.id=:alumno and c.matricula.anioAcademico=:anio and c.conceptoCobro.id=:concepto order by c.id",CompromisoPago.class)
            .setParameter("alumno",b.getAlumno().getId()).setParameter("anio",b.getAnioAcademico()).setParameter("concepto",b.getConcepto().getId()).getResultList();
        var ajustables=new java.util.ArrayList<com.asiscontrol.dto.PagoDto.CompromisoResponse>();
        var omitidos=new java.util.ArrayList<com.asiscontrol.dto.PagoDto.CompromisoResponse>();
        var cambios=new java.util.LinkedHashMap<CompromisoPago,BeneficioCalculoService.CalculoBeneficio>();
        StringBuilder firma=new StringBuilder(b.getId()+"|"+b.getVersion()+"|"+b.isActivo());
        // Obtener todos los bloqueos antes de calcular o cambiar el primer importe.
        if(aplicar)for(var c:candidatos)em.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        for(var c:candidatos){
            int mes=c.getMes()==null?c.getFechaVencimiento().getMonthValue():c.getMes();
            if((mes<b.getMesInicio()||mes>b.getMesFin())&&!Objects.equals(c.getIdBeneficio(),b.getId()))continue;
            firma.append('|').append(c.getId()).append(':').append(c.getVersion()).append(':').append(c.getMontoTotal()).append(':').append(c.getMontoPagado()).append(':').append(c.getEstadoAdministrativo());
            if(c.getMontoPagado().signum()>0||c.getEstadoAdministrativo()==EstadoAdministrativoCompromiso.ANULADO){omitidos.add(compromisoService.obtener(c.getId()));continue;}
            if(ordenes.existePagoVigente(List.of(c.getId()),Instant.now()))throw new ConflictException("PAGO_EN_CURSO","Hay un pago en curso para una cuota que deseas ajustar. Espera su resultado o vencimiento.");
            var calculado=calculo.calcular(b.getAlumno().getId(),b.getAnioAcademico(),b.getConcepto().getId(),mes,c.getMontoBase()==null?c.getMontoTotal():c.getMontoBase());
            firma.append(':').append(calculado.montoBase()).append(':').append(calculado.montoDescuento()).append(':').append(calculado.montoTotal()).append(':').append(calculado.idBeneficio()).append(':').append(calculado.beneficioVersion());
            cambios.put(c,calculado);
        }
        String firmaActual=CompromisoPagoService.claveIdentidad(firma.toString());
        if(aplicar&&!firmaActual.equals(firmaEsperada))throw new ConflictException("AJUSTE_ACTUALIZADO","Las obligaciones o el beneficio cambiaron. Revisa nuevamente la vista previa antes de aplicar el ajuste.");
        for(var entry:cambios.entrySet()){
            var c=entry.getKey();var calculado=entry.getValue();
            if(aplicar){String antes="base="+c.getMontoBase()+",descuento="+c.getMontoDescuento()+",neto="+c.getMontoTotal();
                aplicarCalculo(c,calculado);em.flush();
                auditoria.registrar("AJUSTAR_COMPROMISO_BENEFICIO",ModuloAuditoria.FINANZAS,"CompromisoPago",c.getId().toString(),ResultadoAuditoria.EXITOSO,b.getMotivo(),antes,"base="+c.getMontoBase()+",descuento="+c.getMontoDescuento()+",neto="+c.getMontoTotal());
                ajustables.add(compromisoService.obtener(c.getId()));
            }else ajustables.add(compromisoService.previsualizar(c,calculado));
        }
        return new AjusteBeneficioResponse(b.getId(),aplicar?"Ajuste aplicado a obligaciones sin pagos. Los pagos anteriores se conservaron.":"Revisa los importes antes de aplicar el beneficio.",ajustables,omitidos,firmaActual);
    }
    public static void aplicarCalculo(CompromisoPago c,BeneficioCalculoService.CalculoBeneficio x){
        c.setMontoBase(x.montoBase());c.setMontoDescuento(x.montoDescuento());c.setMontoTotal(x.montoTotal());c.setBeneficioNombre(x.beneficioNombre());c.setIdBeneficio(x.idBeneficio());
        if(c.getMontoTotal().signum()==0)c.setEstado(EstadoCompromisoPago.EXONERADO);
        else if(c.getFechaVencimiento().isBefore(java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"))))c.setEstado(EstadoCompromisoPago.VENCIDO);
        else c.setEstado(EstadoCompromisoPago.PENDIENTE);
    }
    public ConceptoCobro concepto(Long id){return conceptos.findByIdAndActivoTrue(id).orElseThrow(()->no("concepto"));}
    private Alumno alumnoBloqueado(Long id){var a=alumnos.findByIdAndActivoTrue(id).orElseThrow(()->no("estudiante"));em.flush();em.refresh(a,LockModeType.PESSIMISTIC_WRITE);if(!a.getPersona().isActivo())throw regla("ALUMNO_INACTIVO","Selecciona un estudiante activo.");return a;}
    private void exigirAnio(int anio){var a=anios.findByAnio(anio).orElseThrow(()->regla("ANIO_NO_CONFIGURADO","Configura primero el año académico."));if(!a.isActivo()||a.getEstado()==EstadoAnioAcademico.CERRADO)throw regla("ANIO_NO_EDITABLE","El año académico está cerrado o inactivo.");}
    private void version(AuditableEntity e,Long v){if(v==null||e.getVersion()!=v)throw new ConflictException("CONFIGURACION_MODIFICADA","La configuración cambió. Actualiza la consulta antes de continuar.");}
    private void exigirMotivo(String motivo){if(motivo==null||motivo.isBlank())throw regla("MOTIVO_REQUERIDO","Indica el motivo del cambio.");}
    private BusinessRuleException regla(String code,String message){return new BusinessRuleException(code,message);}
    private ResourceNotFoundException no(String entidad){return new ResourceNotFoundException("CONFIGURACION_NO_ENCONTRADA","No se encontró el registro de "+entidad+".");}
    private String limpiar(String value){return value==null||value.isBlank()?null:value.trim();}
    private void audit(String accion,AuditableEntity e,String motivo){auditoria.registrar(accion,ModuloAuditoria.FINANZAS,e.getClass().getSimpleName(),String.valueOf(em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(e)),ResultadoAuditoria.EXITOSO,motivo,null,"versión="+e.getVersion());}
    private ConceptoResponse respuesta(ConceptoCobro c){return new ConceptoResponse(c.getId(),c.getCodigo(),c.getNombre(),c.getDescripcion(),c.getPeriodicidad(),c.isEsPension(),c.getMontoSugerido(),c.isRequiereAceptacion(),c.isActivo(),c.getVersion());}
    private TarifaResponse respuesta(TarifaPension t){return new TarifaResponse(t.getId(),t.getConcepto().getId(),t.getConcepto().getNombre(),t.getAnioAcademico(),t.getGrado(),t.getMontoMensual(),t.isActivo(),t.getVersion());}
    private BeneficioResponse respuesta(BeneficioEstudiante b){return new BeneficioResponse(b.getId(),b.getAlumno().getId(),b.getAlumno().getPersona().nombreCompleto(),b.getAnioAcademico(),b.getConcepto().getId(),b.getConcepto().getNombre(),b.getNombre(),b.getTipo(),b.getValor(),b.getMesInicio(),b.getMesFin(),b.getMotivo(),b.isActivo(),b.getVersion());}
}
