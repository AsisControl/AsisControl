package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.Matricula;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MatriculaFlujosHttpTest extends ApiIntegrationSupport {
    @Test void cambiaSeccionDeMatriculaActivaConVacanteEHistorial() throws Exception {
        Long id = em.createQuery("select m.id from Matricula m where m.alumno.id=:alumno", Long.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        String path = "/api/matriculas/" + id;
        Seccion destino = new Seccion();
        destino.setNombre("B"); destino.setGrado(1); destino.setAnioAcademico(2090);
        destino.setCapacidadMaxima(1); em.persist(destino); em.flush();
        var updated = putOk(path + "/seccion", data("seccionId", destino.getId(),
                "version", getOk(path).path("version").asLong(), "motivo", "Redistribución de aula"));
        assertThat(updated.path("seccionId").asLong()).isEqualTo(destino.getId());
        assertThat(updated.path("seccion").asText()).isEqualTo("B");
        assertThat(getOk(path).path("seccionId").asLong()).isEqualTo(destino.getId());
        var history = getOk(path + "/historial");
        assertThat(history.get(0).path("accion").asText()).isEqualTo("CAMBIAR_SECCION_MATRICULA");
        assertThat(history.get(0).path("campos").get(0).path("anterior").asText()).isEqualTo("2090 · 1° A");
        assertThat(history.get(0).path("campos").get(0).path("nuevo").asText()).isEqualTo("2090 · 1° B");
        request("PUT", path + "/seccion", data("seccionId", section.getId(),
                "version", updated.path("version").asLong(), "motivo", "Retorno"), 200);
    }

    @Test void rechazaTrasladoSinVacanteYOtroGrado() throws Exception {
        Matricula matricula = em.createQuery("from Matricula m where m.alumno.id=:alumno", Matricula.class)
                .setParameter("alumno", student.getId()).getSingleResult();
        Seccion llena = new Seccion(); llena.setNombre("Llena"); llena.setGrado(1);
        llena.setAnioAcademico(2090); llena.setCapacidadMaxima(0); em.persist(llena);
        Seccion otroGrado = new Seccion(); otroGrado.setNombre("Segundo"); otroGrado.setGrado(2);
        otroGrado.setAnioAcademico(2090); otroGrado.setCapacidadMaxima(30); em.persist(otroGrado);
        em.flush();
        String path = "/api/matriculas/" + matricula.getId() + "/seccion";
        request("PUT", path, data("seccionId", llena.getId(), "version", matricula.getVersion(),
                "motivo", "Traslado"), 422);
        request("PUT", path, data("seccionId", otroGrado.getId(), "version", matricula.getVersion(),
                "motivo", "Traslado"), 422);
        assertThat(getOk("/api/matriculas/" + matricula.getId()).path("seccionId").asLong())
                .isEqualTo(section.getId());
    }
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
        com.asiscontrol.support.TarifasTestSupport.configurar(em, 2090, 1);
        String s=nuevaSolicitud();
        getOk("/api/solicitudes-matricula");
        putOk(s,data("observaciones","Informacion actualizada","version",getOk(s).path("version").asLong()));
        estado(s,"ENVIADA",null,null); estado(s,"EN_REVISION",null,null);
        estado(s,"OBSERVADA","Falta documento",null);
        estado(s,"ENVIADA",null,null); estado(s,"EN_REVISION",null,null);
        putOk(s,data("seccionId",section.getId(),"version",getOk(s).path("version").asLong()));
        var reviewed=getOk(s);
        var plan=getOk("/api/configuracion-pagos/planes/solicitudes/"+reviewed.path("id").asLong()+"/preview");
        var enrollment=postOk("/api/matriculas",data("solicitudMatriculaId",reviewed.path("id").asLong(),"versionSolicitud",reviewed.path("version").asLong(),"firmaPlan",plan.path("firmaPlan").asText()));
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
