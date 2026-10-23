package com.asiscontrol.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asiscontrol.dto.academico.AcademicoDtos;
import com.asiscontrol.entity.RegistroAuditoria;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.GlobalExceptionHandler;
import com.asiscontrol.repository.RegistroAuditoriaRepository;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Cada test se deshace al terminar para no dejar cursos ni alumnos que alteren otros tests. Solo
 * quedan los registros de rechazo, que por diseno se guardan en una transaccion propia.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditoriaIntegrationTest {

    @Autowired CursoService cursos;
    @Autowired AuditoriaService auditoria;
    @Autowired ImportacionAlumnoService importacion;
    @Autowired GlobalExceptionHandler exceptionHandler;
    @Autowired RegistroAuditoriaRepository registros;
    @Autowired PlatformTransactionManager transactions;

    private static String clave() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private List<RegistroAuditoria> buscar(String accion, String registroId) {
        return registros.findAll().stream()
                .filter(registro -> registro.getAccion().equals(accion))
                .filter(registro -> registroId == null || registroId.equals(registro.getRegistroId()))
                .toList();
    }

    @Test
    void crearYActualizarCursoGuardaValoresAntesYDespues() {
        String codigo = "CUR" + clave();
        var creado = cursos.crear(new AcademicoDtos.CrearCursoRequest(codigo, "Algebra", null, 4));

        List<RegistroAuditoria> alta = buscar("CREAR_CURSO", creado.id().toString());
        assertThat(alta).hasSize(1);
        assertThat(alta.getFirst().getModulo()).isEqualTo(ModuloAuditoria.ACADEMICO);
        assertThat(alta.getFirst().getEntidad()).isEqualTo("Curso");
        assertThat(alta.getFirst().getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
        assertThat(alta.getFirst().getNuevoValor()).contains("codigo=" + codigo);

        cursos.actualizar(creado.id(), new AcademicoDtos.ActualizarCursoRequest(
                codigo, "Algebra avanzada", null, 5, creado.version()));

        List<RegistroAuditoria> cambio = buscar("ACTUALIZAR_CURSO", creado.id().toString());
        assertThat(cambio).hasSize(1);
        assertThat(cambio.getFirst().getValorAnterior()).contains("nombre=Algebra");
        assertThat(cambio.getFirst().getNuevoValor()).contains("nombre=Algebra avanzada");
    }

    @Test
    void motivoMasLargoQueLaColumnaSeRecortaSinFallar() {
        String registroId = "MOTIVO-" + clave();
        auditoria.registrar("PRUEBA_MOTIVO_LARGO", ModuloAuditoria.GENERAL, "Prueba", registroId,
                ResultadoAuditoria.EXITOSO, "x".repeat(800), null, null);

        List<RegistroAuditoria> guardados = buscar("PRUEBA_MOTIVO_LARGO", registroId);
        assertThat(guardados).hasSize(1);
        assertThat(guardados.getFirst().getMotivo()).hasSize(500);
    }

    @Test
    void elRechazoSeConservaAunqueLaOperacionHagaRollback() {
        String registroId = "ROLLBACK-" + clave();
        // Transaccion real e independiente de la del test, para poder deshacerla de verdad.
        TransactionTemplate operacion = new TransactionTemplate(transactions);
        operacion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        operacion.executeWithoutResult(status -> {
            auditoria.registrarRechazo("PRUEBA_ROLLBACK", ModuloAuditoria.GENERAL, "Prueba",
                    registroId, ResultadoAuditoria.RECHAZADO, "CODIGO: mensaje");
            auditoria.registrar("PRUEBA_ROLLBACK_EXITO", ModuloAuditoria.GENERAL, "Prueba",
                    registroId, ResultadoAuditoria.EXITOSO, null, null, null);
            status.setRollbackOnly();
        });

        assertThat(buscar("PRUEBA_ROLLBACK", registroId)).hasSize(1);
        // El registro de exito vive en la transaccion de la operacion y se deshace con ella.
        assertThat(buscar("PRUEBA_ROLLBACK_EXITO", registroId)).isEmpty();
    }

    @Test
    void unaEscrituraRechazadaQuedaAuditadaConModuloDelCatalogo() {
        long id = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L) + 1_000_000L;
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/cursos-competencias/" + id + "/reactivar");

        exceptionHandler.handleBusinessRule(
                new BusinessRuleException("REGLA_DE_PRUEBA", "No permitido"), request);

        List<RegistroAuditoria> rechazos = buscar("POST_CURSOS_COMPETENCIAS_REACTIVAR", String.valueOf(id));
        assertThat(rechazos).hasSize(1);
        RegistroAuditoria rechazo = rechazos.getFirst();
        assertThat(rechazo.getResultado()).isEqualTo(ResultadoAuditoria.RECHAZADO);
        assertThat(rechazo.getModulo()).isEqualTo(ModuloAuditoria.ACADEMICO);
        assertThat(rechazo.getEntidad()).isEqualTo("CursosCompetencias");
        assertThat(rechazo.getMotivo()).isEqualTo("REGLA_DE_PRUEBA: No permitido");
    }

    @Test
    void unaLecturaRechazadaNoSeAudita() {
        long id = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L) + 2_000_000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cursos/" + id);

        exceptionHandler.handleBusinessRule(
                new BusinessRuleException("REGLA_DE_PRUEBA", "No permitido"), request);

        assertThat(registros.findAll().stream()
                .filter(registro -> String.valueOf(id).equals(registro.getRegistroId())))
                .isEmpty();
    }

    @Test
    void laImportacionMasivaDejaUnResumenConFilasFallidas() throws IOException {
        String valida = clave();
        String archivo = "alumnos-" + clave() + ".xlsx";
        MockMultipartFile excel = new MockMultipartFile(
                "file", archivo,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel(new String[][] {
                        {"EST" + valida, "Ana", "Rojas", "Diaz", "PASAPORTE", valida,
                                "FEMENINO", "2012-05-15", valida.toLowerCase() + "@example.test"},
                        {"EST" + clave(), "Luis", "Paz", "Soto", "NO_EXISTE", clave(),
                                "MASCULINO", "2012-05-15", "otro@example.test"}
                }));

        var resultado = importacion.importStudents(excel);
        assertThat(resultado.importedRows()).isEqualTo(1);
        assertThat(resultado.rejectedRows()).isEqualTo(1);

        List<RegistroAuditoria> resumen = registros.findAll().stream()
                .filter(registro -> registro.getAccion().equals("IMPORTAR_ALUMNOS"))
                .filter(registro -> registro.getNuevoValor() != null
                        && registro.getNuevoValor().contains("archivo=" + archivo))
                .toList();
        assertThat(resumen).hasSize(1);
        assertThat(resumen.getFirst().getModulo()).isEqualTo(ModuloAuditoria.PERSONAS);
        assertThat(resumen.getFirst().getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
        assertThat(resumen.getFirst().getNuevoValor())
                .contains("filas=2", "importados=1", "fallidos=1");
        assertThat(resumen.getFirst().getMotivo()).isEqualTo("Filas con errores: 3");
    }

    private static byte[] excel(String[][] filas) throws IOException {
        String[] encabezados = {"codigoAlumno", "nombres", "apellidoPaterno", "apellidoMaterno",
                "tipoDocumento", "numeroDocumento", "sexo", "fechaNacimiento", "correo"};
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Alumnos");
            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < encabezados.length; i++) {
                cabecera.createCell(i).setCellValue(encabezados[i]);
            }
            for (int f = 0; f < filas.length; f++) {
                Row fila = hoja.createRow(f + 1);
                for (int c = 0; c < filas[f].length; c++) {
                    fila.createCell(c).setCellValue(filas[f][c]);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
