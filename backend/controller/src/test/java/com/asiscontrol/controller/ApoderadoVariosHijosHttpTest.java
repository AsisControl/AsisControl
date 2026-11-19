package com.asiscontrol.controller;

import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.support.TarifasTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recorrido HTTP con login real: un acceso, dos hijos y dos grados, sin crear otra cuenta. */
class ApoderadoVariosHijosHttpTest extends ApiIntegrationSupport {
    @Test void unaCuentaMatriculaDosHijosDeDistintosGradosSinDuplicarIdentidad() throws Exception {
        var guardianData = persona("Carmen", "1984-07-21");
        var apoderado = postOk("/api/apoderados", data("persona", guardianData));
        long personaId = apoderado.path("persona").path("id").asLong();
        long apoderadoId = apoderado.path("id").asLong();
        var primero = postOk("/api/alumnos", data("persona", persona("Mateo", "2013-04-10")));
        long rol = em.createQuery("select r.id from Rol r where r.nombre='APODERADO'", Long.class).getSingleResult();
        var cuenta = postOk("/api/usuarios/provisionar", data("personaId", personaId, "rolId", rol,
                "password", "Temporal2026Clave!", "alumnoIds", java.util.List.of(primero.path("id").asLong())));
        assertThat(cuenta.path("personaId").asLong()).isEqualTo(personaId);
        assertThat(contar("select count(p) from Persona p where p.numeroDocumento='" + guardianData.get("numeroDocumento") + "'" )).isEqualTo(1);

        String username = cuenta.path("username").asText();
        String token = login(username, "Temporal2026Clave!").path("accessToken").asText();
        familiar(token, "POST", "/api/auth/change-password",
                data("currentPassword", "Temporal2026Clave!", "newPassword", "Definitiva2026Clave!"));
        var sesion = login(username, "Definitiva2026Clave!");
        token = sesion.path("accessToken").asText();
        assertThat(sesion.path("user").path("id").asLong()).isEqualTo(cuenta.path("id").asLong());
        assertThat(familiar(token, "GET", "/api/familia/estudiantes", null)).hasSize(1);
        matricular(token, primero.path("id").asLong(), section);
        long usuariosAntes = contar("select count(u) from AccesoUsuario u");

        var segundo = postOk("/api/alumnos", data("persona", persona("Lucia", "2011-06-15")));
        long alumno2 = segundo.path("id").asLong();
        postOk("/api/alumnos/" + alumno2 + "/apoderados", data("apoderadoId", apoderadoId, "parentesco", "MADRE", "principal", true));
        var grado2 = new GradoAcademico(); grado2.setAnioAcademico(year); grado2.setNumero(2); grado2.setCapacidadDefault(30); em.persist(grado2);
        var seccion2 = new Seccion(); seccion2.setNombre("A"); seccion2.setGrado(2); seccion2.setAnioAcademico(2090); seccion2.setCapacidadMaxima(30); em.persist(seccion2); em.flush();
        var hijos = familiar(token, "GET", "/api/familia/estudiantes", null);
        assertThat(hijos.findValuesAsText("id")).containsExactlyInAnyOrder(primero.path("id").asText(), segundo.path("id").asText());
        matricular(token, alumno2, seccion2);

        assertThat(contar("select count(u) from AccesoUsuario u")).isEqualTo(usuariosAntes);
        assertThat(contar("select count(u) from AccesoUsuario u where u.persona.id=" + personaId + " and u.rol.nombre='APODERADO'")).isEqualTo(1);
        assertThat(contar("select count(a) from Apoderado a where a.persona.id=" + personaId)).isEqualTo(1);
        assertThat(contar("select count(v) from AlumnoApoderado v where v.apoderado.id=" + apoderadoId)).isEqualTo(2);
        assertThat(contar("select count(p) from Persona p where p.numeroDocumento='" + guardianData.get("numeroDocumento") + "'")).isEqualTo(1);
        assertThat(contar("select count(a) from Alumno a where a.id in (" + primero.path("id").asLong() + "," + alumno2 + ")")).isEqualTo(2);
        var grados = em.createQuery("select m.seccion.grado from Matricula m where m.alumno.id in (:primero,:segundo)", Integer.class)
                .setParameter("primero", primero.path("id").asLong()).setParameter("segundo", alumno2).getResultList();
        assertThat(grados).containsExactlyInAnyOrder(1, 2);
        assertThat(familiar(token, "GET", "/api/auth/me", null).path("id").asLong()).isEqualTo(cuenta.path("id").asLong());
        mvc.perform(get("/api/alumnos/" + student.getId()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    private java.util.Map<String,Object> persona(String nombre, String nacimiento) {
        return data("nombres", nombre, "apellidoPaterno", "Ramos", "apellidoMaterno", "Quispe",
                "tipoDocumento", "PASAPORTE", "numeroDocumento", key().toUpperCase(java.util.Locale.ROOT), "sexo", "FEMENINO",
                "fechaNacimiento", nacimiento, "correo", key() + "@familia.test");
    }
    private long contar(String hql) { return em.createQuery(hql, Long.class).getSingleResult(); }
    private JsonNode login(String usuario, String password) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(data("identifier", usuario, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
    private JsonNode familiar(String token, String method, String path, Object body) throws Exception {
        var builder = switch (method) { case "POST" -> post(path); case "PUT" -> put(path); default -> get(path); };
        builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response = mvc.perform(builder).andExpect(status().is(method.equals("POST") && path.equals("/api/solicitudes-matricula") ? 201 : 200)).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
    private void matricular(String token, long alumnoId, Seccion destino) throws Exception {
        TarifasTestSupport.configurar(em, 2090, destino.getGrado());
        var solicitud = familiar(token, "POST", "/api/solicitudes-matricula", data("alumnoId", alumnoId, "anioAcademico", 2090, "grado", destino.getGrado()));
        String path = "/api/solicitudes-matricula/" + solicitud.path("id").asLong();
        var enviada = familiar(token, "PUT", path, data("estado", "ENVIADA", "version", solicitud.path("version").asLong()));
        var revision = putOk(path, data("estado", "EN_REVISION", "version", enviada.path("version").asLong()));
        var asignada = putOk(path, data("seccionId", destino.getId(), "version", revision.path("version").asLong()));
        var plan = getOk("/api/configuracion-pagos/planes/solicitudes/" + solicitud.path("id").asLong() + "/preview");
        var matricula = postOk("/api/matriculas", data("solicitudMatriculaId", solicitud.path("id").asLong(), "versionSolicitud", asignada.path("version").asLong(), "firmaPlan", plan.path("firmaPlan").asText()));
        assertThat(matricula.path("alumnoId").asLong()).isEqualTo(alumnoId);
        assertThat(matricula.path("grado").asInt()).isEqualTo(destino.getGrado());
    }
}
