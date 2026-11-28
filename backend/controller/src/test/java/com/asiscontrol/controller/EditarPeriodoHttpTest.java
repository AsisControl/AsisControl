package com.asiscontrol.controller;

import com.asiscontrol.entity.Evaluacion;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.PeriodoEvaluacion;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ASISCTRL-237: editar el nombre y las fechas de un período de un año en borrador (2090). */
class EditarPeriodoHttpTest extends ApiIntegrationSupport {
    private static final String BASE = "/api/configuracion-academica";

    private long primero;
    private long segundo;

    @BeforeEach void periodos() throws Exception {
        year.setEstado(EstadoAnioAcademico.BORRADOR);
        em.flush();
        crear("Bimestre I", 1, "2090-03-01", "2090-05-31");
        var anio = crear("Bimestre II", 2, "2090-06-01", "2090-08-31");
        primero = anio.path("periodos").get(0).path("id").asLong();
        segundo = anio.path("periodos").get(1).path("id").asLong();
    }

    private JsonNode crear(String nombre, int orden, String inicio, String fin) throws Exception {
        return postOk(BASE + "/anios/2090/periodos",
                data("nombre", nombre, "orden", orden, "fechaInicio", inicio, "fechaFin", fin));
    }

    private Map<String, Object> cambio(String nombre, String inicio, String fin, long version) {
        return data("nombre", nombre, "fechaInicio", inicio, "fechaFin", fin,
                "version", version, "motivo", "Ajuste del calendario");
    }

    private JsonNode editar(long id, Object cuerpo, int estado) throws Exception {
        return request("PUT", BASE + "/periodos/" + id, cuerpo, estado);
    }

    private void rechaza(Object cuerpo, int estado, String codigo) throws Exception {
        assertThat(editar(primero, cuerpo, estado).path("code").asText()).isEqualTo(codigo);
        var actual = getOk(BASE + "/anios/2090").path("periodos").get(0);
        assertThat(actual.path("nombre").asText()).isEqualTo("Bimestre I");
        assertThat(actual.path("fechaInicio").asText()).isEqualTo("2090-03-01");
        assertThat(actual.path("fechaFin").asText()).isEqualTo("2090-05-31");
    }

    private void evaluacion(String fecha, EstadoEvaluacion estado, boolean activa) {
        var evaluacion = new Evaluacion();
        evaluacion.setAsignacionCurso(assignment);
        evaluacion.setTitulo("Práctica " + key());
        evaluacion.setTipoEvaluacion(TipoEvaluacion.PRACTICA_CALIFICADA);
        evaluacion.setPeriodoEvaluacion(PeriodoEvaluacion.BIMESTRAL);
        evaluacion.setFechaEvaluacion(LocalDate.parse(fecha));
        evaluacion.setPonderacionPorcentaje(BigDecimal.TEN);
        evaluacion.setEstado(estado);
        evaluacion.setActivo(activa);
        em.persist(evaluacion);
        em.flush();
    }

    private List<RegistroAuditoria> auditorias() {
        return em.createQuery(
                "from RegistroAuditoria r where r.accion = 'ACTUALIZAR_PERIODO' and r.registroId = :id",
                RegistroAuditoria.class).setParameter("id", String.valueOf(primero)).getResultList();
    }

    @Test void actualizaNombreYFechasConservaElOrdenYAudita() throws Exception {
        var cuerpo = cambio("  Primer bimestre  ", "2090-02-15", "2090-05-15", 0);
        cuerpo.put("orden", 9);

        var periodo = editar(primero, cuerpo, 200).path("periodos").get(0);

        assertThat(periodo.path("nombre").asText()).isEqualTo("Primer bimestre");
        assertThat(periodo.path("fechaInicio").asText()).isEqualTo("2090-02-15");
        assertThat(periodo.path("fechaFin").asText()).isEqualTo("2090-05-15");
        assertThat(periodo.path("orden").asInt()).isEqualTo(1);
        assertThat(periodo.path("estado").asText()).isEqualTo("PLANIFICADO");
        assertThat(periodo.path("version").asLong()).isEqualTo(1);
        assertThat(auditorias()).singleElement().satisfies(registro -> {
            assertThat(registro.getMotivo()).isEqualTo("Ajuste del calendario");
            assertThat(registro.getValorAnterior())
                    .isEqualTo("nombre=Bimestre I,inicio=2090-03-01,fin=2090-05-31");
            assertThat(registro.getNuevoValor())
                    .isEqualTo("nombre=Primer bimestre,inicio=2090-02-15,fin=2090-05-15");
        });
    }

    @Test void noSeComparaConsigoMismoYAdmiteUnSoloDia() throws Exception {
        editar(primero, cambio("bimestre i", "2090-03-01", "2090-05-31", 0), 200);
        editar(primero, cambio("Bimestre I", "2090-04-01", "2090-04-01", 1), 200);
    }

    @Test void rechazaFechasInvalidasOFueraDelAnio() throws Exception {
        rechaza(cambio("Bimestre I", "2090-05-01", "2090-04-30", 0), 422, "FECHAS_INVALIDAS");
        rechaza(cambio("Bimestre I", "2089-12-31", "2090-05-31", 0), 422, "PERIODO_FUERA_DEL_ANIO");
        rechaza(cambio("Bimestre I", "2090-03-01", "2091-01-01", 0), 422, "PERIODO_FUERA_DEL_ANIO");
        assertThat(auditorias()).noneMatch(r -> "nombre=Bimestre I,inicio=2090-03-01,fin=2090-05-31"
                .equals(r.getValorAnterior()));
    }

    @Test void rechazaSolapesInclusivosYNombresRepetidosDeOtroPeriodo() throws Exception {
        rechaza(cambio("Bimestre I", "2090-03-01", "2090-06-01", 0), 422, "PERIODOS_SUPERPUESTOS");
        rechaza(cambio("Bimestre I", "2090-07-01", "2090-07-15", 0), 422, "PERIODOS_SUPERPUESTOS");
        rechaza(cambio(" BIMESTRE II ", "2090-03-01", "2090-05-31", 0), 409, "PERIODO_DUPLICADO");
    }

    @Test void rechazaUnaVersionDesactualizadaOUnPeriodoInexistente() throws Exception {
        rechaza(cambio("Otro", "2090-03-01", "2090-05-31", 7), 409, "VERSION_DESACTUALIZADA");
        assertThat(editar(Long.MAX_VALUE, cambio("Otro", "2090-03-01", "2090-05-31", 0), 404)
                .path("code").asText()).isEqualTo("PERIODO_NO_ENCONTRADO");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoAnioAcademico.class, names = {"ACTIVO", "CERRADO"})
    void soloSeEditaConElAnioEnBorrador(EstadoAnioAcademico estado) throws Exception {
        year.setEstado(estado);
        em.flush();
        rechaza(cambio("Otro", "2090-03-01", "2090-05-31", 0), 422, "ANIO_NO_EN_BORRADOR");
    }

    @Test void exigeTodosLosCamposYRespetaSusLongitudes() throws Exception {
        for (String campo : List.of("nombre", "fechaInicio", "fechaFin", "version", "motivo")) {
            var cuerpo = cambio("Otro", "2090-03-01", "2090-05-31", 0);
            cuerpo.remove(campo);
            editar(primero, cuerpo, 400);
        }
        editar(primero, cambio("   ", "2090-03-01", "2090-05-31", 0), 400);
        editar(primero, cambio("a".repeat(101), "2090-03-01", "2090-05-31", 0), 400);
        var sinMotivo = cambio("Otro", "2090-03-01", "2090-05-31", 0);
        sinMotivo.put("motivo", " ");
        editar(primero, sinMotivo, 400);
        sinMotivo.put("motivo", "m".repeat(701));
        editar(primero, sinMotivo, 400);
        editar(primero, cambio("a".repeat(100), "2090-03-01", "2090-05-31", 0), 200);
    }

    @Test void rechazaSiUnaEvaluacionQuedaFueraDeLosPeriodos() throws Exception {
        evaluacion("2090-03-01", EstadoEvaluacion.BORRADOR, true);
        rechaza(cambio("Bimestre I", "2090-03-02", "2090-05-31", 0), 422, "EVALUACIONES_FUERA_DE_PERIODO");
        evaluacion("2090-05-31", EstadoEvaluacion.PUBLICADA, true);
        var rechazo = editar(primero, cambio("Bimestre I", "2090-04-01", "2090-04-30", 0), 422);
        assertThat(rechazo.path("message").asText()).contains("2 evaluaciones quedarían fuera");
    }

    @Test void permiteElCambioSiNingunaEvaluacionVigenteSeQuedaSinPeriodo() throws Exception {
        evaluacion("2090-04-10", EstadoEvaluacion.PUBLICADA, true);
        evaluacion("2090-03-01", EstadoEvaluacion.ANULADA, true);
        evaluacion("2090-05-31", EstadoEvaluacion.BORRADOR, false);
        // Ya estaba fuera de todo período antes del cambio: no lo causa esta edición.
        evaluacion("2090-10-01", EstadoEvaluacion.BORRADOR, true);
        // Pertenece al otro período, que no cambia.
        evaluacion("2090-07-01", EstadoEvaluacion.BORRADOR, true);

        editar(primero, cambio("Bimestre I", "2090-04-10", "2090-04-10", 0), 200);
        editar(segundo, cambio("Bimestre II", "2090-06-15", "2090-08-31", 0), 200);
    }
}
