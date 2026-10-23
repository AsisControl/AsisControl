package com.asiscontrol.service;

import com.asiscontrol.exception.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportacionAlumnoServiceTest {
    private PersonaService personas;
    private ImportacionAlumnoService service;
    private ValidatorFactory factory;
    private final List<String> headers = List.of("codigoAlumno", "nombres", "apellidoPaterno",
            "apellidoMaterno", "tipoDocumento", "numeroDocumento", "nacionalidadId", "sexo",
            "fechaNacimiento", "telefono", "correo", "direccion");

    @BeforeEach void setup() {
        personas = mock(PersonaService.class);
        factory = Validation.buildDefaultValidatorFactory();
        service = new ImportacionAlumnoService(personas, factory.getValidator(), mock(AuditoriaService.class));
    }
    @AfterEach void close() { factory.close(); }

    private MockMultipartFile file(java.util.function.Consumer<Workbook> fill) throws Exception {
        try (Workbook w = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            fill.accept(w); w.write(out);
            return new MockMultipartFile("file", "alumnos.xlsx", "application/octet-stream", out.toByteArray());
        }
    }
    private Sheet base(Workbook w) {
        var sheet = w.createSheet("Alumnos");
        var header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) header.createCell(i).setCellValue(headers.get(i));
        var row = sheet.createRow(1);
        String[] values = {"EST-001", "Maria", "Torres", "Flores", "DNI", "70000001", "",
                "FEMENINO", "2012-05-15", "999999999", "familia@example.test", "Lima"};
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
        return sheet;
    }

    @Test void generaPlantillaImportableConCabecerasYFilaEjemplo() throws Exception {
        byte[] content = service.generateTemplate();
        assertThat(content).isNotEmpty();
        var result = service.importStudents(new MockMultipartFile("file", "plantilla.xlsx", null, content));
        assertThat(result.importedRows()).isEqualTo(1);
        verify(personas).createAlumno(argThat(r -> r.codigoAlumno().equals("EST-0001")
                && r.persona().fechaNacimiento().equals(LocalDate.of(2012, 5, 15))));
    }

    @Test void importaFilasValidasYOmiteFilasVacias() throws Exception {
        var result = service.importStudents(file(w -> {
            var s = base(w); s.createRow(2); s.createRow(4).createCell(0).setCellValue("");
            s.getRow(1).getCell(6).setCellValue("5");
            s.getRow(1).getCell(8).setCellValue("15/05/2012");
        }));
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.importedRows()).isEqualTo(1);
        verify(personas).createAlumno(argThat(r -> r.persona().nacionalidadId().equals(5L)));
    }

    @Test void admiteFechaNativaDeExcelYOpcionalesAusentes() throws Exception {
        var result = service.importStudents(file(w -> {
            var sheet = base(w);
            var cell = sheet.getRow(1).getCell(8);
            cell.setCellValue(LocalDateTime.of(2012, 5, 15, 0, 0));
            var style = w.createCellStyle();
            style.setDataFormat(w.createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(style);
            for (int i : new int[]{6, 9, 11}) {
                sheet.getRow(0).removeCell(sheet.getRow(0).getCell(i));
                sheet.getRow(1).removeCell(sheet.getRow(1).getCell(i));
            }
        }));
        assertThat(result.importedRows()).isEqualTo(1);
        verify(personas).createAlumno(argThat(r -> r.persona().nacionalidadId() == null
                && r.persona().telefono() == null));
    }

    @ParameterizedTest
    @CsvSource({"0,''", "4,INVALIDO", "6,abc", "6,10.01", "6,0", "7,INVALIDO", "8,no-es-fecha", "8,30/02/2012", "10,correo-invalido", "8,2099-01-01"})
    void reportaFilaInvalidaSinPersistir(int column, String value) throws Exception {
        var result = service.importStudents(file(w -> base(w).getRow(1).getCell(column).setCellValue(value)));
        assertThat(result.rejectedRows()).isEqualTo(1);
        assertThat(result.issues()).hasSize(1);
        verifyNoInteractions(personas);
    }

    @Test void reportaErrorDeNegocioYContinuaConLasDemasFilas() throws Exception {
        when(personas.createAlumno(any())).thenThrow(new ConflictException("DUPLICADO", "Documento repetido"));
        var result = service.importStudents(file(this::base));
        assertThat(result.importedRows()).isZero();
        assertThat(result.issues().getFirst().message()).isEqualTo("Documento repetido");
    }

    @Test void rechazaArchivoVacioExtensionYTamanio() {
        assertThatThrownBy(() -> service.importStudents(null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.importStudents(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.importStudents(new MockMultipartFile("file", "x.csv", null, new byte[]{1})))
                .isInstanceOf(BusinessRuleException.class);
        MultipartFile large = mock(MultipartFile.class);
        when(large.isEmpty()).thenReturn(false); when(large.getSize()).thenReturn(6L * 1024 * 1024);
        assertThatThrownBy(() -> service.importStudents(large)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void rechazaHojaVaciaYCabecerasIncompletas() throws Exception {
        var empty = file(w -> w.createSheet("Vacia"));
        assertThatThrownBy(() -> service.importStudents(empty)).isInstanceOf(BusinessRuleException.class);
        var missing = file(w -> w.createSheet("Incompleta").createRow(0).createCell(0).setCellValue("codigoAlumno"));
        assertThatThrownBy(() -> service.importStudents(missing)).hasMessageContaining("Faltan las columnas");
    }

    @Test void traduceErrorDeLectura() throws Exception {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("x.xlsx");
        when(broken.getInputStream()).thenThrow(new IOException("fallo"));
        assertThatThrownBy(() -> service.importStudents(broken)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void rechazaBinarioCorruptoYSanitizaErroresTecnicosPorFila() throws Exception {
        var corrupto=new MockMultipartFile("file","x.xlsx",null,new byte[]{1,2,3});
        assertThatThrownBy(()->service.importStudents(corrupto)).isInstanceOf(BusinessRuleException.class);
        when(personas.createAlumno(any())).thenThrow(new IllegalStateException("SQL password=secreto"));
        var result=service.importStudents(file(this::base));
        assertThat(result.issues().getFirst().message()).doesNotContain("SQL","password","secreto");
    }
}
