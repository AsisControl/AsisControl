package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Datos aislados y transacción revertida por cada escenario funcional HTTP. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class ApiIntegrationSupport {
    @Autowired protected EntityManager em;
    @Autowired WebApplicationContext context;
    protected MockMvc mvc;
    protected final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    protected Alumno student;
    protected Docente teacher;
    protected Apoderado guardian;
    protected Seccion section;
    protected Curso course;
    protected Competencia competence;
    protected AsignacionCurso assignment;
    protected AccesoUsuario admin;
    protected AnioAcademico year;
    private List<org.springframework.security.core.GrantedAuthority> authorities;

    @BeforeEach void fixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
        for (var p : em.createQuery("from Permiso", Permiso.class).getResultList())
            authorities.add(new SimpleGrantedAuthority(p.getCodigo()));
        authorities.add(new SimpleGrantedAuthority("TAREAS_ENTREGAR"));
        var role = em.createQuery("from Rol r where r.nombre='ADMINISTRADOR'", Rol.class).getSingleResult();
        admin = new AccesoUsuario(); admin.setPersona(person()); admin.setRol(role);
        admin.setUsername("admin" + key()); admin.setCorreo(key() + "@example.test");
        admin.setContrasenaHash("hash-exclusivo-de-prueba"); admin.setEstado(EstadoAcceso.ACTIVO);
        admin.setRequiereCambioContrasena(false); em.persist(admin);
        student = new Alumno(); student.setPersona(person()); student.setCodigoAlumno("EST" + key()); em.persist(student);
        teacher = new Docente(); teacher.setPersona(person()); teacher.setCodigoDocente("DOC" + key());
        teacher.setFechaIngreso(LocalDate.now().minusYears(1)); teacher.setEspecialidad(Especialidad.MATEMATICA); em.persist(teacher);
        guardian = new Apoderado(); guardian.setPersona(person()); em.persist(guardian);
        var link = new AlumnoApoderado(); link.setAlumno(student); link.setApoderado(guardian);
        link.setParentesco(Parentesco.MADRE); link.setPrincipal(true); em.persist(link);
        year = new AnioAcademico(); year.setAnio(2090); year.setFechaInicio(LocalDate.of(2090,1,1));
        year.setFechaFin(LocalDate.of(2090,12,31)); year.setEstado(EstadoAnioAcademico.ACTIVO);
        year.setAdmisionAbierta(true); em.persist(year);
        var grade = new GradoAcademico(); grade.setAnioAcademico(year); grade.setNumero(1);
        grade.setCapacidadDefault(30); em.persist(grade);
        section = new Seccion(); section.setNombre("A"); section.setGrado(1); section.setAnioAcademico(2090);
        section.setCapacidadMaxima(30); section.setDocenteTutor(teacher); em.persist(section);
        course = new Curso(); course.setCodigo("MAT" + key()); course.setNombre("Matematica");
        course.setHorasSemanales(4); em.persist(course);
        competence = new Competencia(); competence.setCodigo("COMP" + key()); competence.setNombre("Resuelve problemas"); em.persist(competence);
        var courseCompetence = new CursoCompetencia();
        courseCompetence.setCurso(course); courseCompetence.setCompetencia(competence);
        courseCompetence.setOrden(1); em.persist(courseCompetence);
        assignment = new AsignacionCurso(); assignment.setCurso(course); assignment.setDocente(teacher);
        assignment.setSeccion(section); em.persist(assignment);
        var request = new SolicitudMatricula(); request.setAlumno(student); request.setSeccion(section);
        request.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA); em.persist(request);
        var enrollment = new Matricula(); enrollment.setAlumno(student); enrollment.setSeccion(section);
        enrollment.setSolicitudMatricula(request); enrollment.setCodigoMatricula("MAT" + key());
        enrollment.setAnioAcademico(2090); enrollment.setFechaMatricula(Instant.now());
        enrollment.setEstado(EstadoMatricula.ACTIVA); em.persist(enrollment);
        em.flush();
    }

    protected String key() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    protected Persona person() {
        String k = key(); var p = new Persona(); p.setNombres("Persona"); p.setApellidoPaterno("Prueba");
        p.setApellidoMaterno("Sistema"); p.setTipoDocumento(TipoDocumento.PASAPORTE); p.setNumeroDocumento(k);
        p.setSexo(Sexo.PREFIERE_NO_INDICAR); p.setFechaNacimiento(LocalDate.of(2008,1,1));
        p.setCorreo(k + "@example.test"); em.persist(p); return p;
    }
    protected Map<String,Object> data(Object... entries) {
        var result = new LinkedHashMap<String,Object>();
        for (int i=0;i<entries.length;i+=2) result.put((String)entries[i], entries[i+1]);
        return result;
    }
    protected JsonNode request(String method, String path, Object body, int expected) throws Exception {
        MockHttpServletRequestBuilder builder = switch(method) {
            case "POST" -> post(path); case "PUT" -> put(path); case "DELETE" -> delete(path);
            case "PATCH" -> patch(path); default -> get(path);
        };
        builder.with(asAdmin());
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }
    protected JsonNode postOk(String path,Object body) throws Exception { return request("POST",path,body,201); }
    protected JsonNode getOk(String path) throws Exception { return request("GET",path,null,200); }
    protected JsonNode putOk(String path,Object body) throws Exception { return request("PUT",path,body,200); }
    protected void deleteOk(String path) throws Exception { request("DELETE",path,null,204); }

    protected org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor asAdmin() {
        return jwt().jwt(j -> j.subject(admin.getUsername()).claim("personaId",admin.getPersona().getId())
                .claim("usuarioId",admin.getId()).claim("requiereCambioContrasena",false)).authorities(authorities);
    }
}
