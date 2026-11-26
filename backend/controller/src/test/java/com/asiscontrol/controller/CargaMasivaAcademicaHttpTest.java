package com.asiscontrol.controller;

import com.asiscontrol.entity.Permiso;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.HorarioRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OBS-05 · ASISCTRL-230/231/235: carga masiva del plan de estudios, las asignaciones docentes y
 * los horarios, en el orden en que se cargan. Cada plantilla se prueba con un archivo válido, con
 * errores y repetido. Sin transacción en la prueba: la revisión debe deshacerse de verdad.
 */
@SpringBootTest
@ActiveProfiles("test")
class CargaMasivaAcademicaHttpTest {

    private static final String PLAN = "/api/planes-estudios/importaciones";
    private static final String ASIGNACIONES = "/api/asignaciones-cursos/importaciones";
    private static final String HORARIOS = "/api/horarios/importaciones";
    private static final List<String> COLUMNAS_PLAN = List.of(
            "anioAcademico", "grado", "codigoCurso", "nombreCurso", "horasSemanales",
            "codigosCompetencias", "descripcion", "motivo");
    private static final List<String> COLUMNAS_ASIGNACION =
            List.of("anioAcademico", "grado", "seccion", "codigoCurso", "codigoDocente");
    private static final List<String> COLUMNAS_HORARIO = List.of(
            "anioAcademico", "grado", "seccion", "codigoCurso", "codigoAula",
            "diaSemana", "horaInicio", "horaFin");
    private static final List<String> COLUMNAS_DOCENTE = List.of(
            "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento", "nacionalidad", "fechaIngreso",
            "especialidad", "telefono", "correo", "direccion");
    /** Año que ninguna otra prueba usa: aquí los datos sí quedan guardados. */
    private static final int ANIO = 2068;
    private static final String A = String.valueOf(ANIO);

    @Autowired WebApplicationContext context;
    @Autowired AnioAcademicoRepository anios;
    @Autowired CursoRepository cursos;
    @Autowired AsignacionCursoRepository asignaciones;
    @Autowired HorarioRepository horarios;
    @Autowired EntityManager em;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void preparar() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (anios.findByAnio(ANIO).isEmpty()) {
            crear("/api/configuracion-academica/anios",
                    Map.of("anio", ANIO, "fechaInicio", A + "-03-01", "fechaFin", A + "-12-20"));
            crear("/api/configuracion-academica/anios/" + ANIO + "/grados",
                    Map.of("numero", 1, "capacidadDefault", 30));
        }
    }

    @Test
    void cargaPlanAsignacionesYHorariosConLasReglasDelRegistroManual() throws Exception {
        String s = clave().substring(0, 5).toUpperCase();
        String mat = "MAT" + s, com = "COM" + s, cie = "CIE" + s, his = "HIS" + s;
        String competencia = "CP" + s;
        crear("/api/competencias", Map.of("codigo", competencia, "nombre", "Resuelve problemas"));
        String secA = "A" + s.substring(0, 2), secB = "B" + s.substring(0, 2);
        crear("/api/secciones", Map.of("nombre", secA, "grado", 1, "anioAcademico", ANIO, "capacidadMaxima", 30));
        crear("/api/secciones", Map.of("nombre", secB, "grado", 1, "anioAcademico", ANIO, "capacidadMaxima", 30));
        List<String> docentes = docentes(2);
        String docA = docentes.get(0), docB = docentes.get(1);
        String aula = "AG" + s, aula2 = "AH" + s, aulaChica = "AC" + s;
        crear("/api/aulas", Map.of("codigo", aula, "nombre", "Aula " + s, "tipoAula", "AULA_REGULAR", "capacidad", 40));
        crear("/api/aulas", Map.of("codigo", aula2, "nombre", "Aula 2 " + s, "tipoAula", "AULA_REGULAR", "capacidad", 40));
        crear("/api/aulas", Map.of("codigo", aulaChica, "nombre", "Chica " + s, "tipoAula", "TALLER", "capacidad", 5));

        // ---------- Plan de estudios (230) ----------
        byte[] plan = libro(COLUMNAS_PLAN,
                List.of(A, "1", mat, "Matemática", "6", competencia, "", ""),
                List.of(A, "1", com, "Comunicación", "5", "", "", ""),
                List.of(A, "1", mat.toLowerCase(), "Matemática", "6", "", "", ""),
                List.of(A, "1", cie, "Ciencia", "99", "", "", ""),
                List.of(A, "4", "ART" + s, "Arte", "2", "", "", ""),
                List.of(A, "1", his, "Historia", "3", "NO-EXISTE", "", ""));

        JsonNode revision = importar(PLAN, plan, false);
        assertThat(revision.path("validas").asInt()).isEqualTo(2);
        assertThat(cursos.findByCodigoIgnoreCase(mat)).isEmpty();
        assertThat(planDelGrado().path("cursos")).noneMatch(c -> c.path("codigo").asText().equals(mat));

        JsonNode cargaPlan = importar(PLAN, plan, true);
        assertThat(cargaPlan.path("creadas").asInt()).isEqualTo(2);
        assertThat(campo(cargaPlan.path("omitidas"), 4)).isEqualTo("codigoCurso");
        assertThat(campo(cargaPlan.path("errores"), 5)).isEqualTo("horasSemanales");
        assertThat(campo(cargaPlan.path("errores"), 6)).isEqualTo("fila");
        assertThat(campo(cargaPlan.path("errores"), 7)).isEqualTo("codigosCompetencias");
        JsonNode cursoMat = cursoDelPlan(mat);
        assertThat(cursoMat.path("horasSemanales").asInt()).isEqualTo(6);
        assertThat(cursoMat.path("competencias").findValuesAsText("codigo")).containsExactly(competencia);
        assertThat(cursoDelPlan(com).path("horasSemanales").asInt()).isEqualTo(5);

        JsonNode planRepetido = importar(PLAN, plan, true);
        assertThat(planRepetido.path("creadas").asInt()).isZero();
        assertThat(planRepetido.path("omitidas")).hasSize(3);

        // El plan ya está definido: agregar un curso desde otro archivo exige motivo, como en la pantalla.
        JsonNode sinMotivo = importar(PLAN, libro(COLUMNAS_PLAN,
                List.of(A, "1", his, "Historia", "3", "", "", "")), true);
        assertThat(campo(sinMotivo.path("errores"), 2)).isEqualTo("motivo");
        assertThat(cursos.findByCodigoIgnoreCase(his)).as("la fila completa se deshace").isEmpty();
        JsonNode conMotivo = importar(PLAN, libro(COLUMNAS_PLAN,
                List.of(A, "1", his, "Historia", "3", "", "", "Se agrega Historia")), true);
        assertThat(conMotivo.path("creadas").asInt()).isEqualTo(1);

        // ---------- Asignaciones docentes (231) ----------
        byte[] asignacion = libro(COLUMNAS_ASIGNACION,
                List.of(A, "1", secA, mat, docA),
                List.of(A, "1", secA, com, docB),
                List.of(A, "1", secB, mat, docA),
                List.of(A, "1", "ZZ", mat, docA),
                List.of(A, "1", secA, "NO-EXISTE", docA),
                List.of(A, "1", secB, com, "NO-EXISTE"));

        JsonNode revisionAsig = importar(ASIGNACIONES, asignacion, false);
        assertThat(revisionAsig.path("validas").asInt()).isEqualTo(3);
        long antes = asignaciones.count();
        assertThat(asignaciones.count()).isEqualTo(antes);

        JsonNode cargaAsig = importar(ASIGNACIONES, asignacion, true);
        assertThat(cargaAsig.path("creadas").asInt()).isEqualTo(3);
        assertThat(campo(cargaAsig.path("errores"), 5)).isEqualTo("seccion");
        assertThat(campo(cargaAsig.path("errores"), 6)).isEqualTo("codigoCurso");
        assertThat(campo(cargaAsig.path("errores"), 7)).isEqualTo("codigoDocente");
        assertThat(asignaciones.count()).isEqualTo(antes + 3);

        JsonNode asigRepetida = importar(ASIGNACIONES, asignacion, true);
        assertThat(asigRepetida.path("creadas").asInt()).isZero();
        assertThat(asigRepetida.path("omitidas")).hasSize(3);

        // ---------- Horarios (231) ----------
        byte[] horario = libro(COLUMNAS_HORARIO,
                List.of(A, "1", secA, mat, aula, "LUNES", "08:00", "09:30"),
                // Misma aula y misma sección a la misma hora: cruce.
                List.of(A, "1", secA, com, aula, "LUNES", "08:00", "09:00"),
                // El docente A ya dicta en la sección A a esa hora: cruce de docente.
                List.of(A, "1", secB, mat, aula2, "LUNES", "08:30", "09:00"),
                // Aula sin capacidad para la sección.
                List.of(A, "1", secA, com, aulaChica, "MARTES", "08:00", "09:00"),
                // Fuera de los pasos de 30 minutos.
                List.of(A, "1", secA, com, aula, "MARTES", "08:15", "09:00"),
                List.of(A, "1", secA, com, aula, "MIERCOLES", "10:00", "11:00"),
                // Curso sin docente asignado en la sección B.
                List.of(A, "1", secB, com, aula, "JUEVES", "10:00", "11:00"),
                List.of(A, "1", secA, mat, aula, "VIERNES", "8.00", "09:00"));

        JsonNode revisionHorario = importar(HORARIOS, horario, false);
        assertThat(revisionHorario.path("validas").asInt()).isEqualTo(2);
        assertThat(revisionHorario.path("errores")).hasSize(6);

        JsonNode cargaHorario = importar(HORARIOS, horario, true);
        assertThat(cargaHorario.path("creadas").asInt()).isEqualTo(2);
        assertThat(mensaje(cargaHorario.path("errores"), 3)).contains("aula");
        assertThat(mensaje(cargaHorario.path("errores"), 4)).contains("docente");
        assertThat(mensaje(cargaHorario.path("errores"), 5)).contains("capacidad");
        assertThat(campo(cargaHorario.path("errores"), 6)).isEqualTo("horaInicio");
        assertThat(campo(cargaHorario.path("errores"), 8)).isEqualTo("codigoCurso");
        assertThat(campo(cargaHorario.path("errores"), 9)).isEqualTo("horaInicio");

        JsonNode horarioRepetido = importar(HORARIOS, horario, true);
        assertThat(horarioRepetido.path("creadas").asInt()).isZero();
        assertThat(horarioRepetido.path("omitidas")).hasSize(2);
    }

    @Test
    void lasPlantillasTraenSusColumnasYCadaCargaExigeElPermisoDelRegistroManual() throws Exception {
        assertThat(columnas(descargar(PLAN))).containsExactlyElementsOf(COLUMNAS_PLAN);
        assertThat(columnas(descargar(ASIGNACIONES))).containsExactlyElementsOf(COLUMNAS_ASIGNACION);
        assertThat(columnas(descargar(HORARIOS))).containsExactlyElementsOf(COLUMNAS_HORARIO);

        byte[] archivo = libro(COLUMNAS_ASIGNACION, List.of(A, "1", "A", "X", "Y"));
        var lector = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_DOCENTE"), new SimpleGrantedAuthority("ACADEMICO_LEER"));
        for (String ruta : List.of(PLAN, ASIGNACIONES, HORARIOS)) {
            mvc.perform(multipart(ruta).file(archivo("x.xlsx", archivo)).with(lector))
                    .andExpect(status().isForbidden());
        }
        // El plan, igual que en la pantalla, es solo del administrador.
        var secretaria = jwt().authorities(new SimpleGrantedAuthority("ROLE_SECRETARIA"),
                new SimpleGrantedAuthority("ACADEMICO_LEER"), new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR"));
        mvc.perform(multipart(PLAN).file(archivo("x.xlsx", archivo)).with(secretaria))
                .andExpect(status().isForbidden());
    }

    // ---------- Apoyo ----------

    private JsonNode planDelGrado() throws Exception {
        return json.readTree(mvc.perform(get("/api/planes-estudios/" + ANIO + "/1").with(administrador()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode cursoDelPlan(String codigo) throws Exception {
        for (JsonNode curso : planDelGrado().path("cursos")) {
            if (curso.path("codigo").asText().equals(codigo)) {
                return curso;
            }
        }
        throw new AssertionError("El curso " + codigo + " no está en el plan");
    }

    /** Registra docentes con la carga masiva existente y devuelve sus códigos. */
    private List<String> docentes(int cantidad) throws Exception {
        List<List<String>> filas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            String dni = String.valueOf(ThreadLocalRandom.current().nextInt(40_000_000, 49_999_999));
            filas.add(List.of("DNI", dni, "Docente", "Prueba", "Carga" + i, "FEMENINO", "1985-03-14", "",
                    "2020-03-01", "MATEMATICA", "", "", ""));
        }
        @SuppressWarnings("unchecked")
        JsonNode resultado = importar("/api/docentes/importaciones",
                libro(COLUMNAS_DOCENTE, filas.toArray(List[]::new)), true);
        List<String> codigos = resultado.path("credentials").findValuesAsText("code");
        assertThat(codigos).hasSize(cantidad);
        return codigos;
    }

    private JsonNode importar(String ruta, byte[] contenido, boolean confirmar) throws Exception {
        return json.readTree(mvc.perform(multipart(ruta)
                        .file(archivo("carga.xlsx", contenido))
                        .param("confirmar", String.valueOf(confirmar))
                        .with(administrador()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private byte[] descargar(String ruta) throws Exception {
        return mvc.perform(get(ruta + "/plantilla").with(administrador()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private void crear(String ruta, Map<String, Object> cuerpo) throws Exception {
        mvc.perform(post(ruta).with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated());
    }

    private RequestPostProcessor administrador() {
        List<GrantedAuthority> permisos = new ArrayList<>();
        permisos.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
        em.createQuery("from Permiso", Permiso.class).getResultList()
                .forEach(permiso -> permisos.add(new SimpleGrantedAuthority(permiso.getCodigo())));
        return jwt().jwt(token -> token.subject("importador")).authorities(permisos);
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
        return issue(lista, fila).path("message").asText().toLowerCase();
    }

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre,
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

    private static List<String> columnas(byte[] plantilla) throws Exception {
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(plantilla))) {
            List<String> valores = new ArrayList<>();
            libro.getSheetAt(0).getRow(0).forEach(celda -> valores.add(celda.getStringCellValue()));
            return valores;
        }
    }

    private static String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
