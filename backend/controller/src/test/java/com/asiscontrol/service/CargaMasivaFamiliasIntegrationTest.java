package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.AlumnoRepository;
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
import java.util.List;
import java.util.UUID;

/**
 * Sin @Transactional: la carga abre su propia transaccion y aqui se comprueba que, si no se
 * confirma o hay errores, no queda ninguna fila guardada.
 */
@SpringBootTest
@ActiveProfiles("test")
class CargaMasivaFamiliasIntegrationTest {

    private static final List<String> STUDENT = List.of(
            "codigoAlumno", "nombres", "apellidoPaterno", "apellidoMaterno", "tipoDocumento",
            "numeroDocumento", "sexo", "fechaNacimiento", "telefono", "correo", "direccion");

    @Autowired private CargaMasivaFamiliasService carga;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private AlumnoRepository alumnoRepository;
    @Autowired private PersonaService personas;
    @Autowired private com.asiscontrol.repository.AccesoUsuarioRepository accesos;

    @Test
    void validarRevisaLosTresArchivosJuntosYNoGuardaNada() {
        var familia = new Familia();

        var resultado = carga.procesar(
                familia.estudiantes(), familia.apoderados(), familia.parentescos("MADRE"), false);

        assertThat(resultado.valid()).isTrue();
        assertThat(resultado.saved()).isFalse();
        assertThat(resultado.files()).containsOnlyKeys("estudiantes", "apoderados", "parentescos");
        // Los parentescos encontraron al estudiante y al apoderado de los otros dos archivos.
        assertThat(resultado.files().get("parentescos").result().importedRows()).isEqualTo(1);
        // Al validar se anticipan los usuarios, pero ninguna contraseña: no se guardó nada.
        assertThat(resultado.files().get("estudiantes").result().credentials())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.accessCode()).matches("[0-9]{8}");
                    assertThat(c.temporaryPassword()).isNull();
                });
        assertThat(resultado.files().get("apoderados").result().credentials())
                .allSatisfy(c -> assertThat(c.temporaryPassword()).isNull());
        familia.noSeGuardo();
    }

    @Test
    void unaFilaConErrorImpideGuardarLasDemas() {
        var familia = new Familia();

        var resultado = carga.procesar(
                familia.estudiantes(), familia.apoderados(), familia.parentescos("Mama"), true);

        assertThat(resultado.valid()).isFalse();
        assertThat(resultado.saved()).isFalse();
        assertThat(resultado.files().get("estudiantes").result().issues()).isEmpty();
        assertThat(resultado.files().get("parentescos").result().issues())
                .extracting(PersonaDtos.ImportIssue::message)
                .containsExactly("El valor de parentesco no esta permitido");
        familia.noSeGuardo();
    }

    @Test
    void unArchivoIlegibleSeInformaYTampocoSeGuardaNada() {
        var familia = new Familia();
        var sinCabeceras = excel("parentescos.xlsx", List.of("numeroDocumentoAlumno"));

        var resultado = carga.procesar(familia.estudiantes(), familia.apoderados(), sinCabeceras, true);

        assertThat(resultado.saved()).isFalse();
        assertThat(resultado.files().get("parentescos").error()).startsWith("Faltan las columnas");
        assertThat(resultado.files().get("parentescos").result()).isNull();
        familia.noSeGuardo();
    }

    @Test
    void confirmarSinErroresGuardaTodoJunto() {
        var familia = new Familia();

        var resultado = carga.procesar(
                familia.estudiantes(), familia.apoderados(), familia.parentescos("MADRE"), true);

        assertThat(resultado.valid()).isTrue();
        assertThat(resultado.saved()).isTrue();
        var persona = personaRepository
                .findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, familia.alumnoDni)
                .orElseThrow();
        var alumno = alumnoRepository.findByPersonaIdAndActivoTrue(persona.getId()).orElseThrow();
        // La columna codigoAlumno de un archivo antiguo se ignora: el código lo genera el sistema.
        assertThat(alumno.getCodigoAlumno()).startsWith("EST").isNotEqualTo(familia.codigo);
        assertThat(personas.getAlumno(alumno.getId()).apoderados())
                .extracting(PersonaDtos.ApoderadoVinculadoResponse::nombreCompleto)
                .containsExactly("Carmen Ramos Quispe");
        var estudiante = resultado.files().get("estudiantes").result().credentials().getFirst();
        var madre = resultado.files().get("apoderados").result().credentials().getFirst();
        assertThat(estudiante.role()).isEqualTo("ALUMNO");
        assertThat(estudiante.code()).isEqualTo(alumno.getCodigoAlumno());
        assertThat(estudiante.temporaryPassword()).isNotBlank();
        assertThat(madre.role()).isEqualTo("APODERADO");
        assertThat(madre.temporaryPassword()).isNotBlank();
        var cuenta = accesos.findByPersonaId(persona.getId()).orElseThrow();
        assertThat(cuenta.getUsername()).isEqualTo(estudiante.username());
        assertThat(cuenta.isRequiereCambioContrasena()).isTrue();
    }

    @Test
    void unApoderadoConDosHijosTieneUnaSolaCuenta() {
        String hijo = dni(), hija = dni(), madre = dni();
        var estudiantes = excel("estudiantes.xlsx", STUDENT,
                List.of("", "Mateo", "Ramos", "Quispe", "DNI", hijo, "MASCULINO", "2013-04-10", "", "", ""),
                List.of("", "Lucia", "Ramos", "Quispe", "DNI", hija, "FEMENINO", "2014-06-02", "", "", ""));
        var apoderados = excel("apoderados.xlsx", PlantillaFamiliaService.GUARDIAN_HEADERS,
                List.of("DNI", madre, "Carmen", "Ramos", "Quispe", "FEMENINO", "1984-07-21", "",
                        "987654321", "", ""));
        var parentescos = excel("parentescos.xlsx", PlantillaFamiliaService.RELATIONSHIP_HEADERS,
                List.of("DNI", hijo, "DNI", madre, "MADRE", "SI"),
                List.of("DNI", hija, "DNI", madre, "MADRE", "SI"));

        var resultado = carga.procesar(estudiantes, apoderados, parentescos, true);

        assertThat(resultado.saved()).isTrue();
        assertThat(resultado.files().get("estudiantes").result().credentials()).hasSize(2);
        assertThat(resultado.files().get("apoderados").result().credentials()).hasSize(1);
        var persona = personaRepository
                .findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, madre).orElseThrow();
        assertThat(accesos.findByPersonaId(persona.getId()).orElseThrow().getCodigo())
                .matches("[0-9]{8}");
    }

    @Test
    void rechazaUnaFichaNuevaSinDocumentoEnLaCargaMasiva() {
        var sinDocumento = excel("estudiantes.xlsx", STUDENT,
                List.of("", "Mateo", "Ramos", "Quispe", "", "", "MASCULINO", "2013-04-10",
                        "", "", ""));
        var resultado = carga.procesar(sinDocumento, null, null, true);

        assertThat(resultado.saved()).isFalse();
        assertThat(resultado.files().get("estudiantes").result().issues()).hasSize(1);
        assertThat(resultado.files().get("estudiantes").result().credentials()).isEmpty();
    }

    /** Un estudiante y su madre con documentos y codigo unicos en cada prueba. */
    private class Familia {
        final String alumnoDni = dni();
        final String madreDni = dni();
        final String codigo = "SIM" + alumnoDni;

        MockMultipartFile estudiantes() {
            return excel("estudiantes.xlsx", STUDENT, List.of(codigo, "Mateo", "Ramos", "Quispe",
                    "DNI", alumnoDni, "MASCULINO", "2013-04-10", "", alumnoDni + "@alumno.pe", ""));
        }

        MockMultipartFile apoderados() {
            return excel("apoderados.xlsx", PlantillaFamiliaService.GUARDIAN_HEADERS,
                    List.of("DNI", madreDni, "Carmen", "Ramos", "Quispe", "FEMENINO",
                            "1984-07-21", "", "", madreDni + "@familia.pe", ""));
        }

        MockMultipartFile parentescos(String parentesco) {
            return excel("parentescos.xlsx", PlantillaFamiliaService.RELATIONSHIP_HEADERS,
                    List.of("DNI", alumnoDni, "DNI", madreDni, parentesco, "SI"));
        }

        void noSeGuardo() {
            assertThat(alumnoRepository.findByCodigoAlumnoIgnoreCase(codigo)).isEmpty();
            assertThat(personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, alumnoDni))
                    .isEmpty();
            assertThat(personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, madreDni))
                    .isEmpty();
        }
    }

    @SafeVarargs
    private static MockMultipartFile excel(String nombre, List<String> headers, List<String>... rows) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            write(sheet.createRow(0), headers);
            for (int index = 0; index < rows.length; index++) {
                write(sheet.createRow(index + 1), rows[index]);
            }
            workbook.write(output);
            return new MockMultipartFile("archivo", nombre, null, output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void write(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }

    /** DNI de 8 dígitos distinto en cada llamada. */
    private static String dni() {
        return String.valueOf(10_000_000 + Math.floorMod(UUID.randomUUID().hashCode(), 89_999_999));
    }
}
