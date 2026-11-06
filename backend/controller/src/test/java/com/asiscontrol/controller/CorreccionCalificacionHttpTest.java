package com.asiscontrol.controller;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.CursoCompetencia;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ASISCTRL-111: corrección propia, ajena y sin motivo con verificación de sus efectos persistidos. */
class CorreccionCalificacionHttpTest extends ApiIntegrationSupport {
    private static final String CALIFICACIONES = "/api/calificaciones";
    private static final String CORRECCIONES = CALIFICACIONES + "/correcciones";

    @Autowired private RegistroAuditoriaRepository auditorias;
    private AccesoUsuario accesoDocente;

    @BeforeEach
    void identificarAlDocenteResponsable() {
        teacher.getPersona().setNombres("Lucia");
        teacher.getPersona().setApellidoPaterno("Torres");
        accesoDocente = acceso(teacher);
        em.flush();
    }

    @ParameterizedTest(name = "El docente corrige a {0} y conserva trazabilidad")
    @EnumSource(NivelLogro.class)
    void correccionPropiaActualizaNotaHistorialAuditoriaYConsolidado(NivelLogro nivel) throws Exception {
        String anterior = nivel == NivelLogro.C ? "AD" : "C";
        JsonNode nota = publicada(assignment, anterior);
        Instant antes = Instant.now();

        JsonNode resultado = enviar(post(CORRECCIONES),
                correccion(nota, nivel.name(), "  Revisión de la evidencia de aprendizaje  "),
                comoDocente(), 200).get(0);

        assertThat(resultado.path("id").asLong()).isEqualTo(nota.path("id").asLong());
        assertThat(resultado.path("nivelLogro").asText()).isEqualTo(nivel.name());
        assertThat(resultado.path("version").asLong()).isEqualTo(nota.path("version").asLong() + 1);
        em.flush();
        em.clear();
        JsonNode guardada = getOk(ruta(nota));
        assertThat(guardada.path("nivelLogro").asText()).isEqualTo(nivel.name());
        assertThat(guardada.path("version").asLong()).isEqualTo(resultado.path("version").asLong());

        JsonNode historial = enviar(get(ruta(nota) + "/correcciones"), null, comoDocente(), 200);
        assertThat(historial).hasSize(1);
        JsonNode cambio = historial.get(0);
        assertThat(cambio.path("numeroVersion").asInt()).isEqualTo(1);
        assertThat(cambio.path("nivelAnterior").asText()).isEqualTo(anterior);
        assertThat(cambio.path("nivelNuevo").asText()).isEqualTo(nivel.name());
        assertThat(cambio.path("motivo").asText()).isEqualTo("Revisión de la evidencia de aprendizaje");
        assertThat(cambio.path("autor").asText()).isEqualTo(teacher.getPersona().nombreCompleto());
        Instant fecha = Instant.parse(cambio.path("fecha").asText());
        assertThat(fecha).isBetween(antes, Instant.now());

        List<RegistroAuditoria> eventos = correccionesExitosas(nota);
        assertThat(eventos).hasSize(1);
        RegistroAuditoria evento = eventos.getFirst();
        assertThat(evento.getActorIdentificador()).isEqualTo(accesoDocente.getUsername());
        assertThat(evento.getRolActor().getNombre()).isEqualTo("DOCENTE");
        assertThat(evento.getMotivo()).isEqualTo(cambio.path("motivo").asText());
        assertThat(evento.getValorAnterior()).isEqualTo("nivelLogro=" + anterior);
        assertThat(evento.getNuevoValor()).contains("nivelLogro=" + nivel.name(), "version=1");
        assertThat(evento.getFechaHora()).isBetween(antes, Instant.now());
        JsonNode consolidado = enviar(get(CALIFICACIONES + "/consolidados?idAlumno=" + student.getId()),
                null, comoDocente(), 200);
        assertThat(consolidado.get(0).path("nivelConsolidado").asText()).isEqualTo(nivel.name());
    }

    @ParameterizedTest(name = "Rechaza motivo {0} sin alterar la nota")
    @ValueSource(strings = {"OMITIDO", "NULO", "VACIO", "ESPACIOS", "TABULACIONES", "EXCESIVO"})
    void exigeMotivoAntesDeModificarLaCalificacion(String caso) throws Exception {
        JsonNode nota = publicada(assignment, "C");
        Map<String, Object> cuerpo = correccion(nota, "A", "Motivo");
        switch (caso) {
            case "OMITIDO" -> cuerpo.remove("motivo");
            case "NULO" -> cuerpo.put("motivo", null);
            case "VACIO" -> cuerpo.put("motivo", "");
            case "ESPACIOS" -> cuerpo.put("motivo", "   ");
            case "TABULACIONES" -> cuerpo.put("motivo", "\t\n");
            case "EXCESIVO" -> cuerpo.put("motivo", "m".repeat(501));
            default -> throw new IllegalArgumentException(caso);
        }

        JsonNode error = enviar(post(CORRECCIONES), cuerpo, comoDocente(), 400);

        assertThat(error.path("fieldErrors").findValuesAsText("field")).contains("motivo");
        verificarSinCambios(nota);
    }

    @Test
    void aceptaElLimiteDeQuinientosCaracteresDelMotivo() throws Exception {
        JsonNode nota = publicada(assignment, "C");
        String motivo = "m".repeat(500);

        enviar(post(CORRECCIONES), correccion(nota, "A", motivo), comoDocente(), 200);

        assertThat(getOk(ruta(nota) + "/correcciones").get(0).path("motivo").asText()).isEqualTo(motivo);
    }

    @ParameterizedTest(name = "Rechaza corrección por {0}")
    @ValueSource(strings = {"OTRO_DOCENTE", "SOLO_LECTURA", "ASIGNACION_RETIRADA", "ALUMNO", "APODERADO"})
    void rechazaActoresSinAutorizacionSinGuardarCambios(String caso) throws Exception {
        JsonNode nota = publicada(assignment, "C");
        JwtRequestPostProcessor actor = switch (caso) {
            case "OTRO_DOCENTE" -> como(acceso(otroDocente()), "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR");
            case "SOLO_LECTURA" -> como(accesoDocente, "CALIFICACIONES_LEER");
            case "ALUMNO" -> identidad("alumno", student.getPersona().getId(), "ALUMNO", "CALIFICACIONES_ESCRIBIR");
            case "APODERADO" -> identidad("apoderado", guardian.getPersona().getId(), "APODERADO", "CALIFICACIONES_ESCRIBIR");
            case "ASIGNACION_RETIRADA" -> {
                assignment.setActivo(false);
                em.flush();
                yield comoDocente();
            }
            default -> throw new IllegalArgumentException(caso);
        };

        enviar(post(CORRECCIONES), correccion(nota, "A", "Motivo válido"), actor, 403);

        verificarSinCambios(nota);
    }

    @Test
    void requiereAutenticacionParaCorregir() throws Exception {
        JsonNode nota = publicada(assignment, "C");

        enviar(post(CORRECCIONES), correccion(nota, "A", "Motivo válido"), null, 401);

        verificarSinCambios(nota);
    }

    @ParameterizedTest(name = "Rechaza estructura {0}")
    @ValueSource(strings = {"LISTA_OMITIDA", "LISTA_NULA", "LISTA_VACIA", "ITEM_NULO", "SIN_VERSION"})
    void rechazaSolicitudesIncompletasSinEfectosSecundarios(String caso) throws Exception {
        JsonNode nota = publicada(assignment, "C");
        Map<String, Object> cuerpo = correccion(nota, "A", "Motivo válido");
        switch (caso) {
            case "LISTA_OMITIDA" -> cuerpo.remove("correcciones");
            case "LISTA_NULA" -> cuerpo.put("correcciones", null);
            case "LISTA_VACIA" -> cuerpo.put("correcciones", List.of());
            case "ITEM_NULO" -> cuerpo.put("correcciones", Collections.singletonList(null));
            case "SIN_VERSION" -> cuerpo.put("correcciones", List.of(data(
                    "idCalificacion", nota.path("id").asLong(), "nivelLogro", "A")));
            default -> throw new IllegalArgumentException(caso);
        }

        enviar(post(CORRECCIONES), cuerpo, comoDocente(), 400);

        verificarSinCambios(nota);
    }

    @ParameterizedTest(name = "Rechaza nivel inválido {0}")
    @ValueSource(strings = {"Z", "20"})
    void soloAceptaNivelesDeLaEscalaInstitucional(String nivel) throws Exception {
        JsonNode nota = publicada(assignment, "C");

        enviar(post(CORRECCIONES), correccion(nota, nivel, "Motivo válido"), comoDocente(), 400);

        verificarSinCambios(nota);
    }

    @Test
    void unaVersionAnteriorNoSobrescribeUnaCorreccionConfirmada() throws Exception {
        JsonNode nota = publicada(assignment, "C");
        JsonNode vigente = enviar(post(CORRECCIONES), correccion(nota, "A", "Primera revisión"),
                comoDocente(), 200).get(0);

        JsonNode error = enviar(post(CORRECCIONES), correccion(nota, "B", "Formulario antiguo"),
                comoDocente(), 409);

        assertThat(error.path("code").asText()).isEqualTo("VERSION_DESACTUALIZADA");
        em.clear();
        JsonNode guardada = getOk(ruta(nota));
        assertThat(guardada.path("nivelLogro").asText()).isEqualTo("A");
        assertThat(guardada.path("version").asLong()).isEqualTo(vigente.path("version").asLong());
        assertThat(getOk(ruta(nota) + "/correcciones")).hasSize(1);
        assertThat(correccionesExitosas(nota)).hasSize(1);
    }

    @Test
    void noCreaUnaVersionSiElNivelEsElMismo() throws Exception {
        JsonNode nota = publicada(assignment, "C");

        JsonNode error = enviar(post(CORRECCIONES), correccion(nota, "C", "Revisión sin cambio"),
                comoDocente(), 422);

        assertThat(error.path("code").asText()).isEqualTo("CORRECCION_SIN_CAMBIOS");
        verificarSinCambios(nota);
    }

    @Test
    void elHistorialSoloEsVisibleParaElDocenteAsignadoYLaAdministracion() throws Exception {
        JsonNode nota = publicada(assignment, "C");
        enviar(post(CORRECCIONES), correccion(nota, "A", "Revisión válida"), comoDocente(), 200);
        String ruta = ruta(nota) + "/correcciones";

        assertThat(enviar(get(ruta), null, comoDocente(), 200)).hasSize(1);
        assertThat(getOk(ruta)).hasSize(1);
        enviar(get(ruta), null, como(acceso(otroDocente()), "CALIFICACIONES_LEER"), 403);
        enviar(get(ruta), null, identidad("alumno", student.getPersona().getId(), "ALUMNO", "CALIFICACIONES_LEER"), 403);
        enviar(get(ruta), null, identidad("apoderado", guardian.getPersona().getId(), "APODERADO", "CALIFICACIONES_LEER"), 403);
        enviar(get(ruta), null, null, 401);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void unLoteConUnElementoInvalidoRevierteNotasHistorialYAuditoriaDeExito() throws Exception {
        JsonNode primera = publicada(assignment, "C");
        JsonNode segunda = publicada(assignment, "C");
        JsonNode ajena = publicada(asignacionDe(otroDocente()), "C");
        JwtRequestPostProcessor actor = comoDocente();
        Map<String, Object> cambioValido = item(primera, "A");
        // Se confirma SOLO la base de pruebas. Cada solicitud abre y finaliza su propia transacción.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        List<Map<String, Object>> invalidos = List.of(
                data("item", item(ajena, "B"), "status", 403, "code", "ACCESO_DENEGADO"),
                data("item", item(primera, "B"), "status", 409, "code", "CALIFICACION_DUPLICADA_EN_LOTE"),
                data("item", data("idCalificacion", Long.MAX_VALUE, "nivelLogro", "B", "version", 0),
                        "status", 404, "code", "CALIFICACION_NO_ENCONTRADA"),
                data("item", data("idCalificacion", segunda.path("id").asLong(), "nivelLogro", "B",
                        "version", segunda.path("version").asLong() + 1),
                        "status", 409, "code", "VERSION_DESACTUALIZADA"),
                data("item", item(segunda, "C"), "status", 422, "code", "CORRECCION_SIN_CAMBIOS"));

        for (Map<String, Object> caso : invalidos) {
            JsonNode error = enviar(post(CORRECCIONES), data("motivo", "Revisión de lote",
                    "correcciones", List.of(cambioValido, caso.get("item"))), actor, (int) caso.get("status"));
            assertThat(error.path("code").asText()).isEqualTo(caso.get("code"));
            verificarSinCambios(primera);
            verificarSinCambios(segunda);
            verificarSinCambios(ajena);
        }
        assertThat(getOk(CALIFICACIONES + "/consolidados?idAlumno=" + student.getId()).get(0)
                .path("nivelConsolidado").asText()).isEqualTo("C");
    }

    private void verificarSinCambios(JsonNode original) throws Exception {
        em.clear();
        JsonNode actual = getOk(ruta(original));
        assertThat(actual.path("nivelLogro").asText()).isEqualTo(original.path("nivelLogro").asText());
        assertThat(actual.path("version").asLong()).isEqualTo(original.path("version").asLong());
        assertThat(getOk(ruta(original) + "/correcciones")).isEmpty();
        assertThat(correccionesExitosas(original)).isEmpty();
    }

    private List<RegistroAuditoria> correccionesExitosas(JsonNode nota) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("Calificacion", nota.path("id").asText())
                .stream().filter(evento -> evento.getAccion().equals("CORREGIR_CALIFICACION")
                        && evento.getResultado() == ResultadoAuditoria.EXITOSO).toList();
    }

    private JsonNode publicada(AsignacionCurso asignacion, String nivel) throws Exception {
        long evaluacion = postOk("/api/evaluaciones", data("idAsignacionCurso", asignacion.getId(),
                "titulo", "Evaluación de regresión " + key(), "tipoEvaluacion", "EXAMEN",
                "periodoEvaluacion", "BIMESTRAL", "fechaEvaluacion", LocalDate.now(),
                "ponderacionPorcentaje", 100, "idsCompetencia", Set.of(competence.getId())))
                .path("id").asLong();
        JsonNode nota = postOk(CALIFICACIONES, data("idEvaluacion", evaluacion, "idAlumno", student.getId(),
                "idCompetencia", competence.getId(), "nivelLogro", nivel));
        request("PUT", "/api/evaluaciones/" + evaluacion + "/estado", data("estado", "PUBLICADA"), 200);
        return getOk(ruta(nota));
    }

    private String ruta(JsonNode nota) {
        return CALIFICACIONES + "/" + nota.path("id").asLong();
    }

    private Map<String, Object> item(JsonNode nota, String nivel) {
        return data("idCalificacion", nota.path("id").asLong(), "nivelLogro", nivel,
                "version", nota.path("version").asLong());
    }

    private Map<String, Object> correccion(JsonNode nota, String nivel, String motivo) {
        return data("motivo", motivo, "correcciones", List.of(item(nota, nivel)));
    }

    private JsonNode enviar(MockHttpServletRequestBuilder solicitud, Object cuerpo,
            JwtRequestPostProcessor actor, int esperado) throws Exception {
        if (actor != null) {
            solicitud.with(actor);
        }
        if (cuerpo != null) {
            solicitud.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(cuerpo));
        }
        byte[] respuesta = mvc.perform(solicitud).andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsByteArray();
        return respuesta.length == 0 ? json.nullNode() : json.readTree(respuesta);
    }

    private AccesoUsuario acceso(Docente docente) {
        AccesoUsuario acceso = new AccesoUsuario();
        acceso.setPersona(docente.getPersona());
        acceso.setRol(em.createQuery("from Rol r where r.nombre = 'DOCENTE'", Rol.class).getSingleResult());
        acceso.setUsername("docente" + key());
        acceso.setCorreo(key() + "@example.test");
        acceso.setContrasenaHash("hash-exclusivo-de-prueba");
        acceso.setEstado(EstadoAcceso.ACTIVO);
        acceso.setRequiereCambioContrasena(false);
        em.persist(acceso);
        em.flush();
        return acceso;
    }

    private JwtRequestPostProcessor comoDocente() {
        return como(accesoDocente, "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR");
    }

    private JwtRequestPostProcessor como(AccesoUsuario acceso, String... permisos) {
        return identidad(acceso.getUsername(), acceso.getPersona().getId(), acceso.getRol().getNombre(), permisos);
    }

    private JwtRequestPostProcessor identidad(String usuario, Long persona, String rol, String... permisos) {
        List<org.springframework.security.core.GrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
        for (String permiso : permisos) {
            autoridades.add(new SimpleGrantedAuthority(permiso));
        }
        return jwt().jwt(token -> token.subject(usuario).claim("personaId", persona)
                .claim("requiereCambioContrasena", false)).authorities(autoridades);
    }

    private Docente otroDocente() {
        Docente otro = new Docente();
        otro.setPersona(person());
        otro.setCodigoDocente("DOC" + key());
        otro.setFechaIngreso(LocalDate.now().minusYears(1));
        otro.setEspecialidad(Especialidad.COMUNICACION);
        em.persist(otro);
        em.flush();
        return otro;
    }

    private AsignacionCurso asignacionDe(Docente otro) {
        // Otro curso de la misma sección: la pareja curso-sección debe seguir siendo única.
        Curso otroCurso = new Curso();
        otroCurso.setCodigo("CUR" + key());
        otroCurso.setNombre("Comunicación de prueba");
        otroCurso.setHorasSemanales(4);
        otroCurso.setGradoAcademico(grade);
        em.persist(otroCurso);
        CursoCompetencia vinculo = new CursoCompetencia();
        vinculo.setCurso(otroCurso);
        vinculo.setCompetencia(competence);
        vinculo.setOrden(1);
        em.persist(vinculo);
        AsignacionCurso asignacion = new AsignacionCurso();
        asignacion.setCurso(otroCurso);
        asignacion.setSeccion(section);
        asignacion.setDocente(otro);
        em.persist(asignacion);
        em.flush();
        return asignacion;
    }
}
