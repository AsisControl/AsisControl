package com.asiscontrol.controller;

import com.asiscontrol.entity.*;
import com.asiscontrol.entity.asistencia.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.service.JornadasMarcacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ReporteAsistenciaHttpTest extends ApiIntegrationSupport {
    private static final String REPORT = "/api/reportes/asistencia?tipo=";
    private final LocalDate fecha = LocalDate.of(2090, 3, 4);
    @Autowired Clock reloj;

    private SesionClase sesion(LocalDate dia, int hora, EstadoSesionClase estado) {
        var aula = new Aula(); aula.setCodigo(key()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellón de pruebas"); em.persist(aula);
        var horario = new Horario(); horario.setAsignacionCurso(assignment); horario.setAula(aula);
        horario.setDiaSemana(DiaSemana.LUNES); horario.setHoraInicio(LocalTime.of(hora, 0)); horario.setHoraFin(LocalTime.of(hora+1, 0)); em.persist(horario);
        var s = new SesionClase(); s.setAsignacionCurso(assignment); s.setHorario(horario); s.setFecha(dia);
        s.setHoraInicio(horario.getHoraInicio()); s.setHoraFin(horario.getHoraFin()); s.setEstado(estado); em.persist(s); return s;
    }
    private AsistenciaAlumno asistencia(int dia, EstadoAsistencia estado, EstadoSesionClase estadoSesion) {
        var s = sesion(fecha.plusDays(dia), 9, estadoSesion);
        var a = new AsistenciaAlumno(); a.setAlumno(student); a.setAsignacionCurso(assignment); a.setSesionClase(s);
        a.setFechaRegistro(s.getFecha()); a.setHoraRegistro(LocalTime.of(9, 1)); a.setEstadoAsistencia(estado); em.persist(a); return a;
    }
    @Test void losTotalesUsanTodoElFiltroYElDetalleRespetaEstadoYPaginacion() throws Exception {
        grade.getCursosPlan().put(course, new CursoEnPlan("Matemática y razonamiento", 4));
        asistencia(0, EstadoAsistencia.PRESENTE, EstadoSesionClase.TOMADA);
        asistencia(1, EstadoAsistencia.TARDANZA_JUSTIFICADA, EstadoSesionClase.TOMADA);
        asistencia(2, EstadoAsistencia.INASISTENCIA, EstadoSesionClase.TOMADA);
        asistencia(3, EstadoAsistencia.PRESENTE, EstadoSesionClase.BORRADOR);
        em.flush();
        var r = getOk(REPORT+"ESTUDIANTES&tamano=1&seccionId="+section.getId());
        assertThat(r.path("total").asLong()).isEqualTo(3);
        assertThat(r.path("registros")).hasSize(1);
        assertThat(r.path("registros").get(0).path("curso").asText()).isEqualTo("Matemática y razonamiento");
        assertThat(r.path("indicadores").get(1).path("porcentaje").asDouble()).isEqualTo(33.33);
        var detalle = getOk(REPORT+"ESTUDIANTES&estado=TARDANZA");
        assertThat(detalle.path("total").asLong()).isEqualTo(3);
        assertThat(detalle.path("totalDetalle").asLong()).isEqualTo(1);
        assertThat(detalle.path("registros").get(0).path("estado").asText()).isEqualTo("TARDANZA_JUSTIFICADA");
        assertThat(getOk(REPORT+"ESTUDIANTES&busqueda=persona").path("total").asLong()).isEqualTo(3);
        assertThat(getOk(REPORT+"ESTUDIANTES&personaId="+student.getPersona().getId()).path("total").asLong()).isEqualTo(3);
        assertThat(getOk(REPORT+"ESTUDIANTES&busqueda=%25").path("total").asLong()).isZero();
    }
    @Test void sinCoincidenciasNoInventaIndicadoresYValidaFiltros() throws Exception {
        var r = getOk(REPORT+"ESTUDIANTES");
        assertThat(r.path("indicadores")).isEmpty(); assertThat(r.path("registros")).isEmpty();
        request("GET", REPORT+"ESTUDIANTES&desde=2090-04-01&hasta=2090-03-01", null, 422);
        request("GET", REPORT+"ESTUDIANTES&pagina=-1", null, 422);
        request("GET", REPORT+"ESTUDIANTES&tamano=101", null, 422);
        request("GET", REPORT+"ESTUDIANTES&periodoId=9999999", null, 404);
        request("GET", REPORT+"ESTUDIANTES&seccionId=9999999", null, 404);
        request("GET", REPORT+"OTRO", null, 400);
    }
    @Test void filtraPorPeriodoYFechaSinConfundirPeriodosDelMismoNombre() throws Exception {
        asistencia(0, EstadoAsistencia.PRESENTE, EstadoSesionClase.TOMADA);
        asistencia(4, EstadoAsistencia.TARDANZA, EstadoSesionClase.TOMADA);
        var p = new PeriodoAcademico(); p.setAnioAcademico(year); p.setOrden(1); p.setNombre("Primero");
        p.setFechaInicio(fecha); p.setFechaFin(fecha.plusDays(1)); em.persist(p); em.flush();
        assertThat(getOk(REPORT+"ESTUDIANTES&periodoId="+p.getId()).path("total").asLong()).isEqualTo(1);
        assertThat(getOk(REPORT+"ESTUDIANTES&desde="+fecha.plusDays(4)+"&hasta="+fecha.plusDays(4)).path("total").asLong()).isEqualTo(1);
        assertThat(getOk("/api/reportes/asistencia/filtros").path("periodos")).hasSize(1);
    }
    @Test void unaCorreccionUs32ActualizaElReporteSinSumarLaAuditoria() throws Exception {
        var a = asistencia(0, EstadoAsistencia.INASISTENCIA, EstadoSesionClase.TOMADA); em.flush();
        long id = a.getSesionClase().getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/asistencias/sesiones/"+id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject("docente").claim("personaId", teacher.getPersona().getId()))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DOCENTE"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ASISTENCIA_LEER"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ASISTENCIA_ESCRIBIR")))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(data("version", a.getSesionClase().getVersion(),
                        "motivo", "Se verificó la presencia del estudiante", "marcas", List.of(data("alumnoId",student.getId(),"estado","PRESENTE"))))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var r = getOk(REPORT+"ESTUDIANTES");
        assertThat(r.path("total").asLong()).isEqualTo(1);
        assertThat(r.path("registros").get(0).path("estado").asText()).isEqualTo("PRESENTE");
        assertThat(comoRol("DOCENTE", teacher.getPersona(), "GET", "/api/asistencias/sesiones/"+id+"/correcciones", null, 200)).hasSize(1);
    }
    @Test void reporteInstitucionalRechazaOtrosRolesInclusoConPermisoDeLectura() throws Exception {
        for (String rol : List.of("DOCENTE", "ALUMNO", "APODERADO")) {
            comoRol(rol, teacher.getPersona(), "GET", REPORT+"DOCENTES", null, 403);
            comoRol(rol, teacher.getPersona(), "GET", "/api/reportes/asistencia/filtros", null, 403);
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(REPORT+"DOCENTES")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void soloCuentaJornadasCerradasYLaMarcacionResuelveLaOmisionSinDuplicar() throws Exception {
        var hoy = LocalDate.now(reloj);
        var j = new JornadaMarcacionDocente(); j.setDocente(teacher); j.setFecha(hoy.minusDays(1));
        j.setApertura(hoy.minusDays(1).atTime(8,0)); j.setLimitePuntualidad(hoy.minusDays(1).atTime(8,45)); j.setCierre(hoy.atStartOfDay()); em.persist(j); em.flush();
        assertThat(getOk(REPORT+"DOCENTES").path("registros").get(0).path("estado").asText()).isEqualTo("SIN_MARCACION");
        var a = new AsistenciaDocente(); a.setDocente(teacher); a.setFechaJornada(j.getFecha()); a.setFechaHoraMarcada(j.getFecha().atTime(9,0)); a.setEstado(EstadoAsistencia.TARDANZA); em.persist(a); em.flush();
        var r = getOk(REPORT+"DOCENTES");
        assertThat(r.path("total").asLong()).isEqualTo(1);
        assertThat(r.path("registros").get(0).path("estado").asText()).isEqualTo("TARDANZA");
        assertThat(getOk(REPORT+"DOCENTES&seccionId="+section.getId()).path("total").asLong()).isZero();
        sesion(j.getFecha(),9,EstadoSesionClase.TOMADA); em.flush();
        assertThat(getOk(REPORT+"DOCENTES&seccionId="+section.getId()).path("total").asLong()).isEqualTo(1);
    }
    @Test void elJobNoReconstruyeJornadasPasadasNiDuplicaLasDeHoy() {
        var hoy = fecha;
        var job = new JornadasMarcacionService(em, Clock.fixed(hoy.atTime(12,0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima")));
        sesion(hoy.minusDays(1),9,EstadoSesionClase.PENDIENTE);
        sesion(hoy,22,EstadoSesionClase.PENDIENTE); em.flush();
        job.registrarJornadasDeHoy(); job.registrarJornadasDeHoy();
        var filas=em.createQuery("from JornadaMarcacionDocente",JornadaMarcacionDocente.class).getResultList();
        assertThat(filas).hasSize(1); assertThat(filas.getFirst().getFecha()).isEqualTo(hoy);
    }
}
