package com.asiscontrol.service;

import com.asiscontrol.dto.ConfiguracionPagosDto.*;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.*;
import com.asiscontrol.repository.*;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Diez pensiones por estudiante y año; no confunde rematrícula con un nuevo plan. */
@Service @Transactional(readOnly=true)
public class PlanPensionService {
    private final MatriculaRepository matriculas;
    private final SolicitudMatriculaRepository solicitudes;
    private final ConceptoCobroRepository conceptos;
    private final TarifaPensionRepository tarifas;
    private final CompromisoPagoRepository compromisos;
    private final CompromisoPagoService emisor;
    private final BeneficioCalculoService calculo;
    private final OrdenPagoRepository ordenes;
    private final EntityManager em;
    private final AuditoriaService auditoria;
    private final AnioAcademicoRepository anios;
    public PlanPensionService(MatriculaRepository matriculas,SolicitudMatriculaRepository solicitudes,
            ConceptoCobroRepository conceptos,TarifaPensionRepository tarifas,CompromisoPagoRepository compromisos,
            CompromisoPagoService emisor,BeneficioCalculoService calculo,OrdenPagoRepository ordenes,
            EntityManager em,AuditoriaService auditoria,AnioAcademicoRepository anios){
        this.matriculas=matriculas;this.solicitudes=solicitudes;this.conceptos=conceptos;this.tarifas=tarifas;
        this.compromisos=compromisos;this.emisor=emisor;this.calculo=calculo;this.ordenes=ordenes;this.em=em;this.auditoria=auditoria;
        this.anios=anios;
    }
    public PlanResponse previewSolicitud(Long id){var s=solicitudes.findByIdAndActivoTrue(id).orElseThrow(()->no("solicitud"));
        if(s.getSeccion()==null)throw regla("SECCION_REQUERIDA","Selecciona la sección para consultar la tarifa del grado.");
        return preview(null,s.getId(),s.getAlumno(),s.getAnioAcademico(),s.getSeccion().getGrado(),false);}
    public PlanResponse previewMatricula(Long id){var m=matriculas.findByIdAndActivoTrue(id).orElseThrow(()->no("matrícula"));
        return preview(m.getId(),m.getSolicitudMatricula().getId(),m.getAlumno(),m.getAnioAcademico(),m.getSeccion().getGrado(),false);}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PlanResponse generar(Long id){return generar(id,null);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PlanResponse generar(Long id,String firma){var m=matriculas.bloquearPorId(id).orElseThrow(()->no("matrícula"));return generar(m,firma);}
    @Transactional
    public PlanResponse generar(Matricula m,String firmaEsperada){
        if(m.getEstado()!=EstadoMatricula.ACTIVA)throw regla("MATRICULA_NO_VIGENTE","El plan se genera para una matrícula vigente.");
        em.flush();em.refresh(m.getAlumno(),LockModeType.PESSIMISTIC_WRITE);
        validarEstudianteAnio(m);
        var plan=preview(m.getId(),m.getSolicitudMatricula().getId(),m.getAlumno(),m.getAnioAcademico(),m.getSeccion().getGrado(),true);
        if(!plan.puedeGenerar())throw regla(plan.legados().isEmpty()?"PLAN_PENSION_NO_CONFIGURADO":"PLAN_REQUIERE_REVISION",plan.mensaje());
        if(firmaEsperada!=null&&!firmaEsperada.equals(plan.firmaPlan()))throw new ConflictException("PLAN_ACTUALIZADO","La tarifa o el beneficio cambió. Revisa nuevamente el plan antes de finalizar la matrícula.");
        var concepto=conceptos.findByEsPensionTrueAndActivoTrue().orElseThrow(()->no("concepto de pensión"));
        var tarifa=tarifas.findById(plan.idTarifa()).orElseThrow(()->no("tarifa"));
        for(var item:plan.items())if(item.idCompromiso()==null){
            emisor.emitirPension(m,concepto,tarifa,item.mes(),calculo.calcular(m.getAlumno().getId(),m.getAnioAcademico(),concepto.getId(),item.mes(),tarifa.getMontoMensual()));
        }
        em.flush();return previewMatricula(m.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PlanResponse vincular(Long id,VincularRequest request){
        var m=matriculas.bloquearPorId(id).orElseThrow(()->no("matrícula"));em.flush();em.refresh(m.getAlumno(),LockModeType.PESSIMISTIC_WRITE);
        if(m.getEstado()!=EstadoMatricula.ACTIVA)throw regla("MATRICULA_NO_VIGENTE","Selecciona una matrícula vigente para revisar sus compromisos.");
        validarEstudianteAnio(m);
        var c=compromisos.findByIdAndActivoTrue(request.idCompromiso()).orElseThrow(()->no("compromiso"));em.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(c.getVersion()!=request.version())throw new ConflictException("COMPROMISO_MODIFICADO","El compromiso cambió. Actualiza la revisión.");
        if(!c.getMatricula().getAlumno().getId().equals(m.getAlumno().getId())||c.getMatricula().getAnioAcademico()!=m.getAnioAcademico())throw regla("COMPROMISO_FUERA_PLAN","El compromiso no corresponde al estudiante y año del plan.");
        if(c.getConceptoCobro()!=null||c.getEstadoAdministrativo()==EstadoAdministrativoCompromiso.ANULADO)throw regla("COMPROMISO_YA_CLASIFICADO","Selecciona un compromiso anterior activo que aún no tenga concepto del catálogo.");
        if(ordenes.existePagoVigente(List.of(c.getId()),Instant.now()))throw new ConflictException("PAGO_EN_CURSO","Espera el resultado o vencimiento del pago antes de vincular este compromiso.");
        var concepto=request.mes()!=null?conceptos.findByEsPensionTrueAndActivoTrue().orElseThrow(()->no("pensión")):
            conceptos.findByIdAndActivoTrue(request.idConcepto()==null?-1L:request.idConcepto()).orElseThrow(()->regla("CONCEPTO_REQUERIDO","Selecciona el mes de pensión o un concepto adicional."));
        if(request.mes()==null&&concepto.isEsPension())throw regla("MES_REQUERIDO","Indica a qué mes de pensión corresponde el compromiso.");
        String clave=request.mes()!=null?CompromisoPagoService.clavePension(m.getAlumno().getId(),m.getAnioAcademico(),concepto.getId(),request.mes()):
            CompromisoPagoService.claveIdentidad("UNICO|"+m.getAlumno().getId()+"|"+m.getAnioAcademico()+"|"+concepto.getId()+"|"+c.getCodigoCompromiso());
        if(compromisos.findByClaveObligacion(clave).isPresent()||compromisos.porAlumnoAnio(m.getAlumno().getId(),m.getAnioAcademico()).stream()
                .anyMatch(otro->otro.getConceptoCobro()!=null&&otro.getConceptoCobro().getId().equals(concepto.getId())&&request.mes()!=null&&Objects.equals(otro.getMes(),request.mes())))
            throw new ConflictException("PENSION_DUPLICADA","Ese mes ya tiene un compromiso vinculado.");
        String antes="concepto="+c.getConcepto()+",cuota="+c.getNumeroCuota()+",monto="+c.getMontoTotal();
        c.setConceptoCobro(concepto);c.setMes(request.mes());c.setNumeroCuota(request.mes()==null?null:request.mes()-2);
        c.setReferenciaCobro(c.getCodigoCompromiso());c.setClaveObligacion(clave);
        if(c.getMontoBase()==null)c.setMontoBase(c.getMontoTotal());if(c.getMontoDescuento()==null)c.setMontoDescuento(new BigDecimal("0.00"));
        em.flush();auditoria.registrar("VINCULAR_COMPROMISO_PLAN",ModuloAuditoria.FINANZAS,"CompromisoPago",c.getId().toString(),ResultadoAuditoria.EXITOSO,request.motivo(),antes,"conceptoId="+concepto.getId()+",mes="+request.mes());
        return previewMatricula(id);
    }
    private PlanResponse preview(Long idMatricula,Long idSolicitud,Alumno alumno,int anio,int grado,boolean bloquear){
        var concepto=conceptos.findByEsPensionTrueAndActivoTrue().orElse(null);
        var tarifa=concepto==null?null:tarifas.findByConceptoIdAndAnioAcademicoAndGrado(concepto.getId(),anio,grado).filter(TarifaPension::isActivo).orElse(null);
        if(tarifa!=null&&bloquear){em.refresh(tarifa,LockModeType.PESSIMISTIC_WRITE);if(!tarifa.isActivo())tarifa=null;}
        var previos=compromisos.porAlumnoAnio(alumno.getId(),anio);
        var legados=previos.stream().filter(c->c.getConceptoCobro()==null&&c.getNumeroCuota()!=null&&c.getEstadoAdministrativo()==EstadoAdministrativoCompromiso.ACTIVO)
            .map(c->emisor.obtener(c.getId())).toList();
        if(tarifa==null)return new PlanResponse(idMatricula,idSolicitud,alumno.getId(),alumno.getPersona().nombreCompleto(),anio,grado,null,null,false,
            "Configura una tarifa de pensión activa para el año y grado del estudiante.",List.of(),legados,null,null);
        List<PlanItem> items=new ArrayList<>();StringBuilder firma=new StringBuilder(tarifa.getId()+"|"+tarifa.getVersion()+"|"+anio+"|"+grado+"|"+alumno.getId());
        for(int mes=3;mes<=12;mes++){
            final int actual=mes;
            var existente=previos.stream().filter(c->c.getConceptoCobro()!=null&&c.getConceptoCobro().getId().equals(concepto.getId())&&Objects.equals(c.getMes(),actual)).findFirst().orElse(null);
            if(existente!=null){
                var r=emisor.obtener(existente.getId());
                firma.append('|').append(mes).append(":EMITIDA:").append(r.id()).append(':').append(r.version())
                    .append(':').append(r.montoBase()).append(':').append(r.montoDescuento()).append(':').append(r.montoTotal())
                    .append(':').append(r.montoPagado()).append(':').append(r.fechaVencimiento()).append(':').append(r.estadoAdministrativo())
                    .append(':').append(r.estado()).append(':').append(r.idBeneficio()).append(':').append(r.idTarifa()).append(':').append(r.tarifaVersion());
                items.add(new PlanItem(mes,mes-2,r.concepto(),r.fechaVencimiento(),r.montoBase(),r.montoDescuento(),r.montoTotal(),r.beneficioNombre(),r.id(),r.estado().name()));
            }else{
                var precio=calculo.calcular(alumno.getId(),anio,concepto.getId(),mes,tarifa.getMontoMensual());
                firma.append('|').append(mes).append(":POR_GENERAR:").append(precio.montoBase()).append(':').append(precio.montoDescuento())
                    .append(':').append(precio.montoTotal()).append(':').append(precio.idBeneficio()).append(':').append(precio.beneficioVersion());
                String titulo=concepto.getNombre()+" de "+Month.of(mes).getDisplayName(TextStyle.FULL,Locale.forLanguageTag("es-PE"))+" de "+anio;
                items.add(new PlanItem(mes,mes-2,titulo,YearMonth.of(anio,mes).atEndOfMonth(),precio.montoBase(),precio.montoDescuento(),precio.montoTotal(),precio.beneficioNombre(),null,precio.montoTotal().signum()==0?"EXONERADO":"POR_GENERAR"));
            }
        }
        boolean permitido=legados.isEmpty();return new PlanResponse(idMatricula,idSolicitud,alumno.getId(),alumno.getPersona().nombreCompleto(),anio,grado,tarifa.getId(),tarifa.getMontoMensual(),permitido,
            permitido?(items.stream().allMatch(i->i.idCompromiso()!=null)?"Las diez pensiones ya están registradas.":"Se generarán únicamente las pensiones que faltan."):
                "Revisa y clasifica los compromisos anteriores antes de generar nuevas pensiones.",items,legados,tarifa.getVersion(),CompromisoPagoService.claveIdentidad(firma.toString()));
    }
    private void validarEstudianteAnio(Matricula matricula){
        var anio=anios.findByAnio(matricula.getAnioAcademico()).orElseThrow(()->regla("ANIO_NO_CONFIGURADO","Configura el año académico antes de gestionar el plan."));
        if(!anio.isActivo()||anio.getEstado()==EstadoAnioAcademico.CERRADO)throw regla("ANIO_NO_EDITABLE","El año académico está cerrado o inactivo.");
        if(!matricula.getAlumno().isActivo()||!matricula.getAlumno().getPersona().isActivo())throw regla("ALUMNO_INACTIVO","Selecciona una matrícula de un estudiante activo.");
    }
    private ResourceNotFoundException no(String entidad){return new ResourceNotFoundException("PLAN_REGISTRO_NO_ENCONTRADO","No se encontró el registro de "+entidad+".");}
    private BusinessRuleException regla(String code,String message){return new BusinessRuleException(code,message);}
}
