package com.asiscontrol.controller;

import com.asiscontrol.entity.Docente;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.RegistroAuditoriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-51: sin transaccion en la prueba, porque el analisis debe deshacerse de verdad y cada fila
 * confirmada se guarda por separado.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportacionDocenteHttpTest {

    private static final String IMPORTACIONES = "/api/docentes/importaciones";
    private static final List<String> COLUMNAS = List.of(
            "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento", "nacionalidad", "fechaIngreso",
            "especialidad", "telefono", "correo", "direccion");

    @Autowired WebApplicationContext context;
    @Autowired DocenteRepository docentes;
    @Autowired com.asiscontrol.repository.PersonaRepository personas;
    @Autowired RegistroAuditoriaRepository auditorias;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void laPlantillaTraeLasColumnasDeLaBaseConSusListasYUnEjemploValido() throws Exception {
        byte[] plantilla = mvc.perform(get(IMPORTACIONES + "/plantilla").with(comoRol("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(plantilla))) {
            Sheet hoja = libro.getSheet("Docentes");
            assertThat(celdas(hoja.getRow(0))).containsExactlyElementsOf(COLUMNAS);
            assertThat(hoja.getDataValidations()).hasSize(3);
            assertThat(hoja.getDataValidations().toString()).isNotBlank();
        }
        JsonNode analisis = importar(plantilla, false, status().isOk());
        assertThat(analisis.path("issues")).isEmpty();
        assertThat(analisis.path("importedRows").asInt()).isEqualTo(1);
    }

    @Test
    void analizarNoGuardaNadaNiDejaAuditoriaDelLote() throws Exception {
        List<String> fila = filaValida();
        String archivo = "analisis-" + UUID.randomUUID() + ".xlsx";

        JsonNode resultado = importar(libro(fila), archivo, false, status().isOk());

        assertThat(resultado.path("totalRows").asInt()).isEqualTo(1);
        assertThat(resultado.path("importedRows").asInt()).isEqualTo(1);
        assertThat(docente(fila)).isEmpty();
        assertThat(auditoriasDe(archivo)).isEmpty();
        // El análisis anticipa usuario y código, pero nunca entrega una contraseña.
        JsonNode credencial = resultado.path("credentials").get(0);
        assertThat(credencial.path("username").asText()).matches("[0-9]{8}");
        assertThat(credencial.path("temporaryPassword").isNull()).isTrue();
    }

    @Test
    void confirmarCreaLasFilasValidasConTodosSusDatosYAuditaElLote() throws Exception {
        List<String> completa = filaValida();
        List<String> sinOpcionales = filaValida();
        sinOpcionales.set(7, "");
        sinOpcionales.set(10, "");
        sinOpcionales.set(11, "");
        sinOpcionales.set(12, "");
        String archivo = "lote-" + UUID.randomUUID() + ".xlsx";

        JsonNode resultado = importar(libro(completa, sinOpcionales), archivo, true, status().isOk());

        assertThat(resultado.path("importedRows").asInt()).isEqualTo(2);
        assertThat(resultado.path("rejectedRows").asInt()).isZero();
        Docente creado = docente(completa).orElseThrow();
        int anio = LocalDate.now(java.time.ZoneId.of("America/Lima")).getYear();
        assertThat(creado.getCodigoDocente()).matches("DOC" + anio + "[0-9]{4}");
        assertThat(docente(sinOpcionales).orElseThrow().getCodigoDocente())
                .isNotEqualTo(creado.getCodigoDocente());
        assertThat(creado.getFechaIngreso()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(creado.getEspecialidad()).isEqualTo(Especialidad.MATEMATICA);
        assertThat(creado.isActivo()).isTrue();
        RegistroAuditoria lote = auditoriasDe(archivo).get(0);
        assertThat(lote.getAccion()).isEqualTo("IMPORTAR_DOCENTES");
        assertThat(lote.getNuevoValor()).contains(
                "filas=2", "importados=2", "fallidos=0", "accesosCreados=2", "accesosExistentes=0");
        JsonNode credencial = resultado.path("credentials").get(0);
        String contrasena = credencial.path("temporaryPassword").asText();
        assertThat(contrasena).hasSize(12);
        assertThat(credencial.path("code").asText()).isEqualTo(creado.getCodigoDocente());
        assertThat(credencial.path("username").asText()).matches("[0-9]{8}");
        assertThat(credencial.path("email").asText())
                .isEqualTo(credencial.path("username").asText() + "@asiscontrol.edu.pe");
        // La contraseña no queda en la auditoría.
        assertThat(auditorias.findAll()).noneMatch(registro ->
                String.valueOf(registro.getNuevoValor()).contains(contrasena)
                        || String.valueOf(registro.getValorAnterior()).contains(contrasena));
    }

    @Test
    void rechazaCadaFilaInvalidaConSuMotivoYCreaLasDemas() throws Exception {
        List<String> existente = filaValida();
        importar(libro(existente), true, status().isOk());

        List<String> valida = filaValida();
        List<String> yaRegistrado = new ArrayList<>(existente);
        List<String> documentoRepetidoEnElArchivo = new ArrayList<>(valida);
        documentoRepetidoEnElArchivo.set(11, "otro" + clave() + "@example.test");
        List<String> sinCorreo = filaValida();
        sinCorreo.set(11, "");
        List<String> especialidadInvalida = filaValida();
        especialidadInvalida.set(9, "COCINA");
        List<String> ingresoFuturo = filaValida();
        ingresoFuturo.set(8, LocalDate.now().plusDays(1).toString());
        List<String> fechaIlegible = filaValida();
        fechaIlegible.set(6, "ayer");
        List<String> documentoInvalido = filaValida();
        documentoInvalido.set(1, "12");

        JsonNode resultado = importar(libro(valida, yaRegistrado, documentoRepetidoEnElArchivo,
                sinCorreo, especialidadInvalida, ingresoFuturo, fechaIlegible, documentoInvalido),
                true, status().isOk());

        assertThat(resultado.path("totalRows").asInt()).isEqualTo(8);
        // El correo es opcional: la fila sin correo se registra.
        assertThat(resultado.path("importedRows").asInt()).isEqualTo(2);
        assertThat(resultado.path("rejectedRows").asInt()).isEqualTo(6);
        assertThat(resultado.path("issues").findValuesAsText("row"))
                .containsExactly("3", "4", "6", "7", "8", "9");
        List<String> motivos = resultado.path("issues").findValuesAsText("message");
        assertThat(motivos.get(0)).containsIgnoringCase("docente");
        assertThat(motivos.get(1)).containsIgnoringCase("docente");
        assertThat(motivos.get(2)).isEqualTo("El valor de especialidad no esta permitido");
        assertThat(motivos.get(3)).contains("futuro");
        assertThat(motivos.get(4)).contains("fechaNacimiento");
        assertThat(motivos.get(5)).containsIgnoringCase("documento");
        assertThat(resultado.path("credentials").findValuesAsText("row")).containsExactly("2", "5");
        assertThat(docente(valida)).isPresent();
        assertThat(docentes.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void elAnalisisDetectaRepetidosDentroDelMismoArchivoYNoGuarda() throws Exception {
        List<String> primera = filaValida();
        List<String> mismoDocumento = new ArrayList<>(primera);

        JsonNode resultado = importar(libro(primera, mismoDocumento), false, status().isOk());

        assertThat(resultado.path("importedRows").asInt()).isEqualTo(1);
        assertThat(resultado.path("issues").findValuesAsText("message"))
                .containsExactly("La persona con este documento ya esta registrada como docente");
        assertThat(docente(primera)).isEmpty();
    }

    @Test
    void rechazaArchivosSinLasColumnasObligatoriasONoExcel() throws Exception {
        byte[] sinEspecialidad = libroCon(
                COLUMNAS.stream().filter(columna -> !columna.equals("especialidad")).toList(), List.of());
        importar(sinEspecialidad, false, status().is4xxClientError());
        importar("no es excel".getBytes(), false, status().is4xxClientError());
    }

    @Test
    void soloAdministracionImporta() throws Exception {
        byte[] archivo = libro(filaValida());
        mvc.perform(multipart(IMPORTACIONES).file(archivo("d.xlsx", archivo)))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(IMPORTACIONES).file(archivo("d.xlsx", archivo)).with(comoRol("DOCENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(IMPORTACIONES).file(archivo("d.xlsx", archivo)).with(comoRol("DIRECTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get(IMPORTACIONES + "/plantilla").with(comoRol("DOCENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(IMPORTACIONES).file(archivo("d.xlsx", archivo)).with(comoRol("SECRETARIA")))
                .andExpect(status().isForbidden());
    }

    private List<RegistroAuditoria> auditoriasDe(String archivo) {
        return auditorias.findAll().stream()
                .filter(registro -> registro.getNuevoValor() != null
                        && registro.getNuevoValor().contains("archivo=" + archivo))
                .toList();
    }

    private JsonNode importar(byte[] contenido, boolean confirmar, ResultMatcher esperado)
            throws Exception {
        return importar(contenido, "docentes.xlsx", confirmar, esperado);
    }

    private JsonNode importar(
            byte[] contenido, String nombre, boolean confirmar, ResultMatcher esperado) throws Exception {
        byte[] respuesta = mvc.perform(multipart(IMPORTACIONES)
                        .file(archivo(nombre, contenido))
                        .param("confirmar", String.valueOf(confirmar))
                        .with(comoRol("ADMINISTRADOR")))
                .andExpect(esperado)
                // La respuesta puede traer contraseñas temporales: no se guarda en caché.
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        return json.readTree(respuesta);
    }

    private MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido);
    }

    private RequestPostProcessor comoRol(String rol) {
        return jwt().jwt(token -> token.subject("importador"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    /** Docente importado con el documento de la fila (el código lo genera el sistema). */
    private java.util.Optional<Docente> docente(List<String> fila) {
        return personas.findByTipoDocumentoAndNumeroDocumento(
                        com.asiscontrol.entity.enums.TipoDocumento.valueOf(fila.get(0)), fila.get(1))
                .flatMap(persona -> docentes.findByPersonaIdAndActivoTrue(persona.getId()));
    }

    private List<String> filaValida() {
        String clave = clave();
        return new ArrayList<>(List.of(
                "PASAPORTE", clave.toUpperCase(), "Patricia", "Morales", "Rios", "FEMENINO",
                "1985-03-14", "Peruana", "2020-03-01", "MATEMATICA", "999888777", clave + "@example.test", "Lima"));
    }

    private String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @SafeVarargs
    private byte[] libro(List<String>... filas) throws Exception {
        return libroCon(COLUMNAS, List.of(filas));
    }

    private byte[] libroCon(List<String> columnas, List<List<String>> filas) throws Exception {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Docentes");
            escribir(hoja.createRow(0), columnas);
            for (int indice = 0; indice < filas.size(); indice++) {
                escribir(hoja.createRow(indice + 1), filas.get(indice));
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private void escribir(Row fila, List<String> celdas) {
        for (int indice = 0; indice < celdas.size(); indice++) {
            fila.createCell(indice).setCellValue(celdas.get(indice));
        }
    }

    private List<String> celdas(Row fila) {
        List<String> resultado = new ArrayList<>();
        fila.forEach(celda -> resultado.add(celda.getStringCellValue()));
        return resultado;
    }
}
