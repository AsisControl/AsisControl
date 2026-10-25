package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.PersonaRepository;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sin @Transactional: cada fila se guarda en su propia transaccion, como en produccion,
 * y los datos de cada prueba usan documentos y codigos unicos.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportacionFamiliaIntegrationTest {

    private static final List<String> GUARDIAN = PlantillaFamiliaService.GUARDIAN_HEADERS;
    private static final List<String> RELATIONSHIP = PlantillaFamiliaService.RELATIONSHIP_HEADERS;

    @Autowired private ImportacionFamiliaService importacion;
    @Autowired private PlantillaFamiliaService plantillas;
    @Autowired private PersonaService personas;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private ApoderadoRepository apoderadoRepository;

    @Test
    void laPlantillaDeApoderadosSeImportaTalCual() {
        var result = importacion.importGuardians(
                new MockMultipartFile("archivo", "apoderados.xlsx", null,
                        plantillas.generateGuardianTemplate()));

        assertThat(result.issues()).isEmpty();
        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "40000001"))
                .hasValueSatisfying(persona ->
                        assertThat(apoderadoRepository.findByPersonaIdAndActivoTrue(persona.getId()))
                                .isPresent());
    }

    @Test
    void registraLasFilasValidasYReportaLasDemasPorFila() {
        String a = dni(), b = dni(), docente = dni();
        personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                persona(docente, "Rosa", "Pizarro"), "DOC" + docente, LocalDate.now(),
                Especialidad.TUTORIA));

        var result = importacion.importGuardians(excel(GUARDIAN,
                guardian(a, "Luis", "Rojas", "MASCULINO", a + "@familia.pe"),
                guardian(b, "Ana", "Vega", "DESCONOCIDO", b + "@familia.pe"),
                guardian(dni(), "Sin", "Correo", "FEMENINO", ""),
                guardian(docente, "rosa", "PIZARRO", "FEMENINO", docente + "@familia.pe"),
                guardian(a, "Luis", "Rojas", "MASCULINO", a + "@familia.pe")));

        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.issues()).extracting(PersonaDtos.ImportIssue::row, PersonaDtos.ImportIssue::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3, "El valor de sexo no esta permitido"),
                        org.assertj.core.groups.Tuple.tuple(4, "La columna correo es obligatoria"),
                        org.assertj.core.groups.Tuple.tuple(6,
                                "La persona con este documento ya esta registrada como apoderado"));
        // La docente se reutilizo como apoderada en lugar de duplicarse.
        var rosa = personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, docente)
                .orElseThrow();
        assertThat(apoderadoRepository.findByPersonaIdAndActivoTrue(rosa.getId())).isPresent();
    }

    @Test
    void vinculaPorCodigoYDocumentoConUnSoloPrincipalPorEstudiante() {
        String codigo = "EST" + dni();
        var alumno = personas.createAlumno(new PersonaDtos.AlumnoCreateRequest(
                persona(dni(), "Mateo", "Ramos", LocalDate.of(2014, 2, 3)), codigo));
        String madre = dni(), padre = dni();
        importacion.importGuardians(excel(GUARDIAN,
                guardian(madre, "Carmen", "Ramos", "FEMENINO", madre + "@familia.pe"),
                guardian(padre, "Jorge", "Ramos", "MASCULINO", padre + "@familia.pe")));

        var result = importacion.importRelationships(excel(RELATIONSHIP,
                List.of(codigo.toLowerCase(), "DNI", madre, "MADRE", "SI"),
                List.of(codigo, "DNI", padre, "PADRE", "si"),
                List.of(codigo, "DNI", padre, "PADRE", "NO"),
                List.of("NO-EXISTE", "DNI", padre, "PADRE", "NO"),
                List.of(codigo, "DNI", "99999999", "PADRE", "NO"),
                List.of(codigo, "DNI", padre, "NO_DECLARADO", "NO")));

        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.issues()).extracting(PersonaDtos.ImportIssue::row).containsExactly(3, 5, 6, 7);
        assertThat(result.issues().get(0).message()).contains("ya tiene un apoderado principal en la fila 2");
        assertThat(result.issues().get(1).message()).isEqualTo("No hay un estudiante habilitado con el codigo NO-EXISTE");
        assertThat(result.issues().get(2).message()).contains("Importalo primero");
        assertThat(result.issues().get(3).message()).isEqualTo("El valor de parentesco no esta permitido");
        assertThat(personas.getAlumno(alumno.id()).apoderados())
                .extracting(PersonaDtos.ApoderadoVinculadoResponse::parentesco,
                        PersonaDtos.ApoderadoVinculadoResponse::principal)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(Parentesco.MADRE, true),
                        org.assertj.core.groups.Tuple.tuple(Parentesco.PADRE, false));
    }

    private List<String> guardian(String dni, String nombres, String apellido, String sexo, String correo) {
        return List.of("DNI", dni, nombres, apellido, "Quispe", sexo, "1984-07-21", "", correo, "");
    }

    private PersonaDtos.PersonaRequest persona(String dni, String nombres, String apellido) {
        return persona(dni, nombres, apellido, LocalDate.of(1984, 7, 21));
    }

    private PersonaDtos.PersonaRequest persona(
            String dni, String nombres, String apellido, LocalDate nacimiento) {
        return new PersonaDtos.PersonaRequest(nombres, apellido, "Quispe", TipoDocumento.DNI, dni,
                null, Sexo.FEMENINO, nacimiento, null, dni + "@colegio.pe", null);
    }

    @SafeVarargs
    private MockMultipartFile excel(List<String> headers, List<String>... rows) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            write(sheet.createRow(0), headers);
            for (int index = 0; index < rows.length; index++) {
                write(sheet.createRow(index + 1), rows[index]);
            }
            workbook.write(output);
            return new MockMultipartFile("archivo", "datos.xlsx", null, output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void write(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }

    /** DNI de 8 dígitos distinto en cada llamada. */
    private String dni() {
        return String.valueOf(10_000_000 + Math.floorMod(UUID.randomUUID().hashCode(), 89_999_999));
    }
}
