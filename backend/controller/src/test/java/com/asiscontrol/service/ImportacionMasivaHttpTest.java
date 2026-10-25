package com.asiscontrol.service;

import com.asiscontrol.exception.GlobalExceptionHandler;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Carga masiva de alumnos por HTTP, incluidos los envios mal armados. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMINISTRADOR")
class ImportacionMasivaHttpTest {
    private static final String RUTA = "/api/alumnos/importaciones";
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {"codigoAlumno", "nombres", "apellidoPaterno",
            "apellidoMaterno", "tipoDocumento", "numeroDocumento", "sexo", "fechaNacimiento", "correo"};

    @Autowired WebApplicationContext context;
    @Autowired GlobalExceptionHandler exceptionHandler;
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private byte[] excel(int filas) throws Exception {
        return excel(filas, false);
    }

    private byte[] excel(int filas, boolean nombreInvalido) throws Exception {
        String lote = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (Workbook w = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = w.createSheet("Alumnos");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);
            for (int f = 1; f <= filas; f++) {
                String[] valores = {"I" + lote + f, nombreInvalido ? "Nombre123" : "Nombre " + (char) ('A' + f - 1), "Paterno", "Materno", "PASAPORTE",
                        lote + f, "FEMENINO", "2012-05-15", lote + f + "@example.test"};
                Row r = sheet.createRow(f);
                for (int c = 0; c < valores.length; c++) r.createCell(c).setCellValue(valores[c]);
            }
            w.write(out);
            return out.toByteArray();
        }
    }

    @Test void importaUnExcelEnviadoEnElCampoArchivo() throws Exception {
        mvc.perform(multipart(RUTA).file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX, excel(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(3))
                .andExpect(jsonPath("$.rejectedRows").value(0));
    }

    @Test void rechazaUnNombreConNumerosSinImportarLaFila() throws Exception {
        mvc.perform(multipart(RUTA).file(new MockMultipartFile("archivo", "alumnos.xlsx", XLSX, excel(1, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.rejectedRows").value(1));
    }

    @Test void unCampoConOtroNombreExplicaQueFaltaElArchivo() throws Exception {
        mvc.perform(multipart(RUTA).file(new MockMultipartFile("file", "alumnos.xlsx", XLSX, excel(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARCHIVO_REQUERIDO"))
                .andExpect(jsonPath("$.message").value("Falta el archivo: envíelo en el campo 'archivo'"));
    }

    @Test void unEnvioQueNoEsMultipartSeRechazaComoFormatoNoSoportado() throws Exception {
        mvc.perform(post(RUTA).contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FORMATO_DE_ENVIO_NO_SOPORTADO"));
    }

    @Test void unArchivoSobreElLimiteDelServidorResponde413() {
        // MockMvc no aplica el limite multipart del servidor, asi que se prueba el manejador.
        var respuesta = exceptionHandler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(10L * 1024 * 1024),
                new MockHttpServletRequest("POST", RUTA));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(respuesta.getBody().code()).isEqualTo("ARCHIVO_DEMASIADO_GRANDE");
    }
}
