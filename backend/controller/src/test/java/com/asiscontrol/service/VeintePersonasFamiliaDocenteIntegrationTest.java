package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.repository.AccesoUsuarioRepository;
import com.asiscontrol.repository.AccesoUsuarioRolRepository;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.PersonaRepository;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Cohorte de 20 personas: 10 alumnos, 7 apoderados y 5 docentes. Dos docentes
 * son también apoderados, de modo que hay 22 perfiles y solo 20 identidades/cuentas.
 * La carga familiar usa los mismos tres Excel y la misma transacción que la pantalla.
 */
@SpringBootTest
@ActiveProfiles("test")
class VeintePersonasFamiliaDocenteIntegrationTest {

    private static final List<String> ALUMNO_HEADERS = List.of(
            "codigoAlumno", "nombres", "apellidoPaterno", "apellidoMaterno",
            "tipoDocumento", "numeroDocumento", "sexo", "fechaNacimiento",
            "telefono", "correo", "direccion");
    private static final String[] ALUMNOS = {
            "Mateo", "Lucia", "Diego", "Camila", "Bruno",
            "Sofia", "Nicolas", "Valentina", "Andres", "Daniela"
    };
    private static final String[] APODERADOS = {
            "Rosa", "Jorge", "Elena", "Carlos", "Luciana", "Pedro", "Valeria"
    };
    private static final String[] SEXOS = {
            "FEMENINO", "MASCULINO", "FEMENINO", "MASCULINO",
            "FEMENINO", "MASCULINO", "FEMENINO"
    };
    private static final int[] PRINCIPALES = {0, 0, 1, 1, 2, 3, 4, 5, 6, 6};
    private static final Parentesco[] PARENTESCOS = {
            Parentesco.MADRE, Parentesco.MADRE, Parentesco.PADRE, Parentesco.PADRE,
            Parentesco.MADRE, Parentesco.PADRE, Parentesco.MADRE, Parentesco.PADRE,
            Parentesco.TUTOR_LEGAL, Parentesco.TUTOR_LEGAL
    };

    @Autowired private CargaMasivaFamiliasService carga;
    @Autowired private PersonaService personas;
    @Autowired private PersonaRepository personaRepository;
    @Autowired private AlumnoRepository alumnos;
    @Autowired private ApoderadoRepository apoderados;
    @Autowired private DocenteRepository docentes;
    @Autowired private AlumnoApoderadoRepository vinculos;
    @Autowired private AccesoUsuarioRepository accesos;
    @Autowired private AccesoUsuarioRolRepository roles;

    @Test
    void cargaVeintePersonasConParentescosYDocentesQueTambienSonApoderados() {
        var hoy = LocalDate.now(ZoneId.of("America/Lima"));
        var nacimientoAdulto = hoy.minusYears(35);
        var nacimientoAlumno = hoy.minusYears(13);
        int base = 10_000_000 + Math.floorMod(UUID.randomUUID().hashCode(), 79_000_000);
        long personasAntes = personaRepository.count();
        long cuentasAntes = accesos.count();

        // Primera dirección: el docente ya existe cuando llega en el Excel de apoderados.
        var docentePrimero = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                identidad(APODERADOS[0], documento(base, 10), Sexo.FEMENINO, nacimientoAdulto),
                hoy.minusYears(2), Especialidad.MATEMATICA));

        var filasAlumnos = new ArrayList<List<String>>();
        var filasApoderados = new ArrayList<List<String>>();
        var filasParentescos = new ArrayList<List<String>>();
        for (int i = 0; i < ALUMNOS.length; i++) {
            filasAlumnos.add(List.of("", ALUMNOS[i], "Prueba", "Familia", "DNI",
                    documento(base, i), i % 2 == 0 ? "MASCULINO" : "FEMENINO",
                    nacimientoAlumno.minusDays(i).toString(), "", "", ""));
            int principal = PRINCIPALES[i];
            filasParentescos.add(parentesco(documento(base, i), documento(base, 10 + principal),
                    PARENTESCOS[i], true));
        }
        for (int i = 0; i < APODERADOS.length; i++) {
            filasApoderados.add(List.of("DNI", documento(base, 10 + i), APODERADOS[i],
                    "Prueba", "Familia", SEXOS[i], nacimientoAdulto.toString(), "",
                    "987654321", correo(documento(base, 10 + i)), ""));
        }
        // Cuatro vínculos adicionales prueban dos apoderados por alumno y hogares compartidos.
        filasParentescos.add(parentesco(documento(base, 0), documento(base, 11), Parentesco.PADRE, false));
        filasParentescos.add(parentesco(documento(base, 2), documento(base, 12), Parentesco.OTRO_FAMILIAR, false));
        filasParentescos.add(parentesco(documento(base, 4), documento(base, 13), Parentesco.PADRE, false));
        filasParentescos.add(parentesco(documento(base, 6), documento(base, 15), Parentesco.PADRE, false));

        var resultado = carga.procesar(
                excel("estudiantes.xlsx", ALUMNO_HEADERS, filasAlumnos),
                excel("apoderados.xlsx", PlantillaFamiliaService.GUARDIAN_HEADERS, filasApoderados),
                excel("parentescos.xlsx", PlantillaFamiliaService.RELATIONSHIP_HEADERS, filasParentescos),
                true);

        assertThat(resultado.valid()).isTrue();
        assertThat(resultado.saved()).isTrue();
        assertThat(resultado.files().get("estudiantes").result().importedRows()).isEqualTo(10);
        assertThat(resultado.files().get("apoderados").result().importedRows()).isEqualTo(7);
        assertThat(resultado.files().get("parentescos").result().importedRows()).isEqualTo(14);
        var credencialDocenteApoderado = resultado.files().get("apoderados")
                .result().credentials().getFirst();
        assertThat(credencialDocenteApoderado.username())
                .isEqualTo(docentePrimero.credencialAcceso().username());
        assertThat(credencialDocenteApoderado.temporaryPassword()).isNull();

        // Segunda dirección: el apoderado recibe después un perfil de docente.
        var docenteDespues = personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                identidad(APODERADOS[4], documento(base, 14), Sexo.FEMENINO, nacimientoAdulto),
                hoy.minusYears(1), Especialidad.COMUNICACION));
        var credencialApoderadoDocente = resultado.files().get("apoderados")
                .result().credentials().get(4);
        assertThat(docenteDespues.credencialAcceso().username())
                .isEqualTo(credencialApoderadoDocente.username());
        assertThat(docenteDespues.credencialAcceso().temporaryPassword()).isNull();
        for (int i = 17; i < 20; i++) {
            personas.createDocente(new PersonaDtos.DocenteCreateRequest(
                    identidad("Docente" + (i - 16), documento(base, i),
                            Sexo.PREFIERE_NO_INDICAR, nacimientoAdulto),
                    hoy.minusMonths(6), Especialidad.TUTORIA));
        }

        var identidades = new HashSet<Long>();
        for (int i = 0; i < 20; i++) {
            var persona = personaRepository.findByTipoDocumentoAndNumeroDocumento(
                    TipoDocumento.DNI, documento(base, i)).orElseThrow();
            assertThat(identidades.add(persona.getId())).isTrue();
            assertThat(accesos.findAllByPersonaIdAndActivoTrue(persona.getId())).hasSize(1);
            if (i < 10) assertThat(alumnos.findByPersonaIdAndActivoTrue(persona.getId())).isPresent();
            if (i >= 10 && i < 17) assertThat(apoderados.findByPersonaIdAndActivoTrue(persona.getId())).isPresent();
            if (i == 10 || i == 14 || i >= 17) {
                assertThat(docentes.findByPersonaIdAndActivoTrue(persona.getId())).isPresent();
            }
        }
        assertThat(identidades).hasSize(20);
        assertThat(personaRepository.count()).isEqualTo(personasAntes + 20);
        assertThat(accesos.count()).isEqualTo(cuentasAntes + 20);

        for (int i = 0; i < 10; i++) {
            int indice = i;
            var alumno = alumnos.findByPersonaIdAndActivoTrue(personaRepository
                    .findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, documento(base, i))
                    .orElseThrow().getId()).orElseThrow();
            var relaciones = vinculos.findAllByAlumnoIdAndActivoTrue(alumno.getId());
            assertThat(relaciones).hasSize(i == 0 || i == 2 || i == 4 || i == 6 ? 2 : 1);
            assertThat(relaciones).filteredOn(relacion -> relacion.isPrincipal()).hasSize(1);
            assertThat(relaciones).filteredOn(relacion -> relacion.isPrincipal())
                    .singleElement().satisfies(relacion ->
                            assertThat(relacion.getParentesco()).isEqualTo(PARENTESCOS[indice]));
        }

        var madre = apoderados.findByPersonaIdAndActivoTrue(docentePrimero.persona().id()).orElseThrow();
        assertThat(vinculos.findAllByApoderadoIdAndActivoTrue(madre.getId())).hasSize(2);
        assertThat(docenteDespues.persona().id()).isEqualTo(personaRepository
                .findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, documento(base, 14))
                .orElseThrow().getId());
        for (long personaId : List.of(docentePrimero.persona().id(), docenteDespues.persona().id())) {
            var cuenta = accesos.findByPersonaId(personaId).orElseThrow();
            assertThat(roles.buscarDisponibles(cuenta.getId()))
                    .extracting(rol -> rol.getNombre())
                    .containsExactly("APODERADO", "DOCENTE");
            var apoderado = apoderados.findByPersonaIdAndActivoTrue(personaId).orElseThrow();
            assertThat(vinculos.findAllByApoderadoIdAndActivoTrue(apoderado.getId())).isNotEmpty();
        }
    }

    private static String documento(int base, int indice) { return String.valueOf(base + indice); }
    private static String correo(String documento) { return documento + "@example.test"; }

    private static PersonaDtos.PersonaRequest identidad(
            String nombre, String documento, Sexo sexo, LocalDate nacimiento) {
        return new PersonaDtos.PersonaRequest(nombre, "Prueba", "Familia", TipoDocumento.DNI,
                documento, null, sexo, nacimiento, "987654321", correo(documento), null);
    }

    private static List<String> parentesco(
            String alumno, String apoderado, Parentesco tipo, boolean principal) {
        return List.of("DNI", alumno, "DNI", apoderado, tipo.name(), principal ? "SI" : "NO");
    }

    private static MockMultipartFile excel(
            String nombre, List<String> cabeceras, List<List<String>> filas) {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet();
            escribir(hoja.createRow(0), cabeceras);
            for (int i = 0; i < filas.size(); i++) escribir(hoja.createRow(i + 1), filas.get(i));
            libro.write(salida);
            return new MockMultipartFile("archivo", nombre, null, salida.toByteArray());
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void escribir(Row fila, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) fila.createCell(i).setCellValue(valores.get(i));
    }
}
