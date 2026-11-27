package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SustentoInformeHttpTest extends ApiIntegrationSupport {
    @Value("${app.storage.path}") String storage;
    PeriodoAcademico periodo;
    Evaluacion evaluacion;
    InformeProgreso informe;
    DetalleInformeProgreso detalle;
    EvidenciaPedagogica evidencia;
    ArchivoAdjunto archivo;
    Path ruta;
    final byte[] contenido = "%PDF-1.4\nSustento de prueba".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeEach void prepararSustento() throws Exception {
        periodo = new PeriodoAcademico(); periodo.setAnioAcademico(year); periodo.setNombre("Primero"); periodo.setOrden(1);
        periodo.setFechaInicio(LocalDate.of(2090,3,1)); periodo.setFechaFin(LocalDate.of(2090,4,30)); em.persist(periodo);
        evaluacion = prueba();
        var nota = new Calificacion(); nota.setAlumno(student); nota.setEvaluacion(evaluacion); nota.setCompetencia(competence);
        nota.setNivelLogro(NivelLogro.A); nota.setFechaRegistro(Instant.now()); em.persist(nota);
        informe = new InformeProgreso(); informe.setAlumno(student); informe.setAsignacionCurso(assignment);
        informe.setPeriodoAcademico(periodo); informe.setDocente(teacher); informe.setEstado(EstadoInformeProgreso.PUBLICADA);
        informe.setFechaPublicacion(Instant.now()); em.persist(informe);
        detalle = new DetalleInformeProgreso(); detalle.setInforme(informe); detalle.setEvaluacion(evaluacion);
        detalle.setCompetencia(competence); detalle.setCalificacion(nota); detalle.setNombreCompetencia(competence.getNombre());
        detalle.setTituloEvaluacion(evaluacion.getTitulo()); detalle.setFechaEvaluacion(evaluacion.getFechaEvaluacion());
        detalle.setNivelLogro(NivelLogro.A); detalle.setPonderacionPorcentaje(BigDecimal.TEN); em.persist(detalle);
        archivo = new ArchivoAdjunto(); archivo.setNombreOriginal("sustento.pdf"); archivo.setNombreAlmacenado(key()+".pdf");
        archivo.setRutaArchivo(archivo.getNombreAlmacenado()); archivo.setTipoMime("application/pdf");
        archivo.setTamanioBytes(contenido.length); archivo.setSha256("0".repeat(64)); archivo.setCargadoPor(teacher.getPersona()); em.persist(archivo);
        ruta = Path.of(storage).resolve(archivo.getRutaArchivo()); Files.createDirectories(ruta.getParent()); Files.write(ruta,contenido);
        evidencia = evidencia(true); evidencia.setArchivo(archivo); em.flush();
    }
    @AfterEach void limpiarArchivo() throws Exception { if(ruta != null) Files.deleteIfExists(ruta); }

    @Test void exponeIdsRealesYConsultaYDescargaParaLosCuatroRoles() throws Exception {
        String avance = "/api/calificaciones/avance?idAlumno="+student.getId()+"&idAsignacionCurso="+assignment.getId()+"&idPeriodoAcademico="+periodo.getId();
        for(var usuario : List.of(asAdmin(), como("DOCENTE",teacher.getPersona()), como("ALUMNO",student.getPersona()), como("APODERADO",guardian.getPersona()))) {
            mvc.perform(get(avance).with(usuario)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.filas[0].idInforme").value(informe.getId()))
                    .andExpect(jsonPath("$.filas[0].idDetalleInforme").value(detalle.getId()))
                    .andExpect(jsonPath("$.filas[0].idEvaluacion").value(evaluacion.getId()))
                    .andExpect(jsonPath("$.filas[0].idCompetencia").value(competence.getId()))
                    .andExpect(jsonPath("$.filas[0].idCalificacion").value(detalle.getCalificacion().getId()))
                    .andExpect(jsonPath("$.filas[0].tieneSustento").value(true));
            mvc.perform(get(url()).with(usuario)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.evidencias.length()").value(1))
                    .andExpect(jsonPath("$.evidencias[0].idEvidencia").value(evidencia.getId()))
                    .andExpect(jsonPath("$.evidencias[0].archivo.idArchivo").value(archivo.getId()))
                    .andExpect(jsonPath("$.evidencias[0].archivo.urlDescarga").value(url()+"/"+evidencia.getId()+"/archivo"));
            mvc.perform(get(url()+"/"+evidencia.getId()).with(usuario)).andExpect(status().isOk());
            mvc.perform(get(url()+"/"+evidencia.getId()+"/archivo").with(usuario)).andExpect(status().isOk())
                    .andExpect(content().bytes(contenido)).andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Cache-Control","no-store"));
        }
    }

    @Test void parcialSinLibretaConsultaSoloSustentoExactoYAutorizaPorRol() throws Exception {
        em.remove(detalle);
        em.remove(informe);
        var ajena = evidencia(false);
        ajena.setArchivo(archivo);
        em.flush();
        String base = "/api/calificaciones/informes/parcial";
        String contexto = "?idAlumno=" + student.getId() + "&idAsignacionCurso=" + assignment.getId()
                + "&idPeriodoAcademico=" + periodo.getId() + "&idEvaluacion=" + evaluacion.getId()
                + "&idCompetencia=" + competence.getId();
        var docente = como("DOCENTE", teacher.getPersona());
        mvc.perform(get(base + contexto).with(docente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].idDetalleInforme").value(0))
                .andExpect(jsonPath("$.filas[0].tieneSustento").value(true));
        mvc.perform(get(base + "/evidencias" + contexto).with(docente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidencias.length()").value(1))
                .andExpect(jsonPath("$.evidencias[0].idEvidencia").value(evidencia.getId()));
        String descarga = base + "/evidencias/" + evidencia.getId() + "/archivo";
        mvc.perform(get(descarga + contexto).with(docente)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(contenido));
        mvc.perform(get(base + "/evidencias/" + ajena.getId() + "/archivo" + contexto).with(docente))
                .andExpect(status().isNotFound());
        mvc.perform(get(descarga + contexto.replaceFirst("idCompetencia=[0-9]+", "idCompetencia=999999")).with(docente))
                .andExpect(status().isNotFound());
        for (var usuario : List.of(como("ALUMNO", student.getPersona()), como("APODERADO", guardian.getPersona()),
                como("DOCENTE", person()))) {
            mvc.perform(get(base + "/evidencias" + contexto).with(usuario)).andExpect(status().isForbidden());
            mvc.perform(get(descarga + contexto).with(usuario)).andExpect(status().isForbidden());
        }
        evidencia.setActivo(false);
        em.flush();
        mvc.perform(get(base + contexto).with(docente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].tieneSustento").value(false));
    }

    @Test void altaSinVinculoNoSustentaLaFilaYElPutIdempotenteLaHaceVisible() throws Exception {
        evidencia.setActivo(false);
        var docente = jwt().jwt(j -> j.claim("personaId", teacher.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                        new SimpleGrantedAuthority("CALIFICACIONES_LEER"), new SimpleGrantedAuthority("CALIFICACIONES_ESCRIBIR"));
        String base = "/api/evidencias-pedagogicas";
        var alta = json.writeValueAsString(data("asignacionId", assignment.getId(), "alumnoId", student.getId(),
                "competenciaId", competence.getId(), "periodoId", periodo.getId(), "descripcion", "Producto de la evaluación"));
        var response = mvc.perform(post(base).with(docente).contentType("application/json").content(alta))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.evaluaciones.length()").value(0)).andReturn();
        var id = json.readTree(response.getResponse().getContentAsString()).path("id").asLong();
        String avance = "/api/calificaciones/avance?idAlumno=" + student.getId() + "&idAsignacionCurso="
                + assignment.getId() + "&idPeriodoAcademico=" + periodo.getId();
        mvc.perform(get(avance).with(docente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].tieneSustento").value(false));
        mvc.perform(get(url()).with(docente)).andExpect(status().isOk()).andExpect(jsonPath("$.evidencias.length()").value(0));

        String vinculo = base + "/" + id + "/evaluaciones/" + evaluacion.getId();
        mvc.perform(put(vinculo).with(docente)).andExpect(status().isNoContent());
        mvc.perform(put(vinculo).with(docente)).andExpect(status().isNoContent());
        mvc.perform(get(base + "/" + id).with(docente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones.length()").value(1))
                .andExpect(jsonPath("$.evaluaciones[0].id").value(evaluacion.getId()))
                .andExpect(jsonPath("$.evaluaciones[0].titulo").value(evaluacion.getTitulo()));
        mvc.perform(get(url()).with(docente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidencias.length()").value(1))
                .andExpect(jsonPath("$.evidencias[0].idEvidencia").value(id))
                .andExpect(jsonPath("$.evidencias[0].descripcion").value("Producto de la evaluación"));
    }

    @Test void soloIncluyeVinculosExactosYNoAceptaSustituirIds() throws Exception {
        var ajenas = new ArrayList<EvidenciaPedagogica>();
        ajenas.add(evidencia(false)); // Misma competencia, sin vinculo a la evaluacion.
        var otraPrueba = evidencia(false); otraPrueba.getEvaluaciones().add(prueba()); ajenas.add(otraPrueba);
        var otroAlumno = new Alumno(); otroAlumno.setPersona(person()); otroAlumno.setCodigoAlumno(key()); em.persist(otroAlumno);
        var porAlumno = evidencia(true); porAlumno.setAlumno(otroAlumno); ajenas.add(porAlumno);
        var otraCompetencia = new Competencia(); otraCompetencia.setCodigo(key()); otraCompetencia.setNombre(competence.getNombre()); em.persist(otraCompetencia);
        var porCompetencia = evidencia(true); porCompetencia.setCompetencia(otraCompetencia); ajenas.add(porCompetencia);
        var otroPeriodo = new PeriodoAcademico(); otroPeriodo.setAnioAcademico(year); otroPeriodo.setNombre("Otro"); otroPeriodo.setOrden(2);
        otroPeriodo.setFechaInicio(periodo.getFechaInicio()); otroPeriodo.setFechaFin(periodo.getFechaFin()); em.persist(otroPeriodo);
        var porPeriodo = evidencia(true); porPeriodo.setPeriodo(otroPeriodo); ajenas.add(porPeriodo);
        var curso = new Curso(); curso.setCodigo(key()); curso.setNombre("Otro curso"); curso.setHorasSemanales(2);
        curso.setGradoAcademico(grade); em.persist(curso);
        var otroCurso = new AsignacionCurso(); otroCurso.setCurso(curso); otroCurso.setSeccion(section); otroCurso.setDocente(teacher); em.persist(otroCurso);
        var porCurso = evidencia(true); porCurso.setAsignacion(otroCurso); ajenas.add(porCurso);
        var inactiva = evidencia(true); inactiva.setActivo(false); ajenas.add(inactiva); em.flush();
        mvc.perform(get(url()).with(como("ALUMNO",student.getPersona()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidencias.length()").value(1));
        for(var ajena : ajenas) for(var sufijo : List.of("", "/archivo")) {
            mvc.perform(get(url()+"/"+ajena.getId()+sufijo).with(asAdmin())).andExpect(status().isNotFound());
        }
        mvc.perform(get(url().replace("/informes/"+informe.getId(),"/informes/999999")).with(asAdmin())).andExpect(status().isNotFound());
        evidencia.setActivo(false); em.flush();
        mvc.perform(get(url()).with(asAdmin())).andExpect(status().isOk()).andExpect(jsonPath("$.evidencias.length()").value(0));
    }

    @Test void deniegaAjenosSinPermisoYVinculoRevocadoEnConsultaYArchivo() throws Exception {
        for(String rol : List.of("ALUMNO","APODERADO","DOCENTE")) for(String sufijo : List.of("", "/"+evidencia.getId(), "/"+evidencia.getId()+"/archivo")) {
            mvc.perform(get(url()+sufijo).with(como(rol,person()))).andExpect(status().isForbidden());
        }
        mvc.perform(get(url()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isForbidden());
        em.createQuery("update AlumnoApoderado v set v.activo=false where v.alumno = :a").setParameter("a",student).executeUpdate();
        mvc.perform(get(url()).with(como("APODERADO",guardian.getPersona()))).andExpect(status().isForbidden());
        mvc.perform(get(url()+"/"+evidencia.getId()+"/archivo").with(como("APODERADO",guardian.getPersona()))).andExpect(status().isForbidden());
    }

    @Test void borradorSoloDocenteYAdminYAdjuntoInactivoNoSeExpone() throws Exception {
        informe.setEstado(EstadoInformeProgreso.BORRADOR); informe.setFechaPublicacion(null); em.flush();
        for(var familiar : List.of(como("ALUMNO",student.getPersona()),como("APODERADO",guardian.getPersona()))) {
            mvc.perform(get(url()).with(familiar)).andExpect(status().isForbidden());
            mvc.perform(get(url()+"/"+evidencia.getId()+"/archivo").with(familiar)).andExpect(status().isForbidden());
        }
        mvc.perform(get(url()).with(como("DOCENTE",teacher.getPersona()))).andExpect(status().isOk());
        archivo.setActivo(false); em.flush();
        mvc.perform(get(url()).with(asAdmin())).andExpect(status().isOk()).andExpect(jsonPath("$.evidencias[0].archivo").isEmpty());
        mvc.perform(get(url()+"/"+evidencia.getId()+"/archivo").with(asAdmin())).andExpect(status().isNotFound());
        assignment.setActivo(false); em.flush();
        mvc.perform(get(url()).with(asAdmin())).andExpect(status().isForbidden());
    }

    private Evaluacion prueba() {
        var e=new Evaluacion(); e.setAsignacionCurso(assignment); e.setTitulo("Practica"); e.setTipoEvaluacion(TipoEvaluacion.EXAMEN);
        e.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL); e.setFechaEvaluacion(periodo.getFechaInicio());
        e.setPonderacionPorcentaje(BigDecimal.TEN); e.setEstado(EstadoEvaluacion.PUBLICADA); e.setCompetencias(Set.of(competence)); em.persist(e); return e;
    }
    private EvidenciaPedagogica evidencia(boolean vinculada) {
        var e=new EvidenciaPedagogica(); e.setAsignacion(assignment); e.setAlumno(student); e.setCompetencia(competence);
        e.setPeriodo(periodo); e.setAutor(teacher.getPersona()); e.setDescripcion("Sustento exacto");
        if(vinculada) e.getEvaluaciones().add(evaluacion); em.persist(e); return e;
    }
    private String url(){return "/api/calificaciones/informes/"+informe.getId()+"/filas/"+detalle.getId()+"/evidencias";}
    private RequestPostProcessor como(String rol, Persona persona){return jwt().jwt(j -> j.claim("personaId",persona.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_"+rol),new SimpleGrantedAuthority("CALIFICACIONES_LEER"));}
}
