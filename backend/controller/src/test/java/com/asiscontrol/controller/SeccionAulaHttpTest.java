package com.asiscontrol.controller;

import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ASISCTRL-240: aula opcional y compartible de una sección, con la capacidad del aula mayor o
 * igual a la capacidad máxima de la sección. La base trae un año 2090 activo y la sección "A"
 * de 30 vacantes con matrícula y asignación docente.
 */
class SeccionAulaHttpTest extends ApiIntegrationSupport {

    private static final String SECCIONES = "/api/secciones";
    private static final String AULAS = "/api/aulas";

    @Autowired RegistroAuditoriaRepository auditorias;

    private JsonNode aula(int capacidad) throws Exception {
        String codigo = "SA-" + key();
        return postOk(AULAS, data("codigo", codigo, "nombre", "Aula " + codigo,
                "tipoAula", "AULA_REGULAR", "capacidad", capacidad));
    }

    private Map<String, Object> alta(String nombre, int capacidad, Object... extra) {
        Map<String, Object> cuerpo = data("nombre", nombre, "grado", 1, "anioAcademico", 2090,
                "capacidadMaxima", capacidad, "motivo", "Apertura");
        cuerpo.putAll(data(extra));
        return cuerpo;
    }

    /** PUT con los datos actuales de la sección; el aula solo viaja si se indica. */
    private JsonNode editar(long id, int esperado, Object... extra) throws Exception {
        JsonNode actual = getOk(SECCIONES + "/" + id);
        Map<String, Object> cuerpo = data("nombre", actual.path("nombre").asText(), "grado", 1,
                "anioAcademico", 2090, "capacidadMaxima", actual.path("capacidadMaxima").asInt(),
                "docenteTutorId", actual.path("docenteTutorId").isNull()
                        ? null : actual.path("docenteTutorId").asLong(),
                "version", actual.path("version").asLong(), "motivo", "Ajuste");
        cuerpo.putAll(data(extra));
        return request("PUT", SECCIONES + "/" + id, cuerpo, esperado);
    }

    private JsonNode cambiarEstado(long id, boolean activo, int esperado) throws Exception {
        long version = -1;
        for (JsonNode s : getOk(SECCIONES + "?activo=" + !activo + "&anioAcademico=2090&size=100").path("content")) {
            if (s.path("id").asLong() == id) version = s.path("version").asLong();
        }
        return request("PUT", SECCIONES + "/" + id + "/estado",
                data("activo", activo, "version", version, "motivo", "Cambio"), esperado);
    }

    private JsonNode editarAula(long id, int capacidad, int esperado) throws Exception {
        JsonNode actual = getOk(AULAS + "/" + id);
        return request("PUT", AULAS + "/" + id, data("codigo", actual.path("codigo").asText(),
                "nombre", actual.path("nombre").asText(), "tipoAula", "AULA_REGULAR",
                "capacidad", capacidad, "version", actual.path("version").asLong()), esperado);
    }

    private JsonNode inhabilitar(long id, int esperado) throws Exception {
        long version = getOk(AULAS + "/" + id).path("version").asLong();
        return request("PATCH", AULAS + "/" + id + "/inhabilitar",
                data("motivo", "Mantenimiento", "version", version), esperado);
    }

    private void habilitar(long id) throws Exception {
        long version = getOk(AULAS + "/" + id).path("version").asLong();
        request("PATCH", AULAS + "/" + id + "/habilitar", data("version", version), 200);
    }

    private long id(JsonNode nodo) {
        return nodo.path("id").asLong();
    }

    @Test
    void asignaElAulaAlCrearYLaMuestraEnTodasLasRespuestas() throws Exception {
        JsonNode aula = aula(30);

        JsonNode creada = postOk(SECCIONES, alta("B", 30, "aulaId", id(aula)));

        assertThat(creada.path("aula").path("id").asLong()).isEqualTo(id(aula));
        assertThat(creada.path("aula").path("nombre").asText()).isEqualTo(aula.path("nombre").asText());
        assertThat(creada.path("aula").path("capacidad").asInt()).isEqualTo(30);
        assertThat(getOk(SECCIONES + "/" + id(creada)).path("aula").path("id").asLong()).isEqualTo(id(aula));
        assertThat(getOk("/api/configuracion-academica/anios/2090").path("secciones"))
                .filteredOn(s -> s.path("id").asLong() == id(creada))
                .singleElement()
                .satisfies(s -> assertThat(s.path("aula").path("id").asLong()).isEqualTo(id(aula)));
        assertThat(auditoria(id(creada))).anySatisfy(r -> assertThat(r.getNuevoValor())
                .isEqualTo("grado=1,capacidad=30,aula=" + aula.path("codigo").asText()));
    }

    @Test
    void elAulaEsOpcionalYVariasSeccionesPuedenCompartirla() throws Exception {
        assertThat(postOk(SECCIONES, alta("B", 30)).path("aula").isNull()).isTrue();
        assertThat(postOk(SECCIONES, alta("C", 30, "aulaId", null)).path("aula").isNull()).isTrue();

        JsonNode aula = aula(35);
        JsonNode primera = postOk(SECCIONES, alta("D", 35, "aulaId", id(aula)));
        JsonNode segunda = postOk(SECCIONES, alta("E", 20, "aulaId", id(aula)));

        assertThat(primera.path("aula").path("id").asLong()).isEqualTo(id(aula));
        assertThat(segunda.path("aula").path("id").asLong()).isEqualTo(id(aula));
    }

    @Test
    void rechazaUnAulaPequenaInhabilitadaOInexistenteSinGuardar() throws Exception {
        JsonNode pequena = aula(29);

        JsonNode rechazo = request("POST", SECCIONES, alta("B", 30, "aulaId", id(pequena)), 422);
        assertThat(rechazo.path("code").asText()).isEqualTo("AULA_CAPACIDAD_INSUFICIENTE");
        assertThat(rechazo.path("message").asText()).isEqualTo("El aula " + pequena.path("nombre").asText()
                + " admite 29 estudiantes y la sección necesita capacidad para 30.");
        assertThat(rechazo.path("fieldErrors").path(0).path("field").asText()).isEqualTo("aulaId");

        JsonNode cerrada = aula(40);
        inhabilitar(id(cerrada), 200);
        assertThat(request("POST", SECCIONES, alta("B", 30, "aulaId", id(cerrada)), 422)
                .path("code").asText()).isEqualTo("AULA_INHABILITADA");
        assertThat(request("POST", SECCIONES, alta("B", 30, "aulaId", 999999), 404)
                .path("code").asText()).isEqualTo("AULA_NO_ENCONTRADA");

        assertThat(getOk(SECCIONES + "?anioAcademico=2090").path("content"))
                .noneSatisfy(s -> assertThat(s.path("nombre").asText()).isEqualTo("B"));
    }

    @Test
    void unPutQueOmiteOAnulaElAulaLaConservaYSoloQuitarAulaLaRetira() throws Exception {
        JsonNode primera = aula(30);
        JsonNode segunda = aula(40);
        long seccion = section.getId();

        assertThat(editar(seccion, 200, "aulaId", id(primera)).path("aula").path("id").asLong())
                .isEqualTo(id(primera));
        assertThat(editar(seccion, 200).path("aula").path("id").asLong()).isEqualTo(id(primera));
        assertThat(editar(seccion, 200, "aulaId", null, "quitarAula", false).path("aula").path("id").asLong())
                .isEqualTo(id(primera));
        assertThat(editar(seccion, 200, "aulaId", id(segunda)).path("aula").path("id").asLong())
                .isEqualTo(id(segunda));

        JsonNode contradictoria = editar(seccion, 422, "aulaId", id(primera), "quitarAula", true);
        assertThat(contradictoria.path("code").asText()).isEqualTo("AULA_SECCION_CONTRADICTORIA");
        assertThat(getOk(SECCIONES + "/" + seccion).path("aula").path("id").asLong()).isEqualTo(id(segunda));

        assertThat(editar(seccion, 200, "quitarAula", true).path("aula").isNull()).isTrue();
        assertThat(auditoria(seccion)).last().satisfies(r -> {
            assertThat(r.getValorAnterior()).endsWith("aula=" + segunda.path("codigo").asText());
            assertThat(r.getNuevoValor()).endsWith("aula=ninguna");
        });
    }

    @Test
    void alEditarRevalidaLaCapacidadYElAulaElegida() throws Exception {
        JsonNode justa = aula(30);
        JsonNode pequena = aula(25);
        long seccion = section.getId();
        editar(seccion, 200, "aulaId", id(justa));

        // Ampliar la sección sin tocar el aula actual también aplica la regla.
        assertThat(editar(seccion, 422, "capacidadMaxima", 31).path("code").asText())
                .isEqualTo("AULA_CAPACIDAD_INSUFICIENTE");
        assertThat(editar(seccion, 422, "aulaId", id(pequena)).path("code").asText())
                .isEqualTo("AULA_CAPACIDAD_INSUFICIENTE");
        JsonNode actual = getOk(SECCIONES + "/" + seccion);
        assertThat(actual.path("capacidadMaxima").asInt()).isEqualTo(30);
        assertThat(actual.path("aula").path("id").asLong()).isEqualTo(id(justa));

        // La regla compara con la capacidad nueva: al reducir la sección, el aula pequeña sirve.
        JsonNode reducida = editar(seccion, 200, "capacidadMaxima", 25, "aulaId", id(pequena));
        assertThat(reducida.path("aula").path("id").asLong()).isEqualTo(id(pequena));
        assertThat(editar(seccion, 200, "capacidadMaxima", 40, "quitarAula", true).path("aula").isNull())
                .isTrue();
    }

    @Test
    void rehabilitarUnaSeccionExigeSuAulaHabilitadaYConCapacidad() throws Exception {
        JsonNode aula = aula(30);
        long seccion = id(postOk(SECCIONES, alta("B", 30, "aulaId", id(aula))));
        cambiarEstado(seccion, false, 200);
        // Inactiva la sección, el aula ya puede reducirse o inhabilitarse.
        editarAula(id(aula), 20, 200);

        assertThat(cambiarEstado(seccion, true, 422).path("code").asText())
                .isEqualTo("AULA_CAPACIDAD_INSUFICIENTE");

        editarAula(id(aula), 30, 200);
        inhabilitar(id(aula), 200);
        assertThat(cambiarEstado(seccion, true, 422).path("code").asText()).isEqualTo("AULA_INHABILITADA");
        assertThat(getOk(SECCIONES + "?activo=false&anioAcademico=2090").path("content"))
                .anySatisfy(s -> assertThat(s.path("id").asLong()).isEqualTo(seccion));
    }

    @Test
    void rehabilitaConAulaSuficienteOSinAula() throws Exception {
        JsonNode aula = aula(30);
        long conAula = id(postOk(SECCIONES, alta("B", 30, "aulaId", id(aula))));
        long sinAula = id(postOk(SECCIONES, alta("C", 30)));
        for (long seccion : new long[] {conAula, sinAula}) {
            cambiarEstado(seccion, false, 200);
            assertThat(cambiarEstado(seccion, true, 200).path("activo").asBoolean()).isTrue();
        }
    }

    @Test
    void unAulaAsignadaNoSeReduceNiSeInhabilitaNiSeElimina() throws Exception {
        JsonNode aula = aula(40);
        long seccion = section.getId();
        editar(seccion, 200, "aulaId", id(aula));

        JsonNode reduccion = editarAula(id(aula), 29, 409);
        assertThat(reduccion.path("code").asText()).isEqualTo("AULA_CAPACIDAD_INSUFICIENTE");
        assertThat(reduccion.path("message").asText()).isEqualTo(
                "La capacidad no puede ser menor que 30 porque el aula está asignada a la sección 1° A (2090).");
        assertThat(reduccion.path("details").path(0).path("seccionId").asLong()).isEqualTo(seccion);
        assertThat(reduccion.path("details").path(0).path("capacidadMaximaSeccion").asInt()).isEqualTo(30);

        JsonNode baja = inhabilitar(id(aula), 409);
        assertThat(baja.path("code").asText()).isEqualTo("AULA_ASIGNADA_A_SECCION");
        assertThat(baja.path("message").asText()).contains("inhabilitar", "1° A (2090)");

        JsonNode borrado = request("DELETE", AULAS + "/" + id(aula), null, 409);
        assertThat(borrado.path("code").asText()).isEqualTo("AULA_ASIGNADA_A_SECCION");
        assertThat(borrado.path("message").asText()).contains("eliminar", "1° A (2090)");

        // Hasta el límite sí se reduce; sin la asignación, el aula se puede retirar.
        editarAula(id(aula), 30, 200);
        assertThat(getOk(AULAS + "/" + id(aula)).path("activo").asBoolean()).isTrue();
        editar(seccion, 200, "quitarAula", true);
        editarAula(id(aula), 10, 200);
        inhabilitar(id(aula), 200);
        deleteOk(AULAS + "/" + id(aula));
    }

    @Test
    void elMensajeResumeCuandoMuchasSeccionesCompartenElAula() throws Exception {
        JsonNode aula = aula(40);
        for (String nombre : new String[] {"B", "C", "D", "E"}) {
            postOk(SECCIONES, alta(nombre, nombre.equals("E") ? 35 : 30, "aulaId", id(aula)));
        }

        JsonNode reduccion = editarAula(id(aula), 20, 409);

        assertThat(reduccion.path("message").asText()).isEqualTo(
                "La capacidad no puede ser menor que 35 porque el aula está asignada a las secciones "
                        + "1° B (2090), 1° C (2090), 1° D (2090) y 1 más.");
        assertThat(reduccion.path("details")).hasSize(4);
    }

    @Test
    void enUnAnioCerradoSoloSeImpideEliminarElAula() throws Exception {
        JsonNode aula = aula(30);
        editar(section.getId(), 200, "aulaId", id(aula));
        year.setEstado(EstadoAnioAcademico.CERRADO);
        em.flush();

        editarAula(id(aula), 5, 200);
        inhabilitar(id(aula), 200);

        assertThat(request("DELETE", AULAS + "/" + id(aula), null, 409).path("code").asText())
                .isEqualTo("AULA_ASIGNADA_A_SECCION");
        assertThat(getOk(SECCIONES + "/" + section.getId()).path("aula").path("id").asLong())
                .isEqualTo(id(aula));
    }

    @Test
    void elAulaDeLaSeccionNoObligaAUsarlaEnSusHorarios() throws Exception {
        JsonNode propia = aula(30);
        JsonNode otra = aula(30);
        editar(section.getId(), 200, "aulaId", id(propia));

        JsonNode bloque = postOk("/api/horarios", data("asignacionCursoId", assignment.getId(),
                "aulaId", id(otra), "diaSemana", "LUNES", "horaInicio", "08:00", "horaFin", "09:00"));

        assertThat(bloque.path("aulaId").asLong()).isEqualTo(id(otra));
    }

    @Test
    void soloElAdministradorAsignaElAula() throws Exception {
        JsonNode aula = aula(30);
        byte[] alta = json.writeValueAsBytes(alta("B", 30, "aulaId", id(aula)));
        String edicion = SECCIONES + "/" + section.getId();

        mvc.perform(post(SECCIONES).contentType(MediaType.APPLICATION_JSON).content(alta))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(edicion).contentType(MediaType.APPLICATION_JSON).content(alta))
                .andExpect(status().isUnauthorized());
        for (String rol : new String[] {"DOCENTE", "ALUMNO", "APODERADO", "SECRETARIA"}) {
            var permisos = new ArrayList<SimpleGrantedAuthority>();
            permisos.add(new SimpleGrantedAuthority("ROLE_" + rol));
            permisos.add(new SimpleGrantedAuthority("ACADEMICO_LEER"));
            permisos.add(new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR"));
            var otro = jwt().authorities(permisos.toArray(SimpleGrantedAuthority[]::new));
            mvc.perform(post(SECCIONES).with(otro).contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isForbidden());
        }
    }

    private java.util.List<RegistroAuditoria> auditoria(long seccion) {
        return auditorias.findByEntidadAndRegistroIdOrderByFechaHoraAsc("Seccion", Long.toString(seccion));
    }
}
