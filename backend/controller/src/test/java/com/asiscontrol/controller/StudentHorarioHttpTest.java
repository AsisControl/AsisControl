package com.asiscontrol.controller;

import com.asiscontrol.entity.Aula;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Horario;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.TipoAula;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentHorarioHttpTest extends ApiIntegrationSupport {
    private Horario bloque(AsignacionCurso asignacion) {
        Aula aula = new Aula();
        aula.setCodigo("AULA" + key()); aula.setNombre("Aula de prueba");
        aula.setTipoAula(TipoAula.AULA_REGULAR); aula.setCapacidad(30);
        aula.setUbicacion("Pabellón de prueba");
        em.persist(aula);
        Horario bloque = new Horario();
        bloque.setAsignacionCurso(asignacion); bloque.setAula(aula);
        bloque.setDiaSemana(DiaSemana.LUNES);
        bloque.setHoraInicio(LocalTime.of(8, 0)); bloque.setHoraFin(LocalTime.of(9, 0));
        em.persist(bloque);
        return bloque;
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor alumno() {
        return jwt().jwt(j -> j.subject("alumno" + student.getId())
                .claim("personaId", student.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_ALUMNO"),
                        new SimpleGrantedAuthority("ACADEMICO_LEER"));
    }

    @Test void estudianteSoloVeBloquesDeSuMatriculaActiva() throws Exception {
        Horario propio = bloque(assignment);
        Seccion ajena = new Seccion(); ajena.setNombre("B"); ajena.setGrado(1);
        ajena.setAnioAcademico(2090); ajena.setCapacidadMaxima(30); em.persist(ajena);
        AsignacionCurso otra = new AsignacionCurso(); otra.setCurso(course);
        otra.setDocente(teacher); otra.setSeccion(ajena); em.persist(otra);
        Horario ajeno = bloque(otra);
        em.flush();

        var result = mvc.perform(get("/api/horarios/mio?anioAcademico=2090").with(alumno()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var blocks = json.readTree(result.getContentAsByteArray());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).path("id").asLong()).isEqualTo(propio.getId());
        mvc.perform(get("/api/horarios").with(alumno())).andExpect(status().isForbidden());
        mvc.perform(get("/api/horarios/" + ajeno.getId()).with(alumno()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/horarios/" + propio.getId()).with(jwt()
                .jwt(j -> j.claim("personaId", student.getPersona().getId()))
                .authorities(new SimpleGrantedAuthority("ROLE_ALUMNO"),
                        new SimpleGrantedAuthority("ACADEMICO_LEER"),
                        new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR"))))
                .andExpect(status().isForbidden());
    }

    @Test void matriculaRetiradaNoDaAccesoAlHorario() throws Exception {
        bloque(assignment);
        Matricula matricula = em.createQuery("from Matricula m where m.alumno.id = :id", Matricula.class)
                .setParameter("id", student.getId()).getSingleResult();
        matricula.setEstado(EstadoMatricula.RETIRADA);
        em.flush();
        var result = mvc.perform(get("/api/horarios/mio?anioAcademico=2090").with(alumno()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(result.getContentAsByteArray())).isEmpty();
    }
}
