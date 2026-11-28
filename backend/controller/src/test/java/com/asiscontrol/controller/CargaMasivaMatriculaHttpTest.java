package com.asiscontrol.controller;

import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Seccion;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OBS-05 · ASISCTRL-232/233/235: carga masiva de tarifas y de matrículas con sección sobre el año
 * activo de prueba (2090). Solo puede haber un año activo, por eso esta prueba usa la transacción
 * de {@link ApiIntegrationSupport}: cubre la importación, los errores y la recarga del mismo
 * archivo. La revisión sin guardar ya está probada en las demás cargas con la misma base común.
 */
class CargaMasivaMatriculaHttpTest extends ApiIntegrationSupport {

    private static final String TARIFAS = "/api/configuracion-pagos/tarifas/importaciones";
    private static final String MATRICULAS = "/api/matriculas/importaciones";
    private static final List<String> COLUMNAS_TARIFA =
            List.of("codigoConcepto", "anioAcademico", "grado", "montoMensual");
    private static final List<String> COLUMNAS_MATRICULA =
            List.of("codigoAlumno", "anioAcademico", "grado", "seccion", "observaciones");

    @Test
    void cargaTarifasConLasReglasDelRegistroManualYSinDuplicar() throws Exception {
        byte[] archivo = libro(COLUMNAS_TARIFA,
                List.of("PENSION", "2090", "1", "400.00"),
                List.of("PENSION", "2090", "2", "abc"),
                List.of("NO-EXISTE", "2090", "1", "100"),
                List.of("pension", "2090", "1", "500"),
                List.of("PENSION", "2090", "3", "0"));

        JsonNode carga = importar(TARIFAS, archivo);
        assertThat(carga.path("creadas").asInt()).isEqualTo(1);
        assertThat(campo(carga.path("errores"), 3)).isEqualTo("montoMensual");
        assertThat(campo(carga.path("errores"), 4)).isEqualTo("codigoConcepto");
        assertThat(campo(carga.path("errores"), 6)).isEqualTo("montoMensual");
        assertThat(campo(carga.path("omitidas"), 5)).isEqualTo("grado");
        JsonNode tarifa = getOk("/api/configuracion-pagos/tarifas?anioAcademico=2090").get(0);
        assertThat(tarifa.path("montoMensual").decimalValue()).isEqualByComparingTo("400.00");

        JsonNode repetida = importar(TARIFAS, archivo);
        assertThat(repetida.path("creadas").asInt()).isZero();
        assertThat(repetida.path("omitidas")).hasSize(2);
        assertThat(getOk("/api/configuracion-pagos/tarifas?anioAcademico=2090")).hasSize(1);
    }

    @Test
    void matriculaConSeccionCuposYCuotasComoLaSecretariaYSinDuplicar() throws Exception {
        importar(TARIFAS, libro(COLUMNAS_TARIFA, List.of("PENSION", "2090", "1", "400.00")));
        Seccion chica = new Seccion();
        chica.setNombre("B");
        chica.setGrado(1);
        chica.setAnioAcademico(2090);
        chica.setCapacidadMaxima(1);
        chica.setGradoAcademico(grade);
        em.persist(chica);
        Alumno primero = alumnoNuevo(), segundo = alumnoNuevo(), tercero = alumnoNuevo();
        em.flush();

        byte[] archivo = libro(COLUMNAS_MATRICULA,
                List.of(primero.getCodigoAlumno(), "2090", "1", "A", "Documentos completos"),
                // Ya matriculado en 2090 por el fixture.
                List.of(student.getCodigoAlumno(), "2090", "1", "A", ""),
                List.of(segundo.getCodigoAlumno(), "2090", "1", "B", ""),
                // La sección B solo tiene un cupo.
                List.of(tercero.getCodigoAlumno(), "2090", "1", "B", ""),
                List.of("NO-EXISTE", "2090", "1", "A", ""),
                List.of(tercero.getCodigoAlumno(), "2090", "1", "Z", ""));

        JsonNode carga = importar(MATRICULAS, archivo);
        assertThat(carga.path("creadas").asInt()).as(carga.toString()).isEqualTo(2);
        assertThat(campo(carga.path("omitidas"), 3)).isEqualTo("codigoAlumno");
        assertThat(carga.path("errores").toString()).contains("\"row\":5");
        assertThat(campo(carga.path("errores"), 6)).isEqualTo("codigoAlumno");
        assertThat(campo(carga.path("errores"), 7)).isEqualTo("seccion");

        JsonNode matricula = matriculaDe(primero);
        assertThat(matricula.path("seccionId").asLong()).isEqualTo(section.getId());
        assertThat(matricula.path("estado").asText()).isEqualTo("ACTIVA");
        // Al matricular se generan las cuotas del plan, como en el flujo manual.
        long cuotas = em.createQuery(
                        "select count(c) from CompromisoPago c where c.matricula.alumno.id = :alumno", Long.class)
                .setParameter("alumno", primero.getId()).getSingleResult();
        assertThat(cuotas).isEqualTo(10);
        assertThat(matriculaDe(tercero)).isNull();

        JsonNode repetida = importar(MATRICULAS, archivo);
        assertThat(repetida.path("creadas").asInt()).isZero();
        assertThat(repetida.path("omitidas")).hasSize(3);
    }

    @Test
    void cadaCargaExigeElPermisoDelRegistroManual() throws Exception {
        byte[] archivo = libro(COLUMNAS_MATRICULA, List.of("X", "2090", "1", "A", ""));
        // La secretaría registra tarifas, pero solo el administrador finaliza matrículas.
        var secretaria = jwt().authorities(new SimpleGrantedAuthority("ROLE_SECRETARIA"),
                new SimpleGrantedAuthority("MATRICULA_LEER"), new SimpleGrantedAuthority("MATRICULA_ESCRIBIR"));
        mvc.perform(multipart(MATRICULAS).file(archivo(archivo)).with(secretaria))
                .andExpect(status().isForbidden());
        var docente = jwt().authorities(new SimpleGrantedAuthority("ROLE_DOCENTE"),
                new SimpleGrantedAuthority("PAGOS_LEER"));
        mvc.perform(multipart(TARIFAS).file(archivo(archivo)).with(docente))
                .andExpect(status().isForbidden());
    }

    // ---------- Apoyo ----------

    private Alumno alumnoNuevo() {
        var alumno = new Alumno();
        alumno.setPersona(person());
        alumno.setCodigoAlumno("CM" + key());
        em.persist(alumno);
        return alumno;
    }

    private JsonNode matriculaDe(Alumno alumno) throws Exception {
        for (JsonNode item : getOk("/api/matriculas?size=200").path("content")) {
            if (item.path("alumnoId").asLong() == alumno.getId()) {
                return item;
            }
        }
        return null;
    }

    private JsonNode importar(String ruta, byte[] contenido) throws Exception {
        return json.readTree(mvc.perform(multipart(ruta)
                        .file(archivo(contenido))
                        .param("confirmar", "true")
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static String campo(JsonNode lista, int fila) {
        for (JsonNode item : lista) {
            if (item.path("row").asInt() == fila) {
                return item.path("field").asText();
            }
        }
        throw new AssertionError("Sin observación para la fila " + fila + ": " + lista);
    }

    private static MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("archivo", "carga.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido);
    }

    @SafeVarargs
    private static byte[] libro(List<String> columnas, List<String>... filas) throws Exception {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Carga");
            escribir(hoja.createRow(0), columnas);
            for (int i = 0; i < filas.length; i++) {
                escribir(hoja.createRow(i + 1), filas[i]);
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static void escribir(Row fila, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) {
            fila.createCell(i).setCellValue(valores.get(i));
        }
    }
}
