package com.asiscontrol.controller;

import com.asiscontrol.entity.RegistroError;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdministracionFlujosHttpTest extends ApiIntegrationSupport {
    @Autowired NotificacionService notificaciones;
    @Autowired DiaNoLectivoService calendario;

    @Test void configuraAnioPeriodosGradosSeccionesYDiasNoLectivos() throws Exception {
        String base="/api/configuracion-academica";
        postOk(base+"/anios",data("anio",2091,"fechaInicio","2091-01-01","fechaFin","2091-12-31"));
        getOk(base+"/anios"); getOk(base+"/anios/2091");
        var config=postOk(base+"/anios/2091/periodos",data("nombre","Bimestre I","orden",1,"fechaInicio","2091-03-01","fechaFin","2091-05-31"));
        long pid=config.path("periodos").get(0).path("id").asLong();
        var grado=postOk(base+"/anios/2091/grados",data("numero",1,"capacidadDefault",30));
        long gid=grado.path("grados").get(0).path("id").asLong();
        putOk(base+"/grados/"+gid,data("activo",true,"capacidadDefault",40,"version",0,"motivo","Ampliacion"));
        var section=postOk("/api/secciones",data("nombre","B","grado",1,"anioAcademico",2091,"capacidadMaxima",30,"docenteTutorId",teacher.getId()));
        String sp="/api/secciones/"+section.path("id").asLong();
        getOk("/api/secciones"); getOk(sp);
        putOk(sp,data("nombre","B","grado",1,"anioAcademico",2091,"capacidadMaxima",35,"docenteTutorId",teacher.getId(),"version",0,"motivo","Ajuste de aforo"));
        var updated=getOk(sp);
        putOk(sp+"/estado",data("activo",false,"version",updated.path("version").asLong(),"motivo","Seccion sin uso"));
        request("DELETE",sp,null,422);
        var day=postOk(base+"/anios/2091/dias-no-lectivos",data("fecha","2091-07-28","descripcion","Fiestas Patrias"));
        getOk(base+"/anios/2091/dias-no-lectivos");
        request("PATCH",base+"/dias-no-lectivos/"+day.path("id").asLong(),data("descripcion","Feriado nacional","version",0),200);
        assertThat(calendario.esNoLectivo(2091,LocalDate.of(2091,7,28))).isTrue();
        request("POST",base+"/anios/2091/dias-no-lectivos",data("fecha","2091-07-28","descripcion","Duplicado"),409);
        // Se habilita de nuevo la sección para poder activar la oferta del siguiente año.
        var inactive=em.find(com.asiscontrol.entity.Seccion.class,section.path("id").asLong()); inactive.setActivo(true);
        year.setEstado(EstadoAnioAcademico.CERRADO); em.flush();
        var y=getOk(base+"/anios/2091");
        putOk(base+"/anios/2091/estado",data("estado","ACTIVO","version",y.path("version").asLong(),"motivo","Inicio escolar"));
        y=getOk(base+"/anios/2091");
        putOk(base+"/anios/2091/admision",data("abierta",true,"version",y.path("version").asLong(),"motivo","Inicio de admision"));
        putOk(base+"/periodos/"+pid+"/estado",data("estado","ACTIVO","version",0,"motivo","Inicio del bimestre"));
    }

    @Test void conservaSeguimientoDeErroresYNotificacionesPorDestinatario() throws Exception {
        var error=new RegistroError(); error.setFechaHora(Instant.now()); error.setSeveridad(SeveridadError.ERROR);
        var fechaError = error.getFechaHora().atZone(ZoneId.of("America/Lima")).toLocalDate();
        error.setComponente("Integracion"); error.setMensajeSeguro("Error controlado de prueba");
        error.setCorrelacionId("correlacion-prueba"); em.persist(error); em.flush();
        getOk("/api/auditoria/registros"); getOk("/api/auditoria/errores");
        var result=getOk("/api/auditoria/errores?estado=PENDIENTE&severidad=ERROR&componente=Integracion&correlacion=correlacion-prueba&desde="+fechaError+"&hasta="+fechaError);
        assertThat(result.path("content").size()).isGreaterThanOrEqualTo(1);
        putOk("/api/auditoria/errores/"+error.getId(),data("estado","EN_SEGUIMIENTO","observacionSeguimiento","Investigando causa","version",0));
        assertThat(getOk("/api/auditoria/errores/"+error.getId()+"/seguimientos")).hasSize(1);
        var notification=notificaciones.create(admin.getId(),"Aviso","Mensaje institucional",TipoNotificacion.values()[0],"/inicio");
        assertThat(getOk("/api/notificaciones/resumen").path("noLeidas").asInt()).isGreaterThanOrEqualTo(0);
        getOk("/api/notificaciones");
        assertThat(putOk("/api/notificaciones/"+notification.id()+"/leida",null).path("leida").asBoolean()).isTrue();
        putOk("/api/notificaciones/"+notification.id()+"/leida",null);
        request("PUT","/api/notificaciones/lectura",null,204);
        getOk("/api/dashboard");
    }

    @Test void familiaConsultaSoloSusEstudiantesYLaOfertaDisponible() throws Exception {
        var authentication=jwt().jwt(j->j.subject("apoderado-prueba").claim("personaId",guardian.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_APODERADO"),new SimpleGrantedAuthority("MATRICULA_LEER"));
        mvc.perform(get("/api/familia/estudiantes").with(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(student.getId()));
        mvc.perform(get("/api/familia/oferta").with(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].anio").value(2090));
    }

    @Test void subeConsultaDescargaYDesactivaUnArchivoDesdeHttp() throws Exception {
        byte[] content=new byte[]{0x25,0x50,0x44,0x46};
        var uploaded=mvc.perform(multipart("/api/archivos")
                        .file(new org.springframework.mock.web.MockMultipartFile("archivo","sustento.pdf","application/pdf",content))
                        .with(asAdmin())).andExpect(status().isCreated()).andReturn().getResponse();
        long id=json.readTree(uploaded.getContentAsByteArray()).path("id").asLong();
        getOk("/api/archivos/"+id);
        var download=mvc.perform(get("/api/archivos/"+id+"/descarga").with(asAdmin()))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andReturn().getResponse();
        assertThat(download.getContentAsByteArray()).isEqualTo(content);
        deleteOk("/api/archivos/"+id);
    }
}
