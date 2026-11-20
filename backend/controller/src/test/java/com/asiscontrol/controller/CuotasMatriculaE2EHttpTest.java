package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.CompromisoPago;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta: matricular a un alumno genera exactamente las cuotas de su plan anual de pensiones,
 * aplicando la beca si la tiene, y una nueva matrícula del mismo alumno en el mismo año no las duplica.
 * Todo se hace por la API, como lo haría la secretaría.
 */
class CuotasMatriculaE2EHttpTest extends ApiIntegrationSupport {
    private static final String CONFIG = "/api/configuracion-pagos";
    private static final String SOLICITUDES = "/api/solicitudes-matricula";
    private static final int ANIO = 2090;
    private static final int GRADO = 1;
    /** El plan anual cubre de marzo a diciembre: diez pensiones. */
    private static final List<Integer> MESES = IntStream.rangeClosed(3, 12).boxed().toList();

    private Long pension() {
        return em.createQuery("select c.id from ConceptoCobro c where c.codigo='PENSION'", Long.class).getSingleResult();
    }

    private void tarifa(String montoMensual) throws Exception {
        postOk(CONFIG + "/tarifas", data("idConcepto", pension(), "anioAcademico", ANIO, "grado", GRADO,
                "montoMensual", montoMensual));
    }

    private void beca(Alumno alumno, String tipo, String valor, int mesInicio, int mesFin) throws Exception {
        postOk(CONFIG + "/beneficios", data("idAlumno", alumno.getId(), "anioAcademico", ANIO, "idConcepto", pension(),
                "nombre", "Beca familiar", "tipo", tipo, "valor", valor, "mesInicio", mesInicio, "mesFin", mesFin,
                "motivo", "Acuerdo de la institución"));
    }

    private Alumno alumnoNuevo() {
        var alumno = new Alumno();
        alumno.setPersona(person());
        alumno.setCodigoAlumno("E2E" + key());
        em.persist(alumno);
        em.flush();
        return alumno;
    }

    private void cambiarEstado(String solicitud, String estado) throws Exception {
        putOk(solicitud, data("estado", estado, "version", getOk(solicitud).path("version").asLong()));
    }

    /** Solicitud en revisión con sección asignada, lista para finalizar. */
    private JsonNode solicitudLista(Alumno alumno) throws Exception {
        var creada = postOk(SOLICITUDES, data("alumnoId", alumno.getId(), "anioAcademico", ANIO, "grado", GRADO,
                "observaciones", "Documentos completos"));
        String ruta = SOLICITUDES + "/" + creada.path("id").asLong();
        cambiarEstado(ruta, "ENVIADA");
        cambiarEstado(ruta, "EN_REVISION");
        putOk(ruta, data("seccionId", section.getId(), "version", getOk(ruta).path("version").asLong()));
        return getOk(ruta);
    }

    private JsonNode planDe(JsonNode solicitud) throws Exception {
        return getOk(CONFIG + "/planes/solicitudes/" + solicitud.path("id").asLong() + "/preview");
    }

    /** Finaliza la matrícula con la firma del plan que vio la secretaría, igual que la pantalla. */
    private JsonNode finalizar(JsonNode solicitud, int esperado) throws Exception {
        return request("POST", "/api/matriculas", data("solicitudMatriculaId", solicitud.path("id").asLong(),
                "versionSolicitud", solicitud.path("version").asLong(),
                "firmaPlan", planDe(solicitud).path("firmaPlan").asText()), esperado);
    }

    private List<CompromisoPago> compromisosDe(Alumno alumno) {
        em.flush();
        em.clear();
        return em.createQuery("""
                select c from CompromisoPago c
                where c.matricula.alumno.id = :alumno and c.matricula.anioAcademico = :anio
                order by c.mes""", CompromisoPago.class)
                .setParameter("alumno", alumno.getId()).setParameter("anio", ANIO).getResultList();
    }

    @Test
    void matricularGeneraExactamenteLasDiezPensionesDelPlanSinBeca() throws Exception {
        tarifa("400.00");
        Alumno alumno = alumnoNuevo();
        JsonNode solicitud = solicitudLista(alumno);
        JsonNode plan = planDe(solicitud);
        assertThat(plan.path("puedeGenerar").asBoolean()).isTrue();
        assertThat(plan.path("items")).hasSize(10);

        JsonNode matricula = finalizar(solicitud, 201);

        List<CompromisoPago> cuotas = compromisosDe(alumno);
        assertThat(cuotas).hasSize(10);
        assertThat(cuotas).extracting(CompromisoPago::getMes).containsExactlyElementsOf(MESES);
        assertThat(cuotas).extracting(CompromisoPago::getNumeroCuota)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
        assertThat(cuotas).allSatisfy(cuota -> {
            assertThat(cuota.getMatricula().getId()).isEqualTo(matricula.path("id").asLong());
            assertThat(cuota.getConceptoCobro().getId()).isEqualTo(pension());
            assertThat(cuota.getMontoBase()).isEqualByComparingTo("400.00");
            assertThat(cuota.getMontoDescuento()).isEqualByComparingTo("0.00");
            assertThat(cuota.getMontoTotal()).isEqualByComparingTo("400.00");
            assertThat(cuota.getMontoPagado()).isEqualByComparingTo("0.00");
            assertThat(cuota.getMoneda()).isEqualTo("PEN");
            assertThat(cuota.getIdBeneficio()).isNull();
            assertThat(cuota.getFechaVencimiento()).isEqualTo(YearMonth.of(ANIO, cuota.getMes()).atEndOfMonth());
        });
        assertThat(cuotas.stream().map(CompromisoPago::getClaveObligacion).distinct()).hasSize(10);
    }

    @Test
    void matricularAplicaLaBecaSoloEnLosMesesQueCubre() throws Exception {
        tarifa("400.00");
        Alumno alumno = alumnoNuevo();
        beca(alumno, "PORCENTAJE", "25.00", 4, 6);
        JsonNode solicitud = solicitudLista(alumno);

        finalizar(solicitud, 201);

        List<CompromisoPago> cuotas = compromisosDe(alumno);
        assertThat(cuotas).hasSize(10);
        assertThat(cuotas.stream().filter(c -> c.getMes() >= 4 && c.getMes() <= 6)).hasSize(3).allSatisfy(cuota -> {
            assertThat(cuota.getMontoBase()).isEqualByComparingTo("400.00");
            assertThat(cuota.getMontoDescuento()).isEqualByComparingTo("100.00");
            assertThat(cuota.getMontoTotal()).isEqualByComparingTo("300.00");
            assertThat(cuota.getBeneficioNombre()).isEqualTo("Beca familiar");
            assertThat(cuota.getIdBeneficio()).isNotNull();
        });
        assertThat(cuotas.stream().filter(c -> c.getMes() < 4 || c.getMes() > 6)).hasSize(7).allSatisfy(cuota -> {
            assertThat(cuota.getMontoTotal()).isEqualByComparingTo("400.00");
            assertThat(cuota.getIdBeneficio()).isNull();
        });
    }

    @Test
    void unaBecaCompletaGeneraLasDiezCuotasExoneradas() throws Exception {
        tarifa("400.00");
        Alumno alumno = alumnoNuevo();
        beca(alumno, "PORCENTAJE", "100.00", 3, 12);

        finalizar(solicitudLista(alumno), 201);

        List<CompromisoPago> cuotas = compromisosDe(alumno);
        assertThat(cuotas).hasSize(10).allSatisfy(cuota -> {
            assertThat(cuota.getMontoTotal()).isEqualByComparingTo("0.00");
            assertThat(cuota.getMontoDescuento()).isEqualByComparingTo("400.00");
        });
        JsonNode obligacion = getOk("/api/compromisos-pago/" + cuotas.getFirst().getId());
        assertThat(obligacion.path("estadoFinanciero").asText()).isEqualTo("EXONERADO");
    }

    @Test
    void conUnaMatriculaActivaNoSeAdmiteOtraSolicitudDelMismoAnioYNoSeGeneranCuotas() throws Exception {
        tarifa("400.00");
        Alumno alumno = alumnoNuevo();
        finalizar(solicitudLista(alumno), 201);

        JsonNode error = request("POST", SOLICITUDES, data("alumnoId", alumno.getId(), "anioAcademico", ANIO,
                "grado", GRADO, "observaciones", "Segundo intento"), 409);

        assertThat(error.path("code").asText()).isNotBlank();
        assertThat(compromisosDe(alumno)).hasSize(10);
    }

    @Test
    void unaNuevaMatriculaDelMismoAlumnoEnElMismoAnioNoDuplicaLasCuotas() throws Exception {
        tarifa("400.00");
        Alumno alumno = alumnoNuevo();
        JsonNode primera = finalizar(solicitudLista(alumno), 201);
        List<Long> cuotasOriginales = compromisosDe(alumno).stream().map(CompromisoPago::getId).toList();
        String rutaPrimera = "/api/matriculas/" + primera.path("id").asLong();
        putOk(rutaPrimera, data("estado", "RETIRADA", "version", getOk(rutaPrimera).path("version").asLong(),
                "motivo", "Retiro temporal"));

        JsonNode segunda = solicitudLista(alumno);
        JsonNode plan = planDe(segunda);
        assertThat(plan.path("items")).hasSize(10);
        assertThat(plan.path("items").findValuesAsText("estado")).doesNotContain("POR_GENERAR");
        JsonNode nueva = finalizar(segunda, 201);

        assertThat(nueva.path("id").asLong()).isNotEqualTo(primera.path("id").asLong());
        List<CompromisoPago> cuotas = compromisosDe(alumno);
        assertThat(cuotas).hasSize(10);
        assertThat(cuotas).extracting(CompromisoPago::getId).containsExactlyInAnyOrderElementsOf(cuotasOriginales);
        assertThat(cuotas).extracting(CompromisoPago::getMes).containsExactlyElementsOf(MESES);
    }
}
