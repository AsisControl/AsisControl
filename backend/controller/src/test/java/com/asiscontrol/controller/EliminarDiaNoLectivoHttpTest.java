package com.asiscontrol.controller;

import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.TipoAula;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ASISCTRL-239: eliminar un día no lectivo de hoy o futuro (año 2090 centrado en hoy). */
class EliminarDiaNoLectivoHttpTest extends ApiIntegrationSupport {
    private static final String BASE = "/api/configuracion-academica";

    @Autowired Clock reloj;
    private LocalDate hoy;

    @BeforeEach void anioEnCurso() {
        hoy = LocalDate.now(reloj);
        year.setFechaInicio(hoy.minusDays(30));
        year.setFechaFin(hoy.plusDays(30));
        em.flush();
    }

    private JsonNode crear(LocalDate fecha) throws Exception {
        return postOk(BASE + "/anios/2090/dias-no-lectivos", data("fecha", fecha, "descripcion", "Feriado"));
    }

    private JsonNode eliminar(long id, long version, int estado) throws Exception {
        return request("DELETE", BASE + "/dias-no-lectivos/" + id + "?version=" + version, null, estado);
    }

    private List<String> fechas() throws Exception {
        var fechas = new ArrayList<String>();
        getOk(BASE + "/anios/2090/dias-no-lectivos").forEach(d -> fechas.add(d.path("fecha").asText()));
        return fechas;
    }

    private void rechaza(JsonNode dia, long version, int estado, String codigo) throws Exception {
        assertThat(eliminar(dia.path("id").asLong(), version, estado).path("code").asText()).isEqualTo(codigo);
        assertThat(fechas()).containsExactly(dia.path("fecha").asText());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoAnioAcademico.class, names = {"BORRADOR", "ACTIVO"})
    void eliminaHoyYFechasFuturasYPermiteRegistrarlasDeNuevo(EstadoAnioAcademico estado) throws Exception {
        year.setEstado(estado);
        em.flush();
        for (var fecha : List.of(hoy, hoy.plusDays(7))) {
            eliminar(crear(fecha).path("id").asLong(), 0, 204);
            assertThat(fechas()).isEmpty();
        }
        crear(hoy);
        assertThat(fechas()).containsExactly(hoy.toString());
    }

    @Test void auditaElValorEliminado() throws Exception {
        long id = crear(hoy.plusDays(1)).path("id").asLong();
        eliminar(id, 0, 204);

        var registros = em.createQuery(
                "from RegistroAuditoria r where r.accion = 'ELIMINAR_DIA_NO_LECTIVO' and r.registroId = :id",
                RegistroAuditoria.class).setParameter("id", String.valueOf(id)).getResultList();
        assertThat(registros).singleElement().satisfies(registro -> {
            assertThat(registro.getValorAnterior())
                    .isEqualTo("fecha=" + hoy.plusDays(1) + ",descripcion=Feriado");
            assertThat(registro.getNuevoValor()).isNull();
            assertThat(registro.getMotivo()).isNull();
        });
    }

    @Test void elCalendarioRecuperaLaSesionDelDiaEliminado() throws Exception {
        var aula = new Aula(); aula.setCodigo("AU" + key()); aula.setNombre("Aula");
        aula.setTipoAula(TipoAula.AULA_REGULAR); aula.setCapacidad(30); aula.setUbicacion("Pabellón A");
        em.persist(aula);
        var enUnaSemana = hoy.plusDays(7);
        // El horario no admite domingos.
        var fecha = enUnaSemana.getDayOfWeek() == DayOfWeek.SUNDAY ? enUnaSemana.plusDays(1) : enUnaSemana;
        postOk("/api/horarios", data("asignacionCursoId", assignment.getId(), "aulaId", aula.getId(),
                "diaSemana", DiaSemana.values()[fecha.getDayOfWeek().ordinal()],
                "horaInicio", LocalTime.of(8, 0), "horaFin", LocalTime.of(9, 0)));
        long id = crear(fecha).path("id").asLong();
        assertThat(sesionesActivas(fecha)).isZero();

        eliminar(id, 0, 204);

        assertThat(sesionesActivas(fecha)).isEqualTo(1);
    }

    private long sesionesActivas(LocalDate fecha) {
        return em.createQuery("select count(s) from SesionClase s where s.asignacionCurso.id = :a "
                        + "and s.fecha = :f and s.activo = true", Long.class)
                .setParameter("a", assignment.getId()).setParameter("f", fecha).getSingleResult();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoAnioAcademico.class, names = {"BORRADOR", "ACTIVO"})
    void conservaLosDiasPasados(EstadoAnioAcademico estado) throws Exception {
        year.setEstado(estado);
        em.flush();
        rechaza(crear(hoy.minusDays(1)), 0, 422, "DIA_NO_LECTIVO_PASADO");
    }

    @Test void unAnioCerradoNoAdmiteEliminaciones() throws Exception {
        var dia = crear(hoy.plusDays(1));
        year.setEstado(EstadoAnioAcademico.CERRADO);
        em.flush();
        rechaza(dia, 0, 422, "DIA_NO_LECTIVO_ANIO_CERRADO");
    }

    @Test void exigeLaVersionVigenteYUnDiaExistente() throws Exception {
        var dia = crear(hoy.plusDays(1));
        long id = dia.path("id").asLong();
        rechaza(dia, 3, 409, "VERSION_DESACTUALIZADA");
        request("DELETE", BASE + "/dias-no-lectivos/" + id, null, 400);
        eliminar(id, 0, 204);
        assertThat(eliminar(id, 0, 404).path("code").asText()).isEqualTo("DIA_NO_LECTIVO_NO_ENCONTRADO");
    }
}
