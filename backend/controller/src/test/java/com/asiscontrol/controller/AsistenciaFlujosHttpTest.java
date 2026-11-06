package com.asiscontrol.controller;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AsistenciaAlumno;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.CodigoQRAsistencia;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Notificacion;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.TipoAula;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AsistenciaFlujosHttpTest extends ApiIntegrationSupport {
    private Rol rol(String nombre) {
        return em.createQuery("from Rol r where r.nombre=:n",Rol.class).setParameter("n",nombre).getSingleResult();
    }

    /** Asistencia de una sesión TOMADA; el registro se hace por sesión, no por fila de alumno. */
    private long asistenciaTomada(LocalDate fecha, EstadoAsistencia estado) {
        var aula=new Aula(); aula.setCodigo("AU"+key()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        var horario=new Horario(); horario.setAsignacionCurso(assignment); horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.LUNES); horario.setHoraInicio(LocalTime.of(8,0)); horario.setHoraFin(LocalTime.of(9,30));
        em.persist(horario);
        var sesion=new SesionClase(); sesion.setAsignacionCurso(assignment); sesion.setHorario(horario); sesion.setFecha(fecha);
        sesion.setHoraInicio(horario.getHoraInicio()); sesion.setHoraFin(horario.getHoraFin());
        sesion.setEstado(EstadoSesionClase.TOMADA); sesion.setDocenteRegistrador(teacher);
        sesion.setFechaHoraFinalizacion(fecha.atTime(9,30)); em.persist(sesion);
        var asistencia=new AsistenciaAlumno(); asistencia.setAlumno(student); asistencia.setAsignacionCurso(assignment);
        asistencia.setSesionClase(sesion); asistencia.setDocenteRegistrador(teacher); asistencia.setFechaRegistro(fecha);
        asistencia.setHoraRegistro(LocalTime.of(8,0)); asistencia.setEstadoAsistencia(estado); em.persist(asistencia);
        em.flush();
        return asistencia.getId();
    }

    @Test void consultaAsistenciasRegistradasPorSesion() throws Exception {
        long id=asistenciaTomada(LocalDate.now(),EstadoAsistencia.PRESENTE);
        assertThat(getOk("/api/asistencias/alumnos/"+id).path("estado").asText()).isEqualTo("PRESENTE");
        assertThat(getOk("/api/asistencias/alumnos?alumnoId="+student.getId()).path("content")).hasSize(1);
        request("GET","/api/asistencias/alumnos/"+Long.MAX_VALUE,null,404);
    }

    @Test void justificaAusenciaObservaSubsanaYApruebaConTrazabilidad() throws Exception {
        long a=asistenciaTomada(LocalDate.now(),EstadoAsistencia.INASISTENCIA);
        var j=apoderado("POST","/api/justificaciones-asistencia",data("asistenciaAlumnoId",a,"motivo","Cita medica","archivoIds",Set.of()),201);
        String p="/api/justificaciones-asistencia/"+j.path("id").asLong();
        getOk("/api/justificaciones-asistencia"); getOk(p);
        request("POST",p+"/iniciar-revision",data("version",version(p)),200);
        request("POST",p+"/observar",data("version",version(p),"observacion","Adjuntar sustento"),200);
        apoderado("POST",p+"/subsanar",data("motivo","Sustento entregado","archivoIds",Set.of(),"version",version(p)),200);
        request("POST",p+"/iniciar-revision",data("version",version(p)),200);
        request("POST",p+"/aprobar",data("version",version(p),"observacion","Conforme"),200);
        assertThat(getOk("/api/asistencias/alumnos/"+a).path("estado").asText())
                .isEqualTo("INASISTENCIA_JUSTIFICADA");
        apoderado("POST",p+"/cancelar",null,422);
    }

    private JsonNode apoderado(String method,String path,Object body,int expected) throws Exception {
        return comoRol("APODERADO",guardian.getPersona(),method,path,body,expected);
    }
    private long version(String ruta) throws Exception { return getOk(ruta).path("version").asLong(); }
    private String justificar(long asistencia) throws Exception {
        return "/api/justificaciones-asistencia/"+apoderado("POST","/api/justificaciones-asistencia",
                data("asistenciaAlumnoId",asistencia,"motivo","Cita medica","archivoIds",Set.of()),201).path("id").asLong();
    }

    @Test void soloElApoderadoJustificaYSoloInasistencias() throws Exception {
        long a=asistenciaTomada(LocalDate.now(),EstadoAsistencia.INASISTENCIA);
        var pedido=data("asistenciaAlumnoId",a,"motivo","Cita medica","archivoIds",Set.of());
        comoRol("ALUMNO",student.getPersona(),"POST","/api/justificaciones-asistencia",pedido,403);
        request("POST","/api/justificaciones-asistencia",pedido,403);
        long tardanza=asistenciaTomada(LocalDate.now().minusDays(1),EstadoAsistencia.TARDANZA);
        apoderado("POST","/api/justificaciones-asistencia",data("asistenciaAlumnoId",tardanza,"motivo","Trafico","archivoIds",Set.of()),422);
    }

    @Test void elDocenteResuelveSinIniciarRevisionYConDatosVigentes() throws Exception {
        String p=justificar(asistenciaTomada(LocalDate.now(),EstadoAsistencia.INASISTENCIA));
        var detalle=getOk(p);
        assertThat(detalle.path("estudiante").asText()).contains(", ");
        assertThat(detalle.path("curso").asText()).isEqualTo(assignment.getCurso().getNombre());
        assertThat(detalle.path("seccion").asText()).contains("Grado");
        assertThat(detalle.path("horaSesion").asText()).startsWith("08:00");
        assertThat(detalle.path("estadoAsistencia").asText()).isEqualTo("INASISTENCIA");
        long vieja=detalle.path("version").asLong()+1;
        request("POST",p+"/aprobar",data("version",vieja),409);
        request("POST",p+"/rechazar",data("version",version(p)),422);
        var aprobada=request("POST",p+"/aprobar",data("version",version(p)),200);
        assertThat(aprobada.path("estado").asText()).isEqualTo("APROBADA");
        assertThat(aprobada.path("docenteRevisorId").asLong()).isEqualTo(teacher.getId());
    }

    @Test void avisaAlApoderadoDelResultado() throws Exception {
        var acceso=new AccesoUsuario(); acceso.setPersona(guardian.getPersona());
        acceso.setRol(rol("APODERADO")); acceso.setUsername("apo"+key()); acceso.setCorreo("apo"+key()+"@example.test");
        acceso.setContrasenaHash("x"); em.persist(acceso); em.flush();
        String p=justificar(asistenciaTomada(LocalDate.now(),EstadoAsistencia.INASISTENCIA));
        request("POST",p+"/rechazar",data("version",version(p),"observacion","Sin sustento"),200);
        var aviso=em.createQuery("from Notificacion n where n.destinatario.id=:id",Notificacion.class)
                .setParameter("id",acceso.getId()).getSingleResult();
        assertThat(aviso.getTitulo()).isEqualTo("Justificación rechazada");
        assertThat(aviso.getMensaje()).contains("Sin sustento");
    }

    @Test void laTablaDelApoderadoTraeCursoYJustificacionYUnaCanceladaSeReenvia() throws Exception {
        long a=asistenciaTomada(LocalDate.now(),EstadoAsistencia.INASISTENCIA);
        String p=justificar(a);
        var fila=apoderado("GET","/api/asistencias/alumnos?alumnoId="+student.getId(),null,200).path("content").get(0);
        assertThat(fila.path("curso").asText()).isEqualTo(assignment.getCurso().getNombre());
        assertThat(fila.path("estadoJustificacion").asText()).isEqualTo("ENVIADA");
        assertThat(fila.path("motivoJustificacion").asText()).isEqualTo("Cita medica");
        apoderado("POST",p+"/cancelar",null,200);
        assertThat(justificar(a)).isEqualTo(p);
        apoderado("POST","/api/justificaciones-asistencia",data("asistenciaAlumnoId",a,"motivo","Otra","archivoIds",Set.of()),409);
    }

    @Test void permiteRechazarOCancelarYNoJustificaPresencia() throws Exception {
        for(int i=0;i<2;i++) {
            long a=asistenciaTomada(LocalDate.now().minusDays(i),EstadoAsistencia.INASISTENCIA);
            String p=justificar(a);
            if(i==0) apoderado("POST",p+"/cancelar",null,200);
            else {
                request("POST",p+"/iniciar-revision",data("version",version(p)),200);
                request("POST",p+"/rechazar",data("version",version(p),"observacion","Sin sustento"),200);
            }
        }
        long present=asistenciaTomada(LocalDate.now().minusDays(3),EstadoAsistencia.PRESENTE);
        apoderado("POST","/api/justificaciones-asistencia",data("asistenciaAlumnoId",present,"motivo","No corresponde","archivoIds",Set.of()),422);
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
