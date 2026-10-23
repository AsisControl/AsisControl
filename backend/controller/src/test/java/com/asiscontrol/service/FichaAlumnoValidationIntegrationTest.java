package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:asiscontrol_ficha_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@ActiveProfiles("test")
@Transactional
class FichaAlumnoValidationIntegrationTest {
    @Autowired WebApplicationContext webContext;
    @Autowired AlumnoRepository alumnoRepository;
    @Autowired RegistroAuditoriaRepository auditoriaRepository;
    MockMvc mvc;
    final ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeEach
    void prepareMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
    }

    private Map<String, Object> createStudentPersonPayload() {
        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Diego");
        persona.put("apellidoPaterno", "Ibarra");
        persona.put("apellidoMaterno", null);
        persona.put("tipoDocumento", null);
        persona.put("numeroDocumento", null);
        persona.put("sexo", null);
        persona.put(
                "fechaNacimiento",
                LocalDate.now(ZoneId.of("America/Lima")).minusYears(14).toString());
        persona.put("correo", null);
        persona.put("telefono", null);
        persona.put("direccion", null);
        return persona;
    }

    private MockHttpServletRequestBuilder createAdminRequest(
            MockHttpServletRequestBuilder request, Map<String, Object> payload) throws Exception {
        return request.with(
                        jwt().jwt(jwtBuilder -> jwtBuilder.subject("fe2-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(payload));
    }

    private void assertFieldRejected(String field, Object value, String expected) throws Exception {
        var persona = createStudentPersonPayload();
        persona.put(field, value);
        mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.fieldErrors[?(@.field == 'persona." + field + "')].message")
                                .value(
                                        org.hamcrest.Matchers.hasItem(
                                                org.hamcrest.Matchers.containsString(expected))));
    }

    @Test
    void guardaFichaMinimaSinObligatoriosOcultosYGeneraCodigoUnico() throws Exception {
        long before = alumnoRepository.count();
        String first =
                mvc.perform(
                                createAdminRequest(
                                        post("/api/alumnos"),
                                        Map.of(
                                                "persona",
                                                createStudentPersonPayload(),
                                                "codigoAlumno",
                                                "MANUAL-NO-PERMITIDO")))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.codigoAlumno")
                                        .value(
                                                org.hamcrest.Matchers.matchesPattern(
                                                        "ESTU-[0-9]{6,}")))
                        .andExpect(jsonPath("$.persona.apellidoMaterno").isEmpty())
                        .andExpect(jsonPath("$.persona.tipoDocumento").isEmpty())
                        .andExpect(jsonPath("$.persona.nombreCompleto").value("Diego Ibarra"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second =
                mvc.perform(
                                createAdminRequest(
                                        post("/api/alumnos"),
                                        Map.of("persona", createStudentPersonPayload())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(jsonMapper.readTree(first).get("codigoAlumno").asText())
                .isNotEqualTo(jsonMapper.readTree(second).get("codigoAlumno").asText());
        assertThat(alumnoRepository.count()).isEqualTo(before + 2);
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void dosAltasSimultaneasGeneranCodigosDistintos() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> create =
                    () -> {
                        String body =
                                mvc.perform(
                                                createAdminRequest(
                                                        post("/api/alumnos"),
                                                        Map.of(
                                                                "persona",
                                                                createStudentPersonPayload())))
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString();
                        return jsonMapper.readTree(body).get("codigoAlumno").asText();
                    };
            var firstResult = executor.submit(create);
            var secondResult = executor.submit(create);
            assertThat(firstResult.get(20, java.util.concurrent.TimeUnit.SECONDS))
                    .isNotEqualTo(secondResult.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void admiteLetrasEspanolasYCumpleanosLimite() throws Exception {
        var today = LocalDate.now(ZoneId.of("America/Lima"));
        for (int age : new int[] {10, 20}) {
            var persona = createStudentPersonPayload();
            persona.put("nombres", "María José");
            persona.put("apellidoPaterno", "D’Ávila");
            persona.put("apellidoMaterno", "Muñoz-Güemes");
            persona.put("fechaNacimiento", today.minusYears(age).toString());
            mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void rechazaCaracteresInvalidosPorApiDirecta() throws Exception {
        assertFieldRejected("nombres", "Diego123", "solo letras");
        assertFieldRejected("apellidoPaterno", "Ibarra@", "solo letras");
        assertFieldRejected("apellidoMaterno", "López1", "solo letras");
        assertFieldRejected("telefono", "812345678", "empezar con 9");
        assertFieldRejected("telefono", "9997422000", "9 dígitos");
    }

    @Test
    void informaEdadFuturaRangoYCorreoPorCampo() throws Exception {
        var today = LocalDate.now(ZoneId.of("America/Lima"));
        assertFieldRejected("fechaNacimiento", today.plusDays(1).toString(), "futura");
        assertFieldRejected("fechaNacimiento", today.minusYears(9).toString(), "10 y 20");
        assertFieldRejected("fechaNacimiento", today.minusYears(21).toString(), "10 y 20");
        assertFieldRejected("correo", "correo-invalido", "correo válido");
    }

    @Test
    void formatoJsonInvalidoIdentificaFechaYEnumeracion() throws Exception {
        assertFieldRejected("fechaNacimiento", "07/10/2012", "fecha de nacimiento válida");
        assertFieldRejected("tipoDocumento", "TIPO_INEXISTENTE", "Selecciona DNI");
        assertFieldRejected("sexo", "INVALIDO", "opciones de sexo");
    }

    @Test
    void documentoEsOpcionalEnParejaYSeValidaSegunTipo() throws Exception {
        var persona = createStudentPersonPayload();
        persona.put("numeroDocumento", "12345678");
        mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("persona.tipoDocumento"));
        persona.put("tipoDocumento", "DNI");
        persona.put("numeroDocumento", "1234567");
        mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("persona.numeroDocumento"));
        persona.put("numeroDocumento", "12345678");
        persona.put("telefono", "999742200");
        persona.put("correo", "estudiante.demo@example.com");
        mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                .andExpect(status().isCreated());
        mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("persona.numeroDocumento"));
    }

    @Test
    void corrigeIdentidadConMotivoSinPedirCodigoYConservaAuditoria() throws Exception {
        var persona = createStudentPersonPayload();
        var result =
                mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                        .andExpect(status().isCreated())
                        .andReturn();
        var created = jsonMapper.readTree(result.getResponse().getContentAsString());
        long id = created.get("id").asLong();
        persona.put("nombres", "Diego José");
        mvc.perform(createAdminRequest(put("/api/alumnos/" + id), Map.of("persona", persona)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("motivo"));
        mvc.perform(
                        createAdminRequest(
                                put("/api/alumnos/" + id),
                                Map.of("persona", persona, "motivo", "Corrección según documento")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoAlumno").value(created.get("codigoAlumno").asText()));
        assertThat(auditoriaRepository.findAll())
                .anySatisfy(
                        firstResult -> {
                            assertThat(firstResult.getAccion()).isEqualTo("ACTUALIZAR_ALUMNO");
                            assertThat(firstResult.getMotivo())
                                    .isEqualTo("Corrección según documento");
                            assertThat(firstResult.getValorAnterior()).contains("nombres=Diego,");
                            assertThat(firstResult.getNuevoValor()).contains("nombres=Diego José,");
                            assertThat(firstResult.getActorIdentificador()).isEqualTo("fe2-admin");
                        });
    }

    @Test
    void conservaMotivoCompletoAunqueElResumenDeAuditoriaSeaDeQuinientosCaracteres()
            throws Exception {
        var persona = createStudentPersonPayload();
        var result =
                mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                        .andExpect(status().isCreated())
                        .andReturn();
        long id = jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        persona.put("nombres", "Diego José");
        String motivo = "Corrección documentada: " + "a".repeat(600);
        mvc.perform(
                        createAdminRequest(
                                put("/api/alumnos/" + id),
                                Map.of("persona", persona, "motivo", motivo)))
                .andExpect(status().isOk());
        assertThat(auditoriaRepository.findAll())
                .anySatisfy(
                        registro -> {
                            assertThat(registro.getAccion()).isEqualTo("ACTUALIZAR_ALUMNO");
                            assertThat(registro.getRegistroId()).isEqualTo(Long.toString(id));
                            assertThat(registro.getMotivo()).isEqualTo(motivo.substring(0, 500));
                            assertThat(registro.getNuevoValor())
                                    .contains("motivoCorreccion=" + motivo);
                        });
    }

    @Test
    void actualizarSoloContactoRegistraCamposSinCopiarSusValores() throws Exception {
        var persona = createStudentPersonPayload();
        var result =
                mvc.perform(createAdminRequest(post("/api/alumnos"), Map.of("persona", persona)))
                        .andExpect(status().isCreated())
                        .andReturn();
        long id = jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        persona.put("telefono", "999742200");
        persona.put("correo", "contacto.ficha@example.test");
        mvc.perform(createAdminRequest(put("/api/alumnos/" + id), Map.of("persona", persona)))
                .andExpect(status().isOk());
        assertThat(auditoriaRepository.findAll())
                .anySatisfy(
                        registro -> {
                            assertThat(registro.getAccion()).isEqualTo("ACTUALIZAR_ALUMNO");
                            assertThat(registro.getRegistroId()).isEqualTo(Long.toString(id));
                            assertThat(registro.getMotivo()).contains("telefono", "correo");
                            assertThat(registro.getValorAnterior())
                                    .doesNotContain("999742200", "contacto.ficha@example.test");
                            assertThat(registro.getNuevoValor())
                                    .doesNotContain("999742200", "contacto.ficha@example.test");
                        });
    }

    @Test
    void laApiMantieneAutorizacion() throws Exception {
        mvc.perform(
                        post("/api/alumnos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        jsonMapper.writeValueAsString(
                                                Map.of("persona", createStudentPersonPayload()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/alumnos")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCENTE")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        jsonMapper.writeValueAsString(
                                                Map.of("persona", createStudentPersonPayload()))))
                .andExpect(status().isForbidden());
    }
}
