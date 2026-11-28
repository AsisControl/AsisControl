package com.asiscontrol.controller;

import com.asiscontrol.entity.Permiso;
import com.asiscontrol.repository.AulaRepository;
import com.asiscontrol.repository.SeccionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OBS-05 · ASISCTRL-229/235: carga masiva de aulas y secciones con archivo válido, con errores y
 * repetido. Sin transacción en la prueba: la revisión debe deshacerse de verdad y cada fila
 * confirmada se guarda por separado.
 */
@SpringBootTest
@ActiveProfiles("test")
class CargaMasivaAulasSeccionesHttpTest {

    private static final String AULAS = "/api/aulas/importaciones";
    private static final String SECCIONES = "/api/secciones/importaciones";
    private static final List<String> COLUMNAS_AULA =
            List.of("codigo", "nombre", "tipoAula", "capacidad", "ubicacion");
    private static final List<String> COLUMNAS_SECCION = List.of(
            "anioAcademico", "grado", "nombre", "capacidadMaxima", "codigoDocenteTutor", "motivo");
    /** Año que ninguna otra prueba usa: aquí las secciones sí quedan guardadas. */
    private static final int ANIO = 2067;

    @Autowired WebApplicationContext context;
    @Autowired AulaRepository aulas;
    @Autowired SeccionRepository secciones;
    @Autowired AnioAcademicoRepository anios;
    @Autowired EntityManager em;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void preparar() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        if (anios.findByAnio(ANIO).isEmpty()) {
            crear("/api/configuracion-academica/anios",
                    Map.of("anio", ANIO, "fechaInicio", ANIO + "-03-01", "fechaFin", ANIO + "-12-20"));
            crear("/api/configuracion-academica/anios/" + ANIO + "/grados",
                    Map.of("numero", 1, "capacidadDefault", 30));
        }
    }

    // ---------- Aulas ----------

    @Test
    void laPlantillaDeAulasTraeSusColumnasYSuEjemploEsValido() throws Exception {
        byte[] plantilla = descargar(AULAS);
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(plantilla))) {
            Sheet hoja = libro.getSheet("Aulas");
            assertThat(celdas(hoja.getRow(0))).containsExactlyElementsOf(COLUMNAS_AULA);
            assertThat(hoja.getDataValidations()).hasSize(1);
        }
        JsonNode revision = importar(AULAS, plantilla, false);
        assertThat(revision.path("errores")).isEmpty();
        assertThat(revision.path("total").asInt()).isEqualTo(1);
    }

    @Test
    void revisarAulasNoGuardaNadaYConfirmarLasCrea() throws Exception {
        String codigo = "T-" + clave();
        byte[] archivo = libro(COLUMNAS_AULA, List.of(codigo, "Aula prueba", "TALLER", "25", ""));

        JsonNode revision = importar(AULAS, archivo, false);
        assertThat(revision.path("validas").asInt()).isEqualTo(1);
        assertThat(revision.path("creadas").asInt()).isZero();
        assertThat(aulas.findByCodigoIgnoreCase(codigo)).isEmpty();

        JsonNode importacion = importar(AULAS, archivo, true);
        assertThat(importacion.path("creadas").asInt()).isEqualTo(1);
        var aula = aulas.findByCodigoIgnoreCase(codigo).orElseThrow();
        assertThat(aula.getCapacidad()).isEqualTo(25);
        assertThat(aula.isActivo()).isTrue();
    }

    @Test
    void lasAulasConErroresIndicanFilaCampoYMotivoSinGuardarse() throws Exception {
        String valida = "V-" + clave();
        String capacidadMala = "C-" + clave();
        String tipoMalo = "X-" + clave();
        byte[] archivo = libro(COLUMNAS_AULA,
                List.of(valida, "Aula valida", "AULA_REGULAR", "30", ""),
                List.of(capacidadMala, "Sin capacidad", "AULA_REGULAR", "treinta", ""),
                List.of(tipoMalo, "Tipo invalido", "PISCINA", "30", ""),
                List.of("", "Sin codigo", "AULA_REGULAR", "30", ""));

        JsonNode resultado = importar(AULAS, archivo, true);

        assertThat(resultado.path("total").asInt()).isEqualTo(4);
        assertThat(resultado.path("creadas").asInt()).isEqualTo(1);
        JsonNode errores = resultado.path("errores");
        assertThat(errores).hasSize(3);
        assertThat(error(errores, 3)).containsEntry("field", "capacidad");
        assertThat(error(errores, 4)).containsEntry("field", "tipoAula");
        assertThat(error(errores, 5)).containsEntry("field", "codigo");
        assertThat(String.valueOf(error(errores, 3).get("message"))).contains("entero");
        assertThat(aulas.findByCodigoIgnoreCase(valida)).isPresent();
        assertThat(aulas.findByCodigoIgnoreCase(capacidadMala)).isEmpty();
        assertThat(aulas.findByCodigoIgnoreCase(tipoMalo)).isEmpty();
    }

    @Test
    void volverACargarElMismoArchivoDeAulasNoDuplica() throws Exception {
        String codigo = "R-" + clave();
        byte[] archivo = libro(COLUMNAS_AULA,
                List.of(codigo, "Aula repetida", "BIBLIOTECA", "40", ""),
                // Misma clave con otro formato: se reconoce como repetida dentro del archivo.
                List.of(" " + codigo.toLowerCase() + " ", "Otra", "BIBLIOTECA", "40", ""));

        JsonNode primera = importar(AULAS, archivo, true);
        assertThat(primera.path("creadas").asInt()).isEqualTo(1);
        assertThat(primera.path("omitidas")).hasSize(1);
        assertThat(primera.path("omitidas").get(0).path("message").asText()).contains("fila 2");

        long antes = aulas.count();
        JsonNode segunda = importar(AULAS, archivo, true);
        assertThat(segunda.path("creadas").asInt()).isZero();
        assertThat(segunda.path("omitidas")).hasSize(2);
        assertThat(segunda.path("errores")).isEmpty();
        assertThat(aulas.count()).isEqualTo(antes);
    }

    // ---------- Secciones ----------

    @Test
    void laPlantillaDeSeccionesTraeSusColumnas() throws Exception {
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(descargar(SECCIONES)))) {
            assertThat(celdas(libro.getSheet("Secciones").getRow(0)))
                    .containsExactlyElementsOf(COLUMNAS_SECCION);
        }
    }

    @Test
    void lasSeccionesAplicanLasReglasDelAltaManualYNoSeDuplican() throws Exception {
        String nombre = "S" + clave().substring(0, 6).toUpperCase();
        byte[] archivo = libro(COLUMNAS_SECCION,
                List.of(String.valueOf(ANIO), "1", nombre, "30", "", ""),
                // Grado no ofertado en el año: la misma regla que en el registro manual.
                List.of(String.valueOf(ANIO), "4", nombre, "30", "", ""),
                // Capacidad fuera de rango.
                List.of(String.valueOf(ANIO), "1", nombre + "X", "500", "", ""),
                // Tutor inexistente.
                List.of(String.valueOf(ANIO), "1", nombre + "Y", "30", "NO-EXISTE", ""));

        JsonNode revision = importar(SECCIONES, archivo, false);
        assertThat(revision.path("validas").asInt()).isEqualTo(1);
        assertThat(revision.path("errores")).hasSize(3);
        assertThat(secciones.findByAnioAcademicoAndGradoAndNombreIgnoreCase(ANIO, 1, nombre)).isEmpty();

        JsonNode importacion = importar(SECCIONES, archivo, true);
        assertThat(importacion.path("creadas").asInt()).isEqualTo(1);
        JsonNode errores = importacion.path("errores");
        assertThat(error(errores, 3)).containsEntry("field", "fila");
        assertThat(error(errores, 4)).containsEntry("field", "capacidadMaxima");
        assertThat(error(errores, 5)).containsEntry("field", "codigoDocenteTutor");
        assertThat(secciones.findByAnioAcademicoAndGradoAndNombreIgnoreCase(ANIO, 1, nombre)).isPresent();
        assertThat(secciones.findByAnioAcademicoAndGradoAndNombreIgnoreCase(ANIO, 4, nombre)).isEmpty();

        JsonNode repetida = importar(SECCIONES, archivo, true);
        assertThat(repetida.path("creadas").asInt()).isZero();
        assertThat(repetida.path("omitidas")).hasSize(1);
        assertThat(repetida.path("omitidas").get(0).path("row").asInt()).isEqualTo(2);
    }

    /** ASISCTRL-240/241: la carga masiva solo da de alta; no toca lo registrado a mano. */
    @Test
    void volverACargarNoBorraElEquipamientoNiElAulaDeLaSeccion() throws Exception {
        String codigo = "E-" + clave();
        String nombre = "Q" + clave().substring(0, 6).toUpperCase();
        crear("/api/aulas", Map.of("codigo", codigo, "nombre", "Aula equipada", "tipoAula", "TALLER",
                "capacidad", 30, "equipamiento", "Proyector"));
        Long aulaId = aulas.findByCodigoIgnoreCase(codigo).orElseThrow().getId();
        crear("/api/secciones", Map.of("nombre", nombre, "grado", 1, "anioAcademico", ANIO,
                "capacidadMaxima", 30, "aulaId", aulaId));

        JsonNode aulasCargadas = importar(AULAS,
                libro(COLUMNAS_AULA, List.of(codigo, "Otro nombre", "TALLER", "10", "")), true);
        JsonNode seccionesCargadas = importar(SECCIONES, libro(COLUMNAS_SECCION,
                List.of(String.valueOf(ANIO), "1", nombre, "20", "", "")), true);

        assertThat(aulasCargadas.path("omitidas")).hasSize(1);
        assertThat(seccionesCargadas.path("omitidas")).hasSize(1);
        var aula = aulas.findByCodigoIgnoreCase(codigo).orElseThrow();
        assertThat(aula.getEquipamiento()).isEqualTo("Proyector");
        assertThat(aula.getCapacidad()).isEqualTo(30);
        var seccion = secciones.findByAnioAcademicoAndGradoAndNombreIgnoreCase(ANIO, 1, nombre).orElseThrow();
        assertThat(seccion.getAula().getId()).isEqualTo(aulaId);
        assertThat(seccion.getCapacidadMaxima()).isEqualTo(30);
    }

    @Test
    void soloQuienPuedeRegistrarManualmentePuedeCargarElArchivo() throws Exception {
        byte[] archivo = libro(COLUMNAS_AULA, List.of("P-" + clave(), "Aula", "TALLER", "20", ""));
        var docente = jwt().authorities(
                new SimpleGrantedAuthority("ROLE_DOCENTE"), new SimpleGrantedAuthority("ACADEMICO_LEER"));
        mvc.perform(multipart(AULAS).file(archivo("a.xlsx", archivo)).with(docente))
                .andExpect(status().isForbidden());
        mvc.perform(get(AULAS + "/plantilla").with(docente)).andExpect(status().isForbidden());
        // Secciones exige además el rol ADMINISTRADOR, igual que el alta manual.
        var secretaria = jwt().authorities(new SimpleGrantedAuthority("ROLE_SECRETARIA"),
                new SimpleGrantedAuthority("ACADEMICO_LEER"), new SimpleGrantedAuthority("ACADEMICO_ESCRIBIR"));
        mvc.perform(multipart(SECCIONES).file(archivo("s.xlsx", archivo)).with(secretaria))
                .andExpect(status().isForbidden());
    }

    // ---------- Apoyo ----------

    private JsonNode importar(String ruta, byte[] contenido, boolean confirmar) throws Exception {
        String cuerpo = mvc.perform(multipart(ruta)
                        .file(archivo("carga.xlsx", contenido))
                        .param("confirmar", String.valueOf(confirmar))
                        .with(administrador()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(JsonNode errores, int fila) {
        for (JsonNode error : errores) {
            if (error.path("row").asInt() == fila) {
                return json.convertValue(error, Map.class);
            }
        }
        throw new AssertionError("Sin error para la fila " + fila + ": " + errores);
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

    private static List<String> celdas(Row fila) {
        List<String> valores = new ArrayList<>();
        fila.forEach(celda -> valores.add(celda.getStringCellValue()));
        return valores;
    }

    private static String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
