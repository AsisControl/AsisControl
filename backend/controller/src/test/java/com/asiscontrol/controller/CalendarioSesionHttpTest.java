// asistencia alumnos
package com.asiscontrol.controller;

import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.asistencia.EstadoSesionClase;
import com.asiscontrol.entity.asistencia.SesionClase;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.service.DiaNoLectivoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU14 fase 2: el calendario de sesiones sigue al horario y al calendario del año (2090). */
class CalendarioSesionHttpTest extends ApiIntegrationSupport {

    @Autowired DiaNoLectivoService diaNoLectivoService;

    private String sesiones() {
        return "/api/asistencias/asignaciones/" + assignment.getId() + "/sesiones";
    }

    private Aula aula() {
        var aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula"); aula.setTipoAula(TipoAula.AULA_REGULAR);
        aula.setCapacidad(30); aula.setUbicacion("Pabellon A"); em.persist(aula);
        return aula;
    }

    private JsonNode crearHorario(Aula aula, DiaSemana dia, int desde, int hasta) throws Exception {
        return request("POST", "/api/horarios", data("asignacionCursoId", assignment.getId(), "aulaId", aula.getId(),
                "diaSemana", dia, "horaInicio", LocalTime.of(desde, 0), "horaFin", LocalTime.of(hasta, 0)), 201);
    }

    private List<JsonNode> listar() throws Exception {
        var lista = new ArrayList<JsonNode>();
        getOk(sesiones()).forEach(lista::add);
        return lista;
    }

    private static List<LocalDate> dias(DayOfWeek dia) {
        var fechas = new ArrayList<LocalDate>();
        for (var f = LocalDate.of(2090, 1, 1); f.getYear() == 2090; f = f.plusDays(1))
            if (f.getDayOfWeek() == dia) fechas.add(f);
        return fechas;
    }

    @Test void generaUnEncuentroPorBloquesConsecutivosYOtroPorBloqueSeparado() throws Exception {
        var aula = aula();
        crearHorario(aula, DiaSemana.LUNES, 8, 9);
        crearHorario(aula, DiaSemana.LUNES, 9, 10);
        crearHorario(aula, DiaSemana.LUNES, 11, 12);

        var lista = listar();
        assertThat(lista).hasSize(2 * dias(DayOfWeek.MONDAY).size());
        var primera = lista.get(0);
        assertThat(primera.path("fecha").asText()).isEqualTo(dias(DayOfWeek.MONDAY).get(0).toString());
        assertThat(primera.path("horaInicio").asText()).startsWith("08:00");
        assertThat(primera.path("horaFin").asText()).startsWith("10:00");
        assertThat(lista.get(1).path("horaInicio").asText()).startsWith("11:00");
        assertThat(primera.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(primera.path("puedeTomar").asBoolean()).isFalse();
        assertThat(primera.path("puedeVer").asBoolean()).isFalse();
    }

    @Test void diaNoLectivoRetiraLaSesionDeEsaFecha() throws Exception {
        crearHorario(aula(), DiaSemana.LUNES, 8, 10);
        var feriado = dias(DayOfWeek.MONDAY).get(1);
        diaNoLectivoService.crear(2090, feriado, "Feriado");

        var lista = listar();
        assertThat(lista).hasSize(dias(DayOfWeek.MONDAY).size() - 1);
        assertThat(lista).noneMatch(s -> s.path("fecha").asText().equals(feriado.toString()));
    }

    @Test void cambioDeHorarioReprogramaPendientesYConservaLasTomadas() throws Exception {
        var horario = crearHorario(aula(), DiaSemana.LUNES, 8, 10);
        var primerLunes = dias(DayOfWeek.MONDAY).get(0);
        var tomada = em.createQuery("from SesionClase s where s.asignacionCurso.id = :a and s.fecha = :f", SesionClase.class)
                .setParameter("a", assignment.getId()).setParameter("f", primerLunes).getSingleResult();
        tomada.setEstado(EstadoSesionClase.TOMADA); tomada.setDocenteRegistrador(teacher); em.flush();

        request("PUT", "/api/horarios/" + horario.path("id").asLong(), data("asignacionCursoId", assignment.getId(),
                "aulaId", horario.path("aulaId").asLong(), "diaSemana", DiaSemana.MARTES,
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(10, 0),
                "version", horario.path("version").asLong()), 200);

        var lista = listar();
        assertThat(lista).hasSize(1 + dias(DayOfWeek.TUESDAY).size());
        assertThat(lista.get(0).path("id").asLong()).isEqualTo(tomada.getId());
        assertThat(lista.get(0).path("estado").asText()).isEqualTo("TOMADA");
        assertThat(lista.get(0).path("puedeEditar").asBoolean()).isTrue();
        assertThat(lista.subList(1, lista.size()))
                .allMatch(s -> LocalDate.parse(s.path("fecha").asText()).getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test void eliminarHorarioRetiraLasPendientesYReactivaAlVolverAProgramar() throws Exception {
        var aula = aula();
        var horario = crearHorario(aula, DiaSemana.LUNES, 8, 10);
        request("DELETE", "/api/horarios/" + horario.path("id").asLong(), null, 204);
        assertThat(listar()).isEmpty();

        crearHorario(aula, DiaSemana.LUNES, 8, 10);
        assertThat(listar()).hasSize(dias(DayOfWeek.MONDAY).size());
    }

    @Test void informaEstadoEfectivoYAccionesSegunLaFechaDelServidor() throws Exception {
        var horario = new Horario(); horario.setAsignacionCurso(assignment); horario.setAula(aula());
        horario.setDiaSemana(DiaSemana.LUNES); horario.setHoraInicio(LocalTime.of(8, 0)); horario.setHoraFin(LocalTime.of(9, 0));
        em.persist(horario);
        var hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        sesion(horario, hoy.minusDays(1), EstadoSesionClase.BORRADOR);
        sesion(horario, hoy, EstadoSesionClase.BORRADOR);
        em.flush();

        var lista = listar();
        var vencido = lista.get(0);
        assertThat(vencido.path("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(vencido.path("puedeTomar").asBoolean()).isFalse();
        assertThat(vencido.path("puedeVer").asBoolean()).isTrue();
        var deHoy = lista.get(1);
        assertThat(deHoy.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(deHoy.path("puedeTomar").asBoolean()).isTrue();
    }

    @Test void rechazaAsignacionInexistente() throws Exception {
        request("GET", "/api/asistencias/asignaciones/" + Long.MAX_VALUE + "/sesiones", null, 404);
    }

    private void sesion(Horario horario, LocalDate fecha, EstadoSesionClase estado) {
        var sesion = new SesionClase(); sesion.setAsignacionCurso(assignment); sesion.setHorario(horario);
        sesion.setFecha(fecha); sesion.setHoraInicio(horario.getHoraInicio()); sesion.setHoraFin(horario.getHoraFin());
        sesion.setEstado(estado); em.persist(sesion);
    }
}
