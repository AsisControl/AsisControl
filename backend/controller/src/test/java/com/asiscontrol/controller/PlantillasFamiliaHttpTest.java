package com.asiscontrol.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/** Plantillas e importacion de apoderados y parentescos por HTTP. */
@SpringBootTest
@ActiveProfiles("test")
class PlantillasFamiliaHttpTest {

    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void laPlantillaDeApoderadosTraeSusColumnasYListas() throws Exception {
        try (Workbook workbook = download("/api/apoderados/importaciones/plantilla",
                "plantilla_apoderados.xlsx")) {
            Sheet sheet = workbook.getSheet("Apoderados");
            assertThat(cells(sheet.getRow(0))).containsExactly(
                    "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
                    "apellidoMaterno", "sexo", "fechaNacimiento", "telefono", "correo",
                    "direccion");
            assertThat(cells(sheet.getRow(1))).startsWith("DNI", "40000001");
            assertThat(sheet.getColumnStyle(1).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getDataValidations()).hasSize(2);
        }
    }

    @Test
    void laPlantillaDeParentescosUneAlumnoYApoderadoDeLosEjemplos() throws Exception {
        try (Workbook workbook = download("/api/apoderados/parentescos/importaciones/plantilla",
                "plantilla_parentescos.xlsx")) {
            Sheet sheet = workbook.getSheet("Parentescos");
            assertThat(cells(sheet.getRow(0))).containsExactly(
                    "codigoAlumno", "tipoDocumentoApoderado", "numeroDocumentoApoderado",
                    "parentesco", "principal");
            assertThat(cells(sheet.getRow(1)))
                    .containsExactly("EST-0001", "DNI", "40000001", "MADRE", "SI");
            assertThat(sheet.getDataValidations())
                    .extracting(validation -> List.of(
                            validation.getValidationConstraint().getExplicitListValues()))
                    .contains(
                            List.of("PADRE", "MADRE", "TUTOR_LEGAL", "OTRO_FAMILIAR"),
                            List.of("SI", "NO"));
        }
    }

    @Test
    void unDocenteNoDescargaLasPlantillas() throws Exception {
        mvc.perform(get("/api/apoderados/importaciones/plantilla")
                        .with(user("docente-prueba").roles("DOCENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/apoderados/parentescos/importaciones/plantilla")
                        .with(user("docente-prueba").roles("DOCENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void laImportacionRecibeElArchivoMultipartYRespondeElResumen() throws Exception {
        var archivo = new MockMultipartFile("archivo", "parentescos.xlsx", null,
                new com.asiscontrol.service.PlantillaFamiliaService().generateRelationshipTemplate());
        mvc.perform(multipart("/api/apoderados/parentescos/importaciones").file(archivo)
                        .with(user("admin-prueba").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.rejectedRows").value(1));
        mvc.perform(multipart("/api/apoderados/importaciones").file(archivo)
                        .with(user("docente-prueba").roles("DOCENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaRutaInexistenteRespondeNoEncontradaEnLugarDeErrorInterno() throws Exception {
        mvc.perform(get("/api/apoderados/importaciones/plantilla-que-no-existe")
                        .with(user("admin-prueba").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUTA_NO_ENCONTRADA"));
    }

    private Workbook download(String path, String filename) throws Exception {
        byte[] content = mvc.perform(get(path).with(user("secretaria-prueba").roles("SECRETARIA")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + filename + "\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return WorkbookFactory.create(new ByteArrayInputStream(content));
    }

    private List<String> cells(Row row) {
        List<String> values = new ArrayList<>();
        row.forEach(cell -> values.add(cell.getStringCellValue()));
        return values;
    }
}
