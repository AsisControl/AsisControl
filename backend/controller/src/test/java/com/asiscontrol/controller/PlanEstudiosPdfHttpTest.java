package com.asiscontrol.controller;

import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanEstudiosPdfHttpTest extends ApiIntegrationSupport {
    private static final String RUTA = "/api/planes-estudios/2090/pdf";

    @Test
    void exportaCincoGradosConLosCursosYHorasGuardados() throws Exception {
        course.setNombre("Matemática");
        course.setCodigo("MAT-DEMO-1");
        course.setHorasSemanales(2);
        var nombres = List.of("Arte y Cultura", "Ciencia y Tecnología", "Ciencias Sociales",
                "Comunicación", "Educación Física", "Inglés");
        for (int numero = 1; numero <= 5; numero++) {
            var grado = numero == 1 ? grade : crearGrado(numero);
            var inicial = obtenerPlan(numero);
            var ids = new ArrayList<Long>();
            ids.add(numero == 1 ? course.getId() : curso("MAT-DEMO-" + numero, "Matemática", numero + 1, grado).getId());
            for (int i = 0; i < nombres.size(); i++) {
                ids.add(curso("PLAN-" + numero + "-" + i, nombres.get(i), 2, grado).getId());
            }
            putOk("/api/planes-estudios/2090/" + numero,
                    data("version", inicial.path("version").asLong(), "cursosIds", ids));
        }
        // La exportación solo debe mostrar cursos vinculados al plan, no todo el catálogo.
        curso("CATALOGO-NO-INCLUIDO", "Curso fuera del plan", 3, grade);
        em.flush();

        byte[] archivo = descargar();
        var pdf = new PdfReader(archivo);
        try {
            var texto = new PdfTextExtractor(pdf);
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(texto.getTextFromPage(1)).contains("2090", "5.º de secundaria", "Matemática", "18 h/sem")
                    .contains("MAT-DEMO-1", "MAT-DEMO-2", "MAT-DEMO-3", "MAT-DEMO-4", "MAT-DEMO-5")
                    .doesNotContain("BORRADOR DEL PLAN", "CATALOGO-NO-INCLUIDO");
            assertThat(texto.getTextFromPage(2)).contains("Condiciones para pasar de grado", "1.º, 3.º y 4.º",
                    "2.º y 5.º", "Recuperación pedagógica", "diciembre", "048-2024-MINEDU");
        } finally {
            pdf.close();
        }
        Path ejemplo = Path.of("target", "reportes-prueba", "plan-estudios.pdf");
        Files.createDirectories(ejemplo.getParent());
        Files.write(ejemplo, archivo);
    }

    @Test
    void marcaBorradorYConservaTodosLosCursosAlPaginar() throws Exception {
        year.setEstado(EstadoAnioAcademico.BORRADOR);
        grade.setPlanDefinido(true);
        grade.getCursosPlan().clear();
        for (int numero = 1; numero <= 12; numero++) {
            var guardado = curso("CURSO-PAG-" + numero, "Asignatura " + numero
                    + " con nombre largo para comprobar la distribución", 4, grade);
            grade.getCursosPlan().add(guardado);
        }
        var otroGrado = crearGrado(2);
        curso("SIN-PLAN-DEFINIDO", "Curso del catálogo de referencia", 2, otroGrado);
        em.flush();

        var pdf = new PdfReader(descargar());
        try {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            var texto = new PdfTextExtractor(pdf);
            var mapa = new StringBuilder();
            for (int i = 1; i < pdf.getNumberOfPages(); i++) mapa.append(texto.getTextFromPage(i));
            assertThat(mapa).contains("BORRADOR DEL PLAN", "Grado sin plan definido", "48 h/sem")
                    .doesNotContain("SIN-PLAN-DEFINIDO");
            for (int numero = 1; numero <= 12; numero++) {
                assertThat(mapa).contains("CURSO-PAG-" + numero);
            }
            assertThat(texto.getTextFromPage(pdf.getNumberOfPages())).contains("Condiciones para pasar de grado");
        } finally {
            pdf.close();
        }
    }

    @Test
    void exigeAdministradorConLecturaAcademicaYAnioExistente() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_DOCENTE"), new SimpleGrantedAuthority("ACADEMICO_LEER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/planes-estudios/2098/pdf").with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    private com.fasterxml.jackson.databind.JsonNode obtenerPlan(int grado) throws Exception {
        return getOk("/api/planes-estudios/2090/" + grado);
    }

    private byte[] descargar() throws Exception {
        return mvc.perform(get(RUTA).with(asAdmin())).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Plan_de_Estudios_AsisControl_2090.pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private GradoAcademico crearGrado(int numero) {
        var creado = new GradoAcademico();
        creado.setAnioAcademico(year);
        creado.setNumero(numero);
        creado.setCapacidadDefault(30);
        em.persist(creado);
        return creado;
    }

    private Curso curso(String codigo, String nombre, int horas, GradoAcademico grado) {
        var creado = new Curso();
        creado.setCodigo(codigo);
        creado.setNombre(nombre);
        creado.setHorasSemanales(horas);
        creado.setGradoAcademico(grado);
        em.persist(creado);
        return creado;
    }
}
