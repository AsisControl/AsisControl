package com.asiscontrol.controller;

import com.asiscontrol.entity.Administrador;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PagosRiesgoFlujosHttpTest extends ApiIntegrationSupport {
    private Map<String,Object> compromiso(String code,int amount,LocalDate due) {
        long enrollment=em.createQuery("select m.id from Matricula m where m.alumno.id=:id",Long.class)
                .setParameter("id",student.getId()).getSingleResult();
        return data("idMatricula",enrollment,"codigoCompromiso",code,"concepto","Pension",
                "descripcion","Cuota escolar","montoTotal",amount,"moneda","PEN","fechaVencimiento",due);
    }

    @Test void registraPagosParcialesTotalesEIdempotenciaSinDuplicarImportes() throws Exception {
        var c=postOk("/api/compromisos-pago",compromiso("P-001",1000,LocalDate.now().plusDays(10)));
        String cp="/api/compromisos-pago/"+c.path("id").asLong();
        getOk("/api/compromisos-pago"); getOk(cp);
        putOk(cp,compromiso("P-001",1200,LocalDate.now().plusDays(10)));
        request("POST","/api/compromisos-pago",compromiso("P-001",1000,LocalDate.now()),409);
        var payment=data("idCompromisoPago",c.path("id").asLong(),"idApoderado",guardian.getId(),
                "numeroOperacion","OP-001","monto",300,"medioPago","YAPE","observacion","Pago inicial");
        var first=postOk("/api/transacciones-pago",payment);
        var repeated=postOk("/api/transacciones-pago",payment);
        assertThat(repeated.path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(getOk(cp).path("montoPagado").asInt()).isEqualTo(300);
        assertThat(getOk(cp).path("estado").asText()).isEqualTo("PARCIAL");
        getOk("/api/transacciones-pago"); getOk("/api/transacciones-pago/"+first.path("id").asLong());
        payment.put("monto",301);
        request("POST","/api/transacciones-pago",payment,409);
        var administrator=new Administrador(); administrator.setPersona(admin.getPersona()); em.persist(administrator);
        var finalPayment=postOk("/api/transacciones-pago",data("idCompromisoPago",c.path("id").asLong(),
                "idAdministrador",administrator.getId(),"numeroOperacion","OP-002","monto",900,"medioPago","EFECTIVO"));
        assertThat(getOk(cp).path("estado").asText()).isEqualTo("PAGADO");
        request("DELETE",cp,null,422);
        var receipt=data("idTransaccionPago",finalPayment.path("id").asLong(),"tipoComprobante","BOLETA","serie","B001","numero","0001");
        var issued=postOk("/api/comprobantes",receipt);
        assertThat(postOk("/api/comprobantes",receipt).path("id").asLong()).isEqualTo(issued.path("id").asLong());
        getOk("/api/comprobantes"); getOk("/api/comprobantes/"+issued.path("id").asLong());
        receipt.put("numero","0002"); request("POST","/api/comprobantes",receipt,409);
    }

    @Test void validaSaldoResponsableYAnulacionDeCompromisosSinPagos() throws Exception {
        var c=postOk("/api/compromisos-pago",compromiso("P-ATRASADO",100,LocalDate.now().minusDays(1)));
        String cp="/api/compromisos-pago/"+c.path("id").asLong();
        assertThat(getOk(cp).path("estado").asText()).isEqualTo("VENCIDO");
        request("POST","/api/transacciones-pago",data("idCompromisoPago",c.path("id").asLong(),"numeroOperacion","OP-A","monto",10,"medioPago","EFECTIVO"),422);
        request("POST","/api/transacciones-pago",data("idCompromisoPago",c.path("id").asLong(),"idApoderado",guardian.getId(),"numeroOperacion","OP-B","monto",101,"medioPago","EFECTIVO"),422);
        deleteOk(cp); request("GET",cp,null,404);
    }

    @Test void simulaCuotasConYSinInteresYAjustaElUltimoSaldo() throws Exception {
        for(int interest : new int[]{0,2}) {
            var simulation=request("POST","/api/simulaciones-pago",data("montoTotal",1000,"cuotaInicial",100,
                    "numeroCuotas",3,"tasaInteresMensual",interest,"fechaPrimeraCuota",LocalDate.now()),200);
            assertThat(simulation.path("cronograma")).hasSize(3);
            assertThat(simulation.path("cronograma").get(2).path("saldoRestante").decimalValue().signum()).isZero();
            assertThat(simulation.path("totalPagar").decimalValue().intValue()).isGreaterThanOrEqualTo(1000);
        }
        var paid=request("POST","/api/simulaciones-pago",data("montoTotal",100,"cuotaInicial",100,"numeroCuotas",1,
                "tasaInteresMensual",0,"fechaPrimeraCuota",LocalDate.now()),200);
        assertThat(paid.path("cronograma")).isEmpty();
        request("POST","/api/simulaciones-pago",data("montoTotal",100,"cuotaInicial",101,"numeroCuotas",1,
                "tasaInteresMensual",0,"fechaPrimeraCuota",LocalDate.now()),422);
    }

    private Map<String,Object> criterio(String operator) {
        return data("nombre","Riesgo " + operator,"descripcion","Seguimiento del alumno",
                "indicador","INASISTENCIAS","operador",operator,"umbral",5,"nivelRiesgo","ALTO");
    }
    private Map<String,Object> indicadores(int value) {
        return data("idAlumno",student.getId(),"indicadores",List.of(data("indicador","INASISTENCIAS","valor",value)));
    }

    @Test void detectaActualizaYSigueAlertaHastaResolverla() throws Exception {
        var c=postOk("/api/criterios-riesgo",criterio("MAYOR_IGUAL"));
        String cp="/api/criterios-riesgo/"+c.path("id").asLong();
        getOk("/api/criterios-riesgo"); getOk(cp); putOk(cp,criterio("MAYOR_IGUAL"));
        var alerts=request("POST","/api/evaluaciones-riesgo",indicadores(6),200);
        assertThat(alerts).hasSize(1);
        String ap="/api/alertas-riesgo/"+alerts.get(0).path("id").asLong();
        getOk("/api/alertas-riesgo"); getOk(ap);
        request("DELETE",cp,null,422);
        putOk(ap+"/estado",data("estado","EN_SEGUIMIENTO","observacion","Se contacta con familia"));
        request("PUT",ap+"/estado",data("estado","RESUELTA"),422);
        request("POST","/api/evaluaciones-riesgo",indicadores(7),200);
        var resolved=request("POST","/api/evaluaciones-riesgo",indicadores(0),200);
        assertThat(resolved.get(0).path("estado").asText()).isEqualTo("RESUELTA");
        request("PUT",ap+"/estado",data("estado","ACTIVA"),422); deleteOk(cp);
    }

    @Test void evaluaTodosLosOperadoresYRechazaIndicadoresDuplicados() throws Exception {
        for(String op : List.of("MENOR_QUE","MENOR_IGUAL","MAYOR_QUE","MAYOR_IGUAL","IGUAL")) postOk("/api/criterios-riesgo",criterio(op));
        assertThat(request("POST","/api/evaluaciones-riesgo",indicadores(5),200)).hasSize(3);
        request("POST","/api/evaluaciones-riesgo",indicadores(1),200);
        request("POST","/api/evaluaciones-riesgo",indicadores(10),200);
        var repeated=data("indicador","INASISTENCIAS","valor",5);
        request("POST","/api/evaluaciones-riesgo",data("idAlumno",student.getId(),"indicadores",List.of(repeated,repeated)),409);
    }
}
