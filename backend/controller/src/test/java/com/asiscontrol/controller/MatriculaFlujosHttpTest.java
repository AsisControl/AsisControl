package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MatriculaFlujosHttpTest extends ApiIntegrationSupport {
    private String nuevaSolicitud() throws Exception {
        var alumno=new Alumno(); alumno.setPersona(person()); alumno.setCodigoAlumno("NUEVO"+key()); em.persist(alumno); em.flush();
        var s=postOk("/api/solicitudes-matricula",data("alumnoId",alumno.getId(),"anioAcademico",2090,"grado",1,"observaciones","Documentos entregados"));
        return "/api/solicitudes-matricula/"+s.path("id").asLong();
    }
    private void estado(String path,String state,String observation,String reason) throws Exception {
        putOk(path,data("estado",state,"observaciones",observation,"motivoRechazo",reason,
                "version",getOk(path).path("version").asLong()));
    }

    @Test void revisaObservaSubsanaAsignaSeccionYFinalizaMatricula() throws Exception {
        String s=nuevaSolicitud();
        getOk("/api/solicitudes-matricula");
        putOk(s,data("observaciones","Informacion actualizada","version",getOk(s).path("version").asLong()));
        estado(s,"ENVIADA",null,null); estado(s,"EN_REVISION",null,null);
        estado(s,"OBSERVADA","Falta documento",null);
        estado(s,"ENVIADA",null,null); estado(s,"EN_REVISION",null,null);
        putOk(s,data("seccionId",section.getId(),"version",getOk(s).path("version").asLong()));
        var reviewed=getOk(s);
        var enrollment=postOk("/api/matriculas",data("solicitudMatriculaId",reviewed.path("id").asLong(),"versionSolicitud",reviewed.path("version").asLong()));
        String m="/api/matriculas/"+enrollment.path("id").asLong();
        assertThat(getOk(s).path("estado").asText()).isEqualTo("MATRICULA_FINALIZADA");
        getOk("/api/matriculas"); getOk(m); getOk(s+"/historial"); getOk(m+"/historial");
        putOk(m,data("estado","RETIRADA","version",getOk(m).path("version").asLong(),"motivo","Traslado de colegio"));
        assertThat(getOk(m).path("estado").asText()).isEqualTo("RETIRADA");
        request("DELETE",s,null,422); request("DELETE",m,null,422);
    }

    @Test void rechazaOCancelaConMotivoYConservaLaHistoria() throws Exception {
        String rejected=nuevaSolicitud(); estado(rejected,"ENVIADA",null,null); estado(rejected,"EN_REVISION",null,null);
        estado(rejected,"RECHAZADA",null,"Requisitos incumplidos");
        assertThat(getOk(rejected).path("motivoRechazo").asText()).isEqualTo("Requisitos incumplidos");
        String cancelled=nuevaSolicitud(); estado(cancelled,"CANCELADA","Retiro voluntario",null);
        assertThat(getOk(cancelled).path("estado").asText()).isEqualTo("CANCELADA");
    }
}
