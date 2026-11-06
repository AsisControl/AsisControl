// asistencia alumnos
package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.FinalizarAsistenciaRequest;
import com.asiscontrol.dto.asistencia.AsistenciaSesionDtos.MarcaAsistenciaRequest;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Persona;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoAsistencia;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.entity.planificacion.AnioAcademico;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.service.asistencia.AsistenciaSesionService;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Sin transacción de prueba: cada finalización confirma o revierte por su cuenta. */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:asiscontrol_asistencia_concurrencia;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@ActiveProfiles("test")
class AsistenciaConcurrenciaIntegrationTest {

    @Autowired AsistenciaSesionService asistencia;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transacciones;

    private Docente docente;
    private Alumno alumno;
    private SesionClase sesion;

    @Test
    void dosFinalizacionesALaVezDejanUnaSolaAsistencia() throws Exception {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> crearDatos());
        CountDownLatch listos = new CountDownLatch(2), inicio = new CountDownLatch(1);
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            List<Future<String>> resultados = List.of(
                    hilos.submit(() -> finalizar(listos, inicio)),
                    hilos.submit(() -> finalizar(listos, inicio)));
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            assertThat(List.of(
                            resultados.get(0).get(20, TimeUnit.SECONDS),
                            resultados.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("TOMADA", "VERSION_DESACTUALIZADA");
        }
        assertThat(em.createQuery("select count(a) from AsistenciaAlumno a where a.sesionClase.id = :s", Long.class)
                .setParameter("s", sesion.getId())
                .getSingleResult()).isEqualTo(1);
    }

    private String finalizar(CountDownLatch listos, CountDownLatch inicio) throws InterruptedException {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("docente")
                .claim("personaId", docente.getPersona().getId()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_DOCENTE"))));
        listos.countDown();
        if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la prueba");
        try {
            return asistencia.finalizar(sesion.getId(), new FinalizarAsistenciaRequest(
                            List.of(new MarcaAsistenciaRequest(alumno.getId(), EstadoAsistencia.PRESENTE)),
                            sesion.getVersion()))
                    .estado().name();
        } catch (ConflictException error) {
            return error.getCode();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void crearDatos() {
        var anio = new AnioAcademico(); anio.setAnio(2090); anio.setFechaInicio(LocalDate.of(2090, 1, 1));
        anio.setFechaFin(LocalDate.of(2090, 12, 31)); anio.setEstado(EstadoAnioAcademico.ACTIVO); em.persist(anio);
        docente = new Docente(); docente.setPersona(persona()); docente.setCodigoDocente("DOC" + clave());
        docente.setFechaIngreso(LocalDate.of(2020, 1, 1)); docente.setEspecialidad(Especialidad.MATEMATICA); em.persist(docente);
        alumno = new Alumno(); alumno.setPersona(persona()); alumno.setCodigoAlumno("EST" + clave()); em.persist(alumno);
        var seccion = new Seccion(); seccion.setNombre("A"); seccion.setGrado(1); seccion.setAnioAcademico(2090);
        seccion.setCapacidadMaxima(30); em.persist(seccion);
        var curso = new Curso(); curso.setCodigo("MAT" + clave()); curso.setNombre("Matematica"); curso.setHorasSemanales(4); em.persist(curso);
        var asignacion = new AsignacionCurso(); asignacion.setCurso(curso); asignacion.setDocente(docente);
        asignacion.setSeccion(seccion); em.persist(asignacion);
        var solicitud = new SolicitudMatricula(); solicitud.setAlumno(alumno); solicitud.setSeccion(seccion);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA); em.persist(solicitud);
        var matricula = new Matricula(); matricula.setAlumno(alumno); matricula.setSeccion(seccion);
        matricula.setSolicitudMatricula(solicitud); matricula.setCodigoMatricula("MAT" + clave());
        matricula.setAnioAcademico(2090); matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA); em.persist(matricula);
        var aula = new Aula(); aula.setCodigo("AU" + clave()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        var horario = new Horario(); horario.setAsignacionCurso(asignacion); horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.LUNES); horario.setHoraInicio(LocalTime.of(8, 0)); horario.setHoraFin(LocalTime.of(10, 0));
        em.persist(horario);
        sesion = new SesionClase(); sesion.setAsignacionCurso(asignacion); sesion.setHorario(horario);
        sesion.setFecha(LocalDate.now(ZoneId.of("America/Lima"))); sesion.setHoraInicio(LocalTime.of(8, 0));
        sesion.setHoraFin(LocalTime.of(10, 0)); sesion.setEstado(EstadoSesionClase.PENDIENTE); em.persist(sesion);
    }

    private Persona persona() {
        String clave = clave();
        var persona = new Persona(); persona.setNombres("Persona"); persona.setApellidoPaterno("Prueba");
        persona.setApellidoMaterno("Sistema"); persona.setTipoDocumento(TipoDocumento.PASAPORTE); persona.setNumeroDocumento(clave);
        persona.setSexo(Sexo.PREFIERE_NO_INDICAR); persona.setFechaNacimiento(LocalDate.of(2008, 1, 1));
        persona.setCorreo(clave + "@example.test"); em.persist(persona);
        return persona;
    }

    private String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
