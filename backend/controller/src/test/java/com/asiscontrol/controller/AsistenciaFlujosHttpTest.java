package com.asiscontrol.controller;

import com.asiscontrol.entity.CodigoQRAsistencia;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AsistenciaFlujosHttpTest extends ApiIntegrationSupport {
    private Map<String,Object> registro(LocalDate date,String state) {
        return data("alumnoId",student.getId(),"asignacionCursoId",assignment.getId(),
                "fechaRegistro",date,"estado",state,"comentario","Observacion");
    }
    @Test void registraAsistenciaIndividualYPorLoteConValidaciones() throws Exception {
        var a=postOk("/api/asistencias/alumnos",registro(LocalDate.now(),"PRESENTE"));
        String p="/api/asistencias/alumnos/"+a.path("id").asLong();
        assertThat(a.path("estado").asText()).isEqualTo("PRESENTE");
        getOk("/api/asistencias/alumnos"); getOk(p);
        putOk(p,data("estado","TARDANZA","comentario","Llego tarde","version",0));
        var item=data("alumnoId",student.getId(),"estado","INASISTENCIA","comentario","Ausente");
        var batch=postOk("/api/asistencias/alumnos/lote",data("asignacionCursoId",assignment.getId(),"fechaRegistro",LocalDate.now().minusDays(1),"asistencias",List.of(item)));
        assertThat(batch.get(0).path("estado").asText()).isEqualTo("INASISTENCIA");
        request("POST","/api/asistencias/alumnos",registro(LocalDate.now().plusDays(1),"PRESENTE"),422);
        request("POST","/api/asistencias/alumnos",registro(LocalDate.now(),"INASISTENCIA_JUSTIFICADA"),422);
        request("POST","/api/asistencias/alumnos",registro(LocalDate.now(),"PRESENTE"),409);
        request("POST","/api/asistencias/alumnos/lote",data("asignacionCursoId",assignment.getId(),"fechaRegistro",LocalDate.now().minusDays(2),"asistencias",List.of(item,item)),409);
    }

    @Test void justificaAusenciaObservaSubsanaYApruebaConTrazabilidad() throws Exception {
        var a=postOk("/api/asistencias/alumnos",registro(LocalDate.now(),"INASISTENCIA"));
        var j=postOk("/api/justificaciones-asistencia",data("asistenciaAlumnoId",a.path("id").asLong(),"motivo","Cita medica","archivoIds",Set.of()));
        String p="/api/justificaciones-asistencia/"+j.path("id").asLong();
        getOk("/api/justificaciones-asistencia"); getOk(p);
        request("POST",p+"/iniciar-revision",data("docenteRevisorId",teacher.getId()),200);
        request("POST",p+"/observar",data("docenteRevisorId",teacher.getId(),"observacion","Adjuntar sustento"),200);
        request("POST",p+"/subsanar",data("motivo","Sustento entregado","archivoIds",Set.of(),"version",getOk(p).path("version").asLong()),200);
        request("POST",p+"/iniciar-revision",data("docenteRevisorId",teacher.getId()),200);
        request("POST",p+"/aprobar",data("docenteRevisorId",teacher.getId(),"observacion","Conforme"),200);
        assertThat(getOk("/api/asistencias/alumnos/"+a.path("id").asLong()).path("estado").asText())
                .isEqualTo("INASISTENCIA_JUSTIFICADA");
        request("POST",p+"/cancelar",null,422);
    }

    @Test void permiteRechazarOCancelarYNoJustificaPresencia() throws Exception {
        for(int i=0;i<2;i++) {
            var a=postOk("/api/asistencias/alumnos",registro(LocalDate.now().minusDays(i),"TARDANZA"));
            var j=postOk("/api/justificaciones-asistencia",data("asistenciaAlumnoId",a.path("id").asLong(),"motivo","Motivo declarado","archivoIds",Set.of()));
            String p="/api/justificaciones-asistencia/"+j.path("id").asLong();
            if(i==0) request("POST",p+"/cancelar",null,200);
            else {
                request("POST",p+"/iniciar-revision",data("docenteRevisorId",teacher.getId()),200);
                request("POST",p+"/rechazar",data("docenteRevisorId",teacher.getId(),"observacion","Sin sustento"),200);
            }
        }
        var present=postOk("/api/asistencias/alumnos",registro(LocalDate.now().minusDays(3),"PRESENTE"));
        request("POST","/api/justificaciones-asistencia",data("asistenciaAlumnoId",present.path("id").asLong(),"motivo","No corresponde","archivoIds",Set.of()),422);
    }

    @Test void emiteRenuevaCancelaQrYMarcaSinDuplicarLaJornada() throws Exception {
        var qr=postOk("/api/asistencias/docentes/qr",data("docenteId",teacher.getId(),"vigenciaSegundos",120));
        String p="/api/asistencias/docentes/qr/"+qr.path("id").asLong();
        assertThat(getOk(p).path("token").isNull()).isTrue();
        var renewed=request("POST",p+"/renovar",null,200);
        request("POST",p+"/cancelar",null,422);
        var marked=request("POST","/api/asistencias/docentes/marcar",data("token",renewed.path("token").asText()),200);
        assertThat(marked.path("estado").asText()).isEqualTo("PRESENTE");
        request("POST","/api/asistencias/docentes/marcar",data("token",renewed.path("token").asText()),422);
        request("POST","/api/asistencias/docentes/qr",data("docenteId",teacher.getId()),409);
        getOk("/api/asistencias/docentes");
    }

    @Test void admiteContingenciaYRecuperaQrExpirado() throws Exception {
        var qr=postOk("/api/asistencias/docentes/qr",data("docenteId",teacher.getId()));
        long id=qr.path("id").asLong();
        em.find(CodigoQRAsistencia.class,id).setFechaHoraExpiracion(LocalDateTime.now().minusSeconds(1)); em.flush();
        assertThat(getOk("/api/asistencias/docentes/qr/"+id).path("estado").asText()).isEqualTo("EXPIRADO");
        var renewed=request("POST","/api/asistencias/docentes/qr/"+id+"/renovar",null,200);
        request("POST","/api/asistencias/docentes/qr/"+renewed.path("id").asLong()+"/cancelar",null,204);
        var contingent=postOk("/api/asistencias/docentes/contingencia",data("docenteId",teacher.getId(),"fechaJornada",LocalDate.now(),"estado","TARDANZA","motivo","Problema con el lector QR"));
        assertThat(contingent.path("contingencia").asBoolean()).isTrue();
        request("POST","/api/asistencias/docentes/contingencia",data("docenteId",teacher.getId(),"fechaJornada",LocalDate.now().plusDays(1),"estado","PRESENTE","motivo","Prueba"),422);
    }
}
