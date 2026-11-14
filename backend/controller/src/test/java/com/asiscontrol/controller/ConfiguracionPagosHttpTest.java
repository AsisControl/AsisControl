package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfiguracionPagosHttpTest extends PagosFamiliaIntegrationSupport {
    private static final String CONFIG="/api/configuracion-pagos";
    private Long pension(){return em.createQuery("select c.id from ConceptoCobro c where c.codigo='PENSION'",Long.class).getSingleResult();}
    private Long matricula(){return em.createQuery("select m.id from Matricula m where m.alumno.id=:id",Long.class).setParameter("id",student.getId()).getSingleResult();}
    private JsonNode tarifa(String monto)throws Exception{return postOk(CONFIG+"/tarifas",data("idConcepto",pension(),"anioAcademico",2090,"grado",1,"montoMensual",monto));}
    private JsonNode beneficio(String tipo,String valor,int inicio,int fin)throws Exception{return postOk(CONFIG+"/beneficios",data("idAlumno",student.getId(),"anioAcademico",2090,"idConcepto",pension(),"nombre","Beca autorizada","tipo",tipo,"valor",valor,"mesInicio",inicio,"mesFin",fin,"motivo","Acuerdo institucional"));}
    private JsonNode generar()throws Exception{var p=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");return request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan",p.path("firmaPlan").asText()),200);}
    private List<CompromisoPago> pensiones(){return em.createQuery("select c from CompromisoPago c where c.matricula.alumno.id=:id and c.mes is not null order by c.mes",CompromisoPago.class).setParameter("id",student.getId()).getResultList();}
    private JsonNode concepto(String codigo,String nombre,boolean acepta)throws Exception{return postOk(CONFIG+"/conceptos",data("codigo",codigo,"nombre",nombre,"periodicidad","UNICO","requiereAceptacion",acepta,"montoSugerido","75.00"));}

    @Test void elApoderadoNoAdministraTarifasNiBecasYSinPermisoTampocoSeConsulta()throws Exception{
        familia("GET",CONFIG+"/conceptos",null,403);
        familia("POST",CONFIG+"/beneficios",data("idAlumno",student.getId(),"anioAcademico",2090,"idConcepto",pension(),"nombre","Beca","tipo","PORCENTAJE","valor",25,"mesInicio",3,"mesFin",12,"motivo","Acuerdo"),403);
        mvc.perform(get(CONFIG+"/conceptos").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isForbidden());
        assertThat(getOk(CONFIG+"/anios").toString()).contains("2090");
        assertThat(getOk(CONFIG+"/estudiantes?busqueda="+student.getCodigoAlumno()).path("content")).hasSize(1);
    }

    @Test void conceptosGeneralesSeEditanConVersionSinCambiarSuIdentidad()throws Exception{
        var c=concepto("SERVICIO_"+key(),"Servicio configurable",false);long id=c.path("id").asLong();
        var edit=data("codigo",c.path("codigo").asText(),"nombre","Servicio actualizado","periodicidad","MENSUAL","descripcion","Cobro por servicio","montoSugerido","90.00","version",c.path("version").asLong());
        var actualizado=putOk(CONFIG+"/conceptos/"+id,edit);assertThat(actualizado.path("nombre").asText()).isEqualTo("Servicio actualizado");
        request("PUT",CONFIG+"/conceptos/"+id,edit,409);
        edit.put("version",actualizado.path("version").asLong());edit.put("codigo","OTROCODIGO");
        request("PUT",CONFIG+"/conceptos/"+id,edit,422);
        deleteOk(CONFIG+"/conceptos/"+id+"?version="+actualizado.path("version").asLong()+"&motivo=Desuso");
        assertThat(getOk(CONFIG+"/conceptos").findValues("activo")).contains(json.getNodeFactory().booleanNode(false));
    }

    @Test void noSePermitenConceptosDuplicadosNiCrearOtraPension()throws Exception{
        String codigo="UNICO_"+key();concepto(codigo,"Concepto general",false);
        request("POST",CONFIG+"/conceptos",data("codigo",codigo,"nombre","Duplicado","periodicidad","UNICO"),409);
        request("POST",CONFIG+"/conceptos",data("codigo","PENSION","nombre","Pensión duplicada","periodicidad","MENSUAL","esPension",true),422);
        var p=getOk(CONFIG+"/conceptos");JsonNode pension=null;for(var c:p)if(c.path("esPension").asBoolean())pension=c;
        assertThat(pension).isNotNull();
        request("PUT",CONFIG+"/conceptos/"+pension.path("id").asLong(),data("codigo","PENSION","nombre","Pensión","periodicidad","UNICO","esPension",true,"version",pension.path("version").asLong()),422);
        request("DELETE",CONFIG+"/conceptos/"+pension.path("id").asLong()+"?version="+pension.path("version").asLong()+"&motivo=Desuso",null,422);
    }

    @Test void tarifasSonPorAnioYGradoYNoCambianPensionesEmitidas()throws Exception{
        var t=tarifa("400.00");generar();
        var edit=data("idConcepto",pension(),"anioAcademico",2090,"grado",1,"montoMensual","430.00","version",t.path("version").asLong());
        var nuevo=putOk(CONFIG+"/tarifas/"+t.path("id").asLong(),edit);
        assertThat(pensiones()).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("400.00"));
        request("POST",CONFIG+"/tarifas",edit,409);
        request("PUT",CONFIG+"/tarifas/"+t.path("id").asLong(),edit,409);
        edit.put("version",nuevo.path("version").asLong());edit.put("grado",2);
        request("PUT",CONFIG+"/tarifas/"+t.path("id").asLong(),edit,422);
        deleteOk(CONFIG+"/tarifas/"+t.path("id").asLong()+"?version="+nuevo.path("version").asLong()+"&motivo=Revision");
        assertThat(getOk(CONFIG+"/tarifas?anioAcademico=2090").get(0).path("activo").asBoolean()).isFalse();
    }

    @Test void unaTarifaRequiereAnioAbiertoYConceptoDePension()throws Exception{
        var c=concepto("EXTRA_"+key(),"Servicio",false);
        request("POST",CONFIG+"/tarifas",data("idConcepto",c.path("id").asLong(),"anioAcademico",2090,"grado",1,"montoMensual",100),422);
        request("POST",CONFIG+"/tarifas",data("idConcepto",pension(),"anioAcademico",2071,"grado",1,"montoMensual",100),422);
        year.setEstado(EstadoAnioAcademico.CERRADO);em.flush();
        request("POST",CONFIG+"/tarifas",data("idConcepto",pension(),"anioAcademico",2090,"grado",1,"montoMensual",100),422);
    }

    @Test void diezPensionesTienenUltimoDiaDeCadaMesYSeGeneranUnaSolaVez()throws Exception{
        tarifa("400.00");var p=generar();assertThat(p.path("items")).hasSize(10);
        assertThat(p.path("firmaPlan").asText()).hasSize(64);
        assertThat(p.path("items").get(0).path("fechaVencimiento").asText()).isEqualTo("2090-03-31");
        assertThat(p.path("items").get(1).path("fechaVencimiento").asText()).isEqualTo("2090-04-30");
        assertThat(p.path("items").get(9).path("fechaVencimiento").asText()).isEqualTo("2090-12-31");
        generar();assertThat(pensiones()).hasSize(10);
        assertThat(pensiones()).allSatisfy(c->{assertThat(c.getMontoTotal()).isEqualByComparingTo("400.00");assertThat(c.getMontoBase()).isEqualByComparingTo("400.00");assertThat(c.getMontoPagado()).isZero();assertThat(c.getIdTarifa()).isNotNull();});
    }

    @Test void faltaDeTarifaSeExplicaAntesDeGenerar()throws Exception{
        var preview=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");
        assertThat(preview.path("puedeGenerar").asBoolean()).isFalse();
        request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan","0".repeat(64)),422);
        assertThat(pensiones()).isEmpty();
    }

    @Test void matriculaFinalizadaGeneraPlanYRespetaFirmaRevisada()throws Exception{
        tarifa("400.00");var alumno=new Alumno();alumno.setPersona(person());alumno.setCodigoAlumno("NUEVO"+key());em.persist(alumno);
        var solicitud=new SolicitudMatricula();solicitud.setAlumno(alumno);solicitud.setSeccion(section);solicitud.setAnioAcademico(2090);solicitud.setGrado(1);solicitud.setEstado(EstadoSolicitudMatricula.EN_REVISION);em.persist(solicitud);em.flush();
        var plan=getOk(CONFIG+"/planes/solicitudes/"+solicitud.getId()+"/preview");
        var response=postOk("/api/matriculas",data("solicitudMatriculaId",solicitud.getId(),"versionSolicitud",solicitud.getVersion(),"firmaPlan",plan.path("firmaPlan").asText()));
        assertThat(response.path("estado").asText()).isEqualTo("ACTIVA");
        assertThat(em.createQuery("select count(c) from CompromisoPago c where c.matricula.alumno.id=:id",Long.class).setParameter("id",alumno.getId()).getSingleResult()).isEqualTo(10);
    }

    @Test void cambiarTarifaTrasVistaPreviaExigeRevisarOtraVez()throws Exception{
        var t=tarifa("400.00");var preview=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");
        putOk(CONFIG+"/tarifas/"+t.path("id").asLong(),data("idConcepto",pension(),"anioAcademico",2090,"grado",1,"montoMensual",450,"version",t.path("version").asLong()));
        var error=request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan",preview.path("firmaPlan").asText()),409);
        assertThat(error.path("code").asText()).isEqualTo("PLAN_ACTUALIZADO");assertThat(pensiones()).isEmpty();
    }

    @Test void generarOAplicarBecaNoPermiteOmitirLaRevisionDeImportes()throws Exception{
        tarifa("400.00");String ruta=CONFIG+"/planes/matriculas/"+matricula()+"/generar";
        request("POST",ruta,null,400);request("POST",ruta,data("firmaPlan"," "),400);
        assertThat(pensiones()).isEmpty();generar();
        var b=beneficio("PORCENTAJE","50.00",3,12);String aplicar=CONFIG+"/beneficios/"+b.path("id").asLong()+"/aplicar";
        request("POST",aplicar,data("motivo","Aplicar beca","version",b.path("version").asLong()),400);
        request("POST",aplicar,data("motivo","Aplicar beca","version",b.path("version").asLong(),"firmaAjuste"," "),400);
        assertThat(pensiones()).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("400.00"));
    }

    @Test void previewDePlanEmitidoConservaSnapshotsAunqueLaNuevaTarifaSeaMenorALaBeca()throws Exception{
        var t=tarifa("400.00");beneficio("IMPORTE_FIJO","150.00",3,12);generar();
        putOk(CONFIG+"/tarifas/"+t.path("id").asLong(),data("idConcepto",pension(),"anioAcademico",2090,"grado",1,"montoMensual",100,"version",t.path("version").asLong()));
        var preview=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");
        assertThat(preview.path("items")).hasSize(10);assertThat(preview.path("montoMensual").decimalValue()).isEqualByComparingTo("100.00");
        for(var item:preview.path("items")){assertThat(item.path("montoBase").decimalValue()).isEqualByComparingTo("400.00");assertThat(item.path("montoTotal").decimalValue()).isEqualByComparingTo("250.00");}
        generar();assertThat(pensiones()).hasSize(10);assertThat(pensiones()).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("250.00"));
    }

    @Test void cambiarUnCompromisoYaEmitidoInvalidaLaFirmaDelPlan()throws Exception{
        tarifa("400.00");generar();var preview=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");
        var c=pensiones().getFirst();c.setDescripcion("Actualización autorizada entre revisión y confirmación");em.flush();
        var error=request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan",preview.path("firmaPlan").asText()),409);
        assertThat(error.path("code").asText()).isEqualTo("PLAN_ACTUALIZADO");assertThat(pensiones()).hasSize(10);
    }

    @Test void becaDePorcentajeSoloAfectaMesesDeSuVigencia()throws Exception{
        tarifa("400.00");beneficio("PORCENTAJE","25.00",4,6);generar();
        var items=pensiones();assertThat(items).hasSize(10);
        assertThat(items.stream().filter(c->c.getMes()>=4&&c.getMes()<=6)).allSatisfy(c->{assertThat(c.getMontoTotal()).isEqualByComparingTo("300.00");assertThat(c.getMontoDescuento()).isEqualByComparingTo("100.00");assertThat(c.getBeneficioNombre()).isEqualTo("Beca autorizada");});
        assertThat(items.stream().filter(c->c.getMes()<4||c.getMes()>6)).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("400.00"));
        assertThat(getOk(CONFIG+"/beneficios?idAlumno="+student.getId()+"&anioAcademico=2090")).hasSize(1);
    }

    @Test void becaFijaYRedondeoSonMonetariosYSinNegativos()throws Exception{
        tarifa("400.01");beneficio("IMPORTE_FIJO","50.00",3,12);generar();
        assertThat(pensiones()).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("350.01"));
    }

    @Test void porcentajeDecimalSeRedondeaPorMensualidadADosCentimos()throws Exception{
        tarifa("400.01");beneficio("PORCENTAJE","33.33",3,12);generar();
        assertThat(pensiones()).allSatisfy(c->{assertThat(c.getMontoDescuento()).isEqualByComparingTo("133.32");assertThat(c.getMontoTotal()).isEqualByComparingTo("266.69");});
    }

    @Test void becaTotalExoneraSinInventarPagoNiComprobante()throws Exception{
        tarifa("400.00");beneficio("PORCENTAJE","100.00",3,12);generar();
        assertThat(pensiones()).allSatisfy(c->{assertThat(c.getEstado()).isEqualTo(EstadoCompromisoPago.EXONERADO);assertThat(c.getMontoTotal()).isZero();assertThat(c.getMontoPagado()).isZero();});
        assertThat(contar("TransaccionPago")).isZero();assertThat(contar("Comprobante")).isZero();
        assertThat(familia("GET",OBLIGACIONES+"?estado=EXONERADO",null,200).path("content")).hasSize(10);
        assertThat(familia("GET",OBLIGACIONES+"?estado=PAGADO",null,200).path("content")).isEmpty();
        familia(guardian,"POST",PAGOS+"/iniciar",data("obligacionesIds",List.of(pensiones().getFirst().getId()),"metodo","TARJETA"),422,java.util.UUID.randomUUID().toString());
    }

    @Test void noSeAcumulanBecasSuperpuestasYSeValidaPorcentajeYVigencia()throws Exception{
        beneficio("PORCENTAJE","25.00",3,6);
        var base=data("idAlumno",student.getId(),"anioAcademico",2090,"idConcepto",pension(),"nombre","Otra beca","tipo","PORCENTAJE","valor",50,"mesInicio",6,"mesFin",12,"motivo","Acuerdo");
        request("POST",CONFIG+"/beneficios",base,409);
        base.put("mesInicio",7);base.put("valor",101);request("POST",CONFIG+"/beneficios",base,422);
        base.put("valor",50);base.put("mesInicio",12);base.put("mesFin",7);request("POST",CONFIG+"/beneficios",base,422);
        base.put("mesInicio",7);base.put("mesFin",12);postOk(CONFIG+"/beneficios",base);
        assertThat(getOk(CONFIG+"/beneficios")).hasSize(2);
    }

    @Test void beneficioFijoMayorAlImporteNoGeneraPlanParcial()throws Exception{
        tarifa("100.00");beneficio("IMPORTE_FIJO","150.00",3,12);
        request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan","0".repeat(64)),422);assertThat(pensiones()).isEmpty();
    }

    @Test void ajusteConservaPagosAnterioresYRequiereVistaPreviaActual()throws Exception{
        tarifa("400.00");generar();var pagada=pensiones().getFirst();confirmar(iniciar("TARJETA",pagada.getId()),"APROBADO");
        var b=beneficio("PORCENTAJE","25.00",3,12);long id=b.path("id").asLong();var p=getOk(CONFIG+"/beneficios/"+id+"/preview");
        assertThat(p.path("ajustables")).hasSize(9);assertThat(p.path("omitidos")).hasSize(1);
        assertThat(p.path("ajustables").get(0).path("montoTotal").decimalValue()).isEqualByComparingTo("300.00");
        request("POST",CONFIG+"/beneficios/"+id+"/aplicar",data("motivo","Beca aprobada","version",b.path("version").asLong(),"firmaAjuste",p.path("firmaAjuste").asText()),200);
        assertThat(pagada.getMontoTotal()).isEqualByComparingTo("400.00");assertThat(pagada.getMontoPagado()).isEqualByComparingTo("400.00");
        assertThat(pensiones().stream().filter(c->!c.getId().equals(pagada.getId()))).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("300.00"));
        assertThat(contar("Comprobante")).isEqualTo(1);
    }

    @Test void cambioDeObligacionEntrePreviewYAjusteSeRechazaAntesDeModificarElLote()throws Exception{
        tarifa("400.00");generar();var b=beneficio("PORCENTAJE","50.00",3,12);long id=b.path("id").asLong();var p=getOk(CONFIG+"/beneficios/"+id+"/preview");
        var c=pensiones().getFirst();c.setDescripcion("Cambio concurrente autorizado");em.flush();
        var error=request("POST",CONFIG+"/beneficios/"+id+"/aplicar",data("motivo","Aplicar beca","version",b.path("version").asLong(),"firmaAjuste",p.path("firmaAjuste").asText()),409);
        assertThat(error.path("code").asText()).isEqualTo("AJUSTE_ACTUALIZADO");assertThat(pensiones()).allSatisfy(item->assertThat(item.getMontoTotal()).isEqualByComparingTo("400.00"));
    }

    @Test void pagoEnCursoBloqueaAjustarBecasYSigueConfirmandoImporteOriginal()throws Exception{
        tarifa("400.00");generar();var c=pensiones().getFirst();var o=iniciar("TARJETA",c.getId());var b=beneficio("PORCENTAJE","50.00",3,12);
        request("GET",CONFIG+"/beneficios/"+b.path("id").asLong()+"/preview",null,409);
        assertThat(confirmar(o,"APROBADO").path("montoTotal").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(c.getMontoPagado()).isEqualByComparingTo("400.00");
    }

    @Test void desactivarBeneficioNoReescribeObligacionesHastaAjusteExplicito()throws Exception{
        tarifa("400.00");var b=beneficio("PORCENTAJE","100.00",3,12);generar();long id=b.path("id").asLong();
        deleteOk(CONFIG+"/beneficios/"+id+"?version="+b.path("version").asLong()+"&motivo=FinDelBeneficio");
        assertThat(pensiones()).allSatisfy(c->assertThat(c.getMontoTotal()).isZero());
        var p=getOk(CONFIG+"/beneficios/"+id+"/preview");var actual=em.find(BeneficioEstudiante.class,id);
        request("POST",CONFIG+"/beneficios/"+id+"/aplicar",data("version",actual.getVersion(),"motivo","Retirar beneficio","firmaAjuste",p.path("firmaAjuste").asText()),200);
        assertThat(pensiones()).allSatisfy(c->{assertThat(c.getMontoTotal()).isEqualByComparingTo("400.00");assertThat(c.getBeneficioNombre()).isNull();});
    }

    @Test void actualizarVigenciaDeBecaConservaEmitidasHastaAplicarYRestituyeMesesExcluidos()throws Exception{
        tarifa("400.00");var b=beneficio("PORCENTAJE","50.00",3,6);generar();long id=b.path("id").asLong();
        var edit=data("idAlumno",student.getId(),"anioAcademico",2090,"idConcepto",pension(),"nombre","Beca revisada","tipo","PORCENTAJE","valor",25,"mesInicio",5,"mesFin",8,"motivo","Vigencia aprobada","version",b.path("version").asLong());
        var updated=putOk(CONFIG+"/beneficios/"+id,edit);
        assertThat(pensiones().getFirst().getMontoTotal()).isEqualByComparingTo("200.00");
        var preview=getOk(CONFIG+"/beneficios/"+id+"/preview");assertThat(preview.path("ajustables")).hasSize(6);
        request("POST",CONFIG+"/beneficios/"+id+"/aplicar",data("motivo","Actualizar mensualidades","version",updated.path("version").asLong(),"firmaAjuste",preview.path("firmaAjuste").asText()),200);
        assertThat(pensiones().stream().filter(c->c.getMes()>=5&&c.getMes()<=8)).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("300.00"));
        assertThat(pensiones().stream().filter(c->c.getMes()<5||c.getMes()>8)).allSatisfy(c->assertThat(c.getMontoTotal()).isEqualByComparingTo("400.00"));
        request("PUT",CONFIG+"/beneficios/"+id,edit,409);
    }

    @Test void unCobroGeneralNoExigeCuotaYSeDistinguePorSuReferencia()throws Exception{
        var c=concepto("SERV_"+key(),"Servicio adicional",false);var body=data("idMatricula",matricula(),"idConcepto",c.path("id").asLong(),"concepto","Texto cliente","referenciaCobro","ACTIVIDAD Á 2090","montoTotal",75,"moneda","PEN","fechaVencimiento","2090-07-31");
        var emitido=postOk("/api/compromisos-pago",body);assertThat(emitido.path("numeroCuota").isNull()).isTrue();assertThat(emitido.path("concepto").asText()).isEqualTo("Servicio adicional");
        body.put("referenciaCobro","actividad-a-2090");request("POST","/api/compromisos-pago",body,409);
        body.put("referenciaCobro","ACTIVIDAD-B");postOk("/api/compromisos-pago",body);
        body.put("numeroCuota",1);request("POST","/api/compromisos-pago",body,422);
    }

    @Test void planGestionadoNoPermiteCambiarNetoManualmenteYLosLegadosSeRevisanSinDuplicarPagos()throws Exception{
        tarifa("400.00");var legado=compromiso(1,"350.00");
        var bloqueado=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");assertThat(bloqueado.path("legados")).hasSize(1);assertThat(bloqueado.path("puedeGenerar").asBoolean()).isFalse();
        request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan",bloqueado.path("firmaPlan").asText()),422);
        request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/vincular",data("idCompromiso",legado.getId(),"mes",3,"motivo","Clasificación aprobada","version",legado.getVersion()),200);
        generar();assertThat(pensiones()).hasSize(10);assertThat(legado.getMontoTotal()).isEqualByComparingTo("350.00");
        request("PUT","/api/compromisos-pago/"+legado.getId(),data("montoTotal",100,"fechaVencimiento","2090-03-31","motivo","Cambio","version",legado.getVersion()),422);
        assertThat(legado.getMontoTotal()).isEqualByComparingTo("350.00");
    }

    @Test void clasificarLegadoComoOtroConceptoPermiteGenerarPensionesSinConfundirElPago()throws Exception{
        tarifa("400.00");var legado=compromiso(1,"80.00");var c=concepto("LEG_"+key(),"Servicio anterior",false);
        request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/vincular",data("idCompromiso",legado.getId(),"idConcepto",c.path("id").asLong(),"motivo","Era otro servicio","version",legado.getVersion()),200);
        generar();assertThat(pensiones()).hasSize(10);assertThat(legado.getNumeroCuota()).isNull();assertThat(legado.getMontoTotal()).isEqualByComparingTo("80.00");
    }

    @Test void noSeGeneranPlanesParaAlumnosInactivosOAniosCerrados()throws Exception{
        tarifa("400.00");var p=getOk(CONFIG+"/planes/matriculas/"+matricula()+"/preview");student.setActivo(false);em.flush();request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan",p.path("firmaPlan").asText()),422);
        student.setActivo(true);year.setEstado(EstadoAnioAcademico.CERRADO);em.flush();request("POST",CONFIG+"/planes/matriculas/"+matricula()+"/generar",data("firmaPlan",p.path("firmaPlan").asText()),422);
        assertThat(pensiones()).isEmpty();
    }

    @Test void clasificarLegadosRequiereMatriculaVigenteAnioAbiertoYEstudianteActivo()throws Exception{
        tarifa("400.00");var legado=compromiso(1,"350.00");var m=em.find(Matricula.class,matricula());
        var body=data("idCompromiso",legado.getId(),"mes",3,"motivo","Revisión de obligaciones anteriores","version",legado.getVersion());
        String ruta=CONFIG+"/planes/matriculas/"+m.getId()+"/vincular";
        m.setEstado(EstadoMatricula.RETIRADA);em.flush();request("POST",ruta,body,422);
        m.setEstado(EstadoMatricula.ACTIVA);student.setActivo(false);em.flush();request("POST",ruta,body,422);
        student.setActivo(true);student.getPersona().setActivo(false);em.flush();request("POST",ruta,body,422);
        student.getPersona().setActivo(true);year.setEstado(EstadoAnioAcademico.CERRADO);em.flush();request("POST",ruta,body,422);
        year.setEstado(EstadoAnioAcademico.ACTIVO);year.setActivo(false);em.flush();request("POST",ruta,body,422);
        assertThat(legado.getConceptoCobro()).isNull();assertThat(legado.getMontoTotal()).isEqualByComparingTo("350.00");
    }
}
