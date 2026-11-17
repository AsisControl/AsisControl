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
    @Autowired private com.asiscontrol.repository.ApoderadoRepository apoderadoRepository;
    @Autowired private com.asiscontrol.repository.AlumnoApoderadoRepository vinculos;
    @Autowired private com.asiscontrol.repository.AccesoUsuarioRepository accesos;
    @Autowired private com.asiscontrol.repository.RolRepository roles;
    @Autowired private UsuarioService usuarios;

    @Test
    void familiaNuevaImportaDosHijosUnApoderadoYDosVinculosSinCrearCuentas() {
        var familia = new Familia();
        long personasAntes = personaRepository.count(), apoderadosAntes = apoderadoRepository.count();
        long alumnosAntes = alumnoRepository.count(), vinculosAntes = vinculos.count(), cuentasAntes = accesos.count();
        var resultado = carga.procesar(familia.dosEstudiantes(), familia.apoderados(), familia.dosParentescos(), true);
        assertThat(resultado.valid()).isTrue();
        assertThat(resultado.saved()).isTrue();
        assertThat(personaRepository.count()).isEqualTo(personasAntes + 3);
        assertThat(apoderadoRepository.count()).isEqualTo(apoderadosAntes + 1);
        assertThat(alumnoRepository.count()).isEqualTo(alumnosAntes + 2);
        assertThat(vinculos.count()).isEqualTo(vinculosAntes + 2);
        assertThat(accesos.count()).isEqualTo(cuentasAntes);
        var madre = personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, familia.madreDni).orElseThrow();
        var apoderado = apoderadoRepository.findByPersonaIdAndActivoTrue(madre.getId()).orElseThrow();
        assertThat(personas.getApoderado(apoderado.getId()).alumnos()).hasSize(2);
    }

    @Test
    void agregaSegundoHijoSinArchivoDeApoderadosYConservaSuUnicaCuenta() {
        var familia = new Familia();
        assertThat(carga.procesar(familia.estudiantes(), familia.apoderados(), familia.parentescos("MADRE"), true).saved()).isTrue();
        var cuenta = familia.crearCuenta();
        long personasAntes = personaRepository.count(), apoderadosAntes = apoderadoRepository.count();
        long alumnosAntes = alumnoRepository.count(), vinculosAntes = vinculos.count(), cuentasAntes = accesos.count();
        var resultado = carga.procesar(familia.segundoEstudiante(), null, familia.segundoParentesco(), true);
        assertThat(resultado.saved()).isTrue();
        assertThat(resultado.files()).containsOnlyKeys("estudiantes", "parentescos");
        assertThat(personaRepository.count()).isEqualTo(personasAntes + 1);
        assertThat(apoderadoRepository.count()).isEqualTo(apoderadosAntes);
        assertThat(alumnoRepository.count()).isEqualTo(alumnosAntes + 1);
        assertThat(vinculos.count()).isEqualTo(vinculosAntes + 1);
        assertThat(accesos.count()).isEqualTo(cuentasAntes);
        assertThat(usuarios.get(cuenta.id()).personaId()).isEqualTo(cuenta.personaId());
        var apoderado = apoderadoRepository.findByPersonaIdAndActivoTrue(cuenta.personaId()).orElseThrow();
        assertThat(personas.getApoderado(apoderado.getId()).alumnos()).hasSize(2);
    }

    @Test
    void reimportarApoderadoRechazaTodoElLoteSinDuplicadosNiSegundoHijoParcial() {
        var familia = new Familia();
        assertThat(carga.procesar(familia.estudiantes(), familia.apoderados(), familia.parentescos("MADRE"), true).saved()).isTrue();
        familia.crearCuenta();
        long personasAntes = personaRepository.count(), apoderadosAntes = apoderadoRepository.count();
        long alumnosAntes = alumnoRepository.count(), vinculosAntes = vinculos.count(), cuentasAntes = accesos.count();
        var resultado = carga.procesar(familia.segundoEstudiante(), familia.apoderados(), familia.segundoParentesco(), true);
        assertThat(resultado.valid()).isFalse();
        assertThat(resultado.saved()).isFalse();
        assertThat(resultado.files().get("apoderados").result().issues()).anySatisfy(issue ->
                assertThat(issue.message()).contains("ya esta registrada como apoderado"));
        assertThat(personaRepository.count()).isEqualTo(personasAntes);
        assertThat(apoderadoRepository.count()).isEqualTo(apoderadosAntes);
        assertThat(alumnoRepository.count()).isEqualTo(alumnosAntes);
        assertThat(vinculos.count()).isEqualTo(vinculosAntes);
        assertThat(accesos.count()).isEqualTo(cuentasAntes);
        assertThat(alumnoRepository.findByCodigoAlumnoIgnoreCase(familia.codigo2)).isEmpty();
        assertThat(personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, familia.segundoDni)).isEmpty();
    }

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
        var sinCabeceras = excel("parentescos.xlsx", List.of("codigoAlumno"));

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
        var alumno = alumnoRepository.findByCodigoAlumnoIgnoreCase(familia.codigo).orElseThrow();
        assertThat(personas.getAlumno(alumno.getId()).apoderados())
                .extracting(PersonaDtos.ApoderadoVinculadoResponse::nombreCompleto)
                .containsExactly("Carmen Ramos Quispe");
    }

    /** Un estudiante y su madre con documentos y codigo unicos en cada prueba. */
    private class Familia {
        final String alumnoDni = dni();
        final String madreDni = dni();
        final String codigo = "SIM" + alumnoDni;
        final String segundoDni = dni();
        final String codigo2 = "SIM" + segundoDni;

        List<String> filaSegundo() {
            return List.of(codigo2, "Lucia", "Ramos", "Quispe", "DNI", segundoDni,
                    "FEMENINO", "2011-06-15", "", segundoDni + "@alumno.pe", "");
        }
        MockMultipartFile segundoEstudiante() { return excel("estudiantes.xlsx", STUDENT, filaSegundo()); }
        MockMultipartFile dosEstudiantes() {
            return excel("estudiantes.xlsx", STUDENT,
                    List.of(codigo, "Mateo", "Ramos", "Quispe", "DNI", alumnoDni, "MASCULINO", "2013-04-10", "", alumnoDni + "@alumno.pe", ""), filaSegundo());
        }
        MockMultipartFile segundoParentesco() {
            return excel("parentescos.xlsx", PlantillaFamiliaService.RELATIONSHIP_HEADERS, List.of(codigo2, "DNI", madreDni, "MADRE", "SI"));
        }
        MockMultipartFile dosParentescos() {
            return excel("parentescos.xlsx", PlantillaFamiliaService.RELATIONSHIP_HEADERS,
                    List.of(codigo, "DNI", madreDni, "MADRE", "SI"), List.of(codigo2, "DNI", madreDni, "MADRE", "SI"));
        }
        com.asiscontrol.dto.seguridad.UsuarioDtos.Response crearCuenta() {
            var madre = personaRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, madreDni).orElseThrow();
            return usuarios.create(new com.asiscontrol.dto.seguridad.UsuarioDtos.CreateRequest(madre.getId(),
                    roles.findByNombreIgnoreCase("APODERADO").orElseThrow().getId(), "madre" + madreDni,
                    madreDni + "@familia.pe", "Temporal2026Clave!", true));
        }

        MockMultipartFile estudiantes() {
            return excel("estudiantes.xlsx", STUDENT, List.of(codigo, "Mateo", "Ramos", "Quispe",
                    "DNI", alumnoDni, "MASCULINO", "2013-04-10", "", alumnoDni + "@alumno.pe", ""));
        }

        MockMultipartFile apoderados() {
            return excel("apoderados.xlsx", PlantillaFamiliaService.GUARDIAN_HEADERS,
                    List.of("DNI", madreDni, "Carmen", "Ramos", "Quispe", "FEMENINO",
                            "1984-07-21", "", madreDni + "@familia.pe", ""));
        }

        MockMultipartFile parentescos(String parentesco) {
            return excel("parentescos.xlsx", PlantillaFamiliaService.RELATIONSHIP_HEADERS,
                    List.of(codigo, "DNI", madreDni, parentesco, "SI"));
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
