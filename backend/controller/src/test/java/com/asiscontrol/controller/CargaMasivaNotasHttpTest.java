package com.asiscontrol.controller;

import com.asiscontrol.entity.AccesoUsuario;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.CursoCompetencia;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.Rol;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoAcceso;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.Especialidad;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.ResultMatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OBS-05 · ASISCTRL-234/235: carga masiva de notas en la planilla del docente, con la plantilla
 * prellenada de la evaluación, un archivo válido, con errores y repetido, y los permisos.
 */
class CargaMasivaNotasHttpTest extends ApiIntegrationSupport {

    private static final List<String> COLUMNAS = List.of(
            "codigoAlumno", "alumno", "codigoCompetencia", "competencia", "nivelLogro", "observacion");

    private AccesoUsuario accesoDocente;
    private Competencia segunda;
    private Alumno companero;
    private long evaluacion;

    @BeforeEach
    void prepararEvaluacion() throws Exception {
        accesoDocente = acceso(teacher);
        segunda = new Competencia();
        segunda.setCodigo("CMP" + key());
        segunda.setNombre("Argumenta");
        em.persist(segunda);
        var vinculo = new CursoCompetencia();
        vinculo.setCurso(course);
        vinculo.setCompetencia(segunda);
        vinculo.setOrden(2);
        em.persist(vinculo);
        companero = alumno();
        matricular(companero);
        em.flush();
        evaluacion = postOk("/api/evaluaciones", data("idAsignacionCurso", assignment.getId(),
                "titulo", "Práctica " + key(), "tipoEvaluacion", "EXAMEN",
                "periodoEvaluacion", "BIMESTRAL", "fechaEvaluacion", LocalDate.now(),
                "ponderacionPorcentaje", 100, "idsCompetencia", Set.of(competence.getId(), segunda.getId())))
                .path("id").asLong();
    }

    @Test
    void laPlantillaTraeACadaAlumnoMatriculadoConLasCompetenciasDeLaEvaluacion() throws Exception {
        byte[] plantilla = mvc.perform(get(ruta() + "/plantilla").with(comoDocente()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(plantilla))) {
            Sheet hoja = libro.getSheet("Notas");
            assertThat(celdas(hoja.getRow(0))).containsExactlyElementsOf(COLUMNAS);
            List<String> claves = new ArrayList<>();
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                claves.add(hoja.getRow(i).getCell(0).getStringCellValue() + "|"
                        + hoja.getRow(i).getCell(2).getStringCellValue());
            }
            assertThat(claves).containsExactlyInAnyOrder(
                    student.getCodigoAlumno() + "|" + competence.getCodigo(),
                    student.getCodigoAlumno() + "|" + segunda.getCodigo(),
                    companero.getCodigoAlumno() + "|" + competence.getCodigo(),
                    companero.getCodigoAlumno() + "|" + segunda.getCodigo());
            assertThat(hoja.getDataValidations()).hasSize(1);
        }
    }

    @Test
    void registraLosNivelesConLasReglasDeLaPlanillaYSinDuplicar() throws Exception {
        Alumno sinMatricula = alumno();
        em.flush();
        String c1 = competence.getCodigo(), c2 = segunda.getCodigo();
        byte[] archivo = libro(
                List.of(student.getCodigoAlumno(), "", c1, "", "A", ""),
                List.of(student.getCodigoAlumno(), "", c2, "", "", ""),
                List.of(companero.getCodigoAlumno(), "", c1, "", "AD", "Muy bien"),
                List.of(companero.getCodigoAlumno(), "", c1.toLowerCase(), "", "B", ""),
                List.of(sinMatricula.getCodigoAlumno(), "", c1, "", "A", ""),
                List.of(student.getCodigoAlumno(), "", "OTRA", "", "A", ""),
                List.of(companero.getCodigoAlumno(), "", c2, "", "X", ""),
                List.of("NO-EXISTE", "", c1, "", "A", ""));

        JsonNode carga = importar(archivo, comoDocente(), status().isOk());
        assertThat(carga.path("creadas").asInt()).isEqualTo(2);
        assertThat(mensaje(carga.path("omitidas"), 3)).contains("Sin nivel");
        assertThat(mensaje(carga.path("omitidas"), 5)).contains("fila 4");
        assertThat(mensaje(carga.path("errores"), 6)).contains("matrícula");
        assertThat(campo(carga.path("errores"), 7)).isEqualTo("codigoCompetencia");
        assertThat(campo(carga.path("errores"), 8)).isEqualTo("nivelLogro");
        assertThat(campo(carga.path("errores"), 9)).isEqualTo("codigoAlumno");
        assertThat(nivel(student, competence)).isEqualTo("A");
        assertThat(nivel(companero, competence)).isEqualTo("AD");

        JsonNode repetida = importar(archivo, comoDocente(), status().isOk());
        assertThat(repetida.path("creadas").asInt()).isZero();
        assertThat(mensaje(repetida.path("omitidas"), 2)).contains("Ya tiene ese nivel");

        // En borrador, igual que en la planilla, un nivel se puede cambiar: se actualiza, no se duplica.
        JsonNode cambio = importar(libro(List.of(student.getCodigoAlumno(), "", c1, "", "B", "")),
                comoDocente(), status().isOk());
        assertThat(cambio.path("creadas").asInt()).isEqualTo(1);
        assertThat(nivel(student, competence)).isEqualTo("B");
        long notas = em.createQuery("select count(c) from Calificacion c where c.evaluacion.id = :e"
                        + " and c.alumno.id = :a and c.competencia.id = :c", Long.class)
                .setParameter("e", evaluacion).setParameter("a", student.getId())
                .setParameter("c", competence.getId()).getSingleResult();
        assertThat(notas).isEqualTo(1);
    }

    @Test
    void soloElDocenteDeLaAsignacionCargaYSoloEnBorrador() throws Exception {
        byte[] archivo = libro(List.of(student.getCodigoAlumno(), "", competence.getCodigo(), "", "A", ""));
        AccesoUsuario ajeno = acceso(otroDocente());
        importar(archivo, como(ajeno, "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR"), status().isForbidden());
        mvc.perform(get(ruta() + "/plantilla").with(como(ajeno, "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR")))
                .andExpect(status().isForbidden());
        importar(archivo, como(accesoDocente, "CALIFICACIONES_LEER"), status().isForbidden());

        importar(libro(List.of(student.getCodigoAlumno(), "", competence.getCodigo(), "", "A", ""),
                List.of(companero.getCodigoAlumno(), "", competence.getCodigo(), "", "A", ""),
                List.of(student.getCodigoAlumno(), "", segunda.getCodigo(), "", "A", ""),
                List.of(companero.getCodigoAlumno(), "", segunda.getCodigo(), "", "A", "")),
                comoDocente(), status().isOk());
        request("PUT", "/api/evaluaciones/" + evaluacion + "/estado", data("estado", "PUBLICADA"), 200);
        // Lo publicado se corrige con motivo en la planilla, no por carga masiva.
        importar(archivo, comoDocente(), status().is4xxClientError());
    }

    // ---------- Apoyo ----------

    private String ruta() {
        return "/api/evaluaciones/" + evaluacion + "/calificaciones/importaciones";
    }

    private String nivel(Alumno alumno, Competencia competencia) {
        return em.createQuery("select c.nivelLogro from Calificacion c where c.evaluacion.id = :e"
                        + " and c.alumno.id = :a and c.competencia.id = :c and c.activo = true", Object.class)
                .setParameter("e", evaluacion).setParameter("a", alumno.getId())
                .setParameter("c", competencia.getId()).getSingleResult().toString();
    }

    private JsonNode importar(byte[] contenido, JwtRequestPostProcessor quien, ResultMatcher esperado)
            throws Exception {
        String cuerpo = mvc.perform(multipart(ruta())
                        .file(new MockMultipartFile("archivo", "notas.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido))
                        .param("confirmar", "true")
                        .with(quien))
                .andExpect(esperado)
                .andReturn().getResponse().getContentAsString();
        return cuerpo.isBlank() ? json.nullNode() : json.readTree(cuerpo);
    }

    private Alumno alumno() {
        Alumno alumno = new Alumno();
        alumno.setPersona(person());
        alumno.setCodigoAlumno("NT" + key());
        em.persist(alumno);
        return alumno;
    }

    private void matricular(Alumno alumno) {
        var solicitud = new SolicitudMatricula();
        solicitud.setAlumno(alumno);
        solicitud.setSeccion(section);
        solicitud.setEstado(EstadoSolicitudMatricula.MATRICULA_FINALIZADA);
        em.persist(solicitud);
        var matricula = new Matricula();
        matricula.setAlumno(alumno);
        matricula.setSeccion(section);
        matricula.setSolicitudMatricula(solicitud);
        matricula.setCodigoMatricula("MAT" + key());
        matricula.setAnioAcademico(2090);
        matricula.setFechaMatricula(Instant.now());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        em.persist(matricula);
    }

    private Docente otroDocente() {
        Docente otro = new Docente();
        otro.setPersona(person());
        otro.setCodigoDocente("DOC" + key());
        otro.setFechaIngreso(LocalDate.now().minusYears(1));
        otro.setEspecialidad(Especialidad.COMUNICACION);
        em.persist(otro);
        return otro;
    }

    private AccesoUsuario acceso(Docente docente) {
        AccesoUsuario acceso = new AccesoUsuario();
        acceso.setPersona(docente.getPersona());
        acceso.setUsername("docente" + key());
        acceso.setCodigo(acceso.getUsername());
        acceso.setCorreo(key() + "@example.test");
        acceso.setContrasenaHash("hash-exclusivo-de-prueba");
        acceso.setEstado(EstadoAcceso.ACTIVO);
        acceso.setRequiereCambioContrasena(false);
        em.persist(acceso);
        asignarRol(acceso, em.createQuery("from Rol r where r.nombre = 'DOCENTE'", Rol.class).getSingleResult());
        em.flush();
        return acceso;
    }

    private JwtRequestPostProcessor comoDocente() {
        return como(accesoDocente, "CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR");
    }

    private JwtRequestPostProcessor como(AccesoUsuario acceso, String... permisos) {
        List<GrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_DOCENTE"));
        for (String permiso : permisos) {
            autoridades.add(new SimpleGrantedAuthority(permiso));
        }
        return jwt().jwt(token -> token.subject(acceso.getUsername())
                .claim("personaId", acceso.getPersona().getId())
                .claim("rol", "DOCENTE").claim("requiereCambioContrasena", false)).authorities(autoridades);
    }

    private static JsonNode issue(JsonNode lista, int fila) {
        for (JsonNode item : lista) {
            if (item.path("row").asInt() == fila) {
                return item;
            }
        }
        throw new AssertionError("Sin observación para la fila " + fila + ": " + lista);
    }

    private static String campo(JsonNode lista, int fila) {
        return issue(lista, fila).path("field").asText();
    }

    private static String mensaje(JsonNode lista, int fila) {
        return issue(lista, fila).path("message").asText();
    }

    @SafeVarargs
    private static byte[] libro(List<String>... filas) throws Exception {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Notas");
            escribir(hoja.createRow(0), COLUMNAS);
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

    private static List<String> celdas(Row fila) {
        List<String> valores = new ArrayList<>();
        fila.forEach(celda -> valores.add(celda.getStringCellValue()));
        return valores;
    }
}
