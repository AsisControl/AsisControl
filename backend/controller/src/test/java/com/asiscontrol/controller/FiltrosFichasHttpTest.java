package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FiltrosFichasHttpTest extends ApiIntegrationSupport {

    @Test void filtraEstudiantesPorMatriculaGradoYApoderado() throws Exception {
        var sinNada = new Alumno();
        sinNada.setPersona(person());
        sinNada.setCodigoAlumno("SIN" + key());
        em.persist(sinNada);
        em.flush();
        String buscado = "/api/alumnos?search=" + sinNada.getCodigoAlumno();
        String matriculado = "/api/alumnos?search=" + student.getCodigoAlumno();

        assertThat(total(buscado + "&conMatricula=false&conApoderado=false")).isOne();
        assertThat(total(buscado + "&conMatricula=true")).isZero();
        assertThat(total(buscado + "&conApoderado=true")).isZero();
        assertThat(total(matriculado + "&conMatricula=true&grado=1&conApoderado=true")).isOne();
        assertThat(total(matriculado + "&grado=2")).isZero();
        assertThat(total(matriculado + "&conMatricula=false")).isZero();
    }

    @Test void filtraApoderadosPorEstudiantesVinculados() throws Exception {
        String buscado = "/api/apoderados?search=" + guardian.getPersona().getNumeroDocumento();
        assertThat(total(buscado + "&conEstudiantes=true")).isOne();
        assertThat(total(buscado + "&conEstudiantes=false")).isZero();
    }

    private int total(String path) throws Exception {
        return getOk(path).path("content").size();
    }
}
