package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Sexo;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlantillaAlumnoTest {

    @Test
    void omiteNacionalidadYConservaLasColumnasDeLaFicha() throws Exception {
        var importService = new ImportacionAlumnoService(
                mock(PersonaService.class), null, mock(AuditoriaService.class));

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(importService.generateTemplate()))) {
            var sheet = workbook.getSheetAt(0);
            List<String> columnNames = new ArrayList<>();
            sheet.getRow(0).forEach(cell -> columnNames.add(cell.getStringCellValue()));

            assertThat(columnNames).containsExactly(
                    "codigoAlumno", "nombres", "apellidoPaterno", "apellidoMaterno",
                    "tipoDocumento", "numeroDocumento", "sexo", "fechaNacimiento",
                    "telefono", "correo", "direccion");
            assertThat(sheet.getRow(1).getLastCellNum()).isEqualTo((short) columnNames.size());
            assertThat(sheet.getColumnStyle(5).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    void importaLaPlantillaSinDesplazarSexoNacimientoNiContacto() throws Exception {
        PersonaService personaService = mock(PersonaService.class);
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var importService = new ImportacionAlumnoService(
                    personaService, validatorFactory.getValidator(), mock(AuditoriaService.class));
            byte[] template = importService.generateTemplate();
            var file = new MockMultipartFile("archivo", "alumnos.xlsx", null, template);

            var result = importService.importStudents(file);

            assertThat(result.importedRows()).isEqualTo(1);
            assertThat(result.rejectedRows()).isZero();
            var request = ArgumentCaptor.forClass(PersonaDtos.AlumnoCreateRequest.class);
            verify(personaService).createAlumno(request.capture());
            var person = request.getValue().persona();
            assertThat(person.nacionalidadId()).isNull();
            assertThat(person.sexo()).isEqualTo(Sexo.FEMENINO);
            assertThat(person.fechaNacimiento()).isEqualTo(LocalDate.of(2012, 5, 15));
            assertThat(person.telefono()).isEqualTo("999999999");
            assertThat(person.correo()).isEqualTo("familia@ejemplo.com");
            assertThat(person.direccion()).isEqualTo("Lima");
        }
    }
}
