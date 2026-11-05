package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Carga masiva de estudiantes, apoderados y parentescos como una sola operacion, todo o nada.
 *
 * <p>Los tres archivos se procesan en orden dentro de una unica transaccion con las mismas reglas
 * que la importacion de cada archivo, asi los parentescos ven a los estudiantes y apoderados de
 * los otros dos archivos. Al validar siempre se deshace; al confirmar solo se guarda si ningun
 * archivo tiene errores, de modo que nunca queda una carga a medias.
 */
@Service
public class CargaMasivaFamiliasService {

    private final ImportacionAlumnoService alumnos;
    private final ImportacionFamiliaService familias;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transaccion;

    public CargaMasivaFamiliasService(
            ImportacionAlumnoService alumnos,
            ImportacionFamiliaService familias,
            AuditoriaService auditoria,
            PlatformTransactionManager transactionManager) {
        this.alumnos = alumnos;
        this.familias = familias;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    public PersonaDtos.FamilyImportResult procesar(
            MultipartFile estudiantes,
            MultipartFile apoderados,
            MultipartFile parentescos,
            boolean confirmar) {
        if (vacio(estudiantes) && vacio(apoderados) && vacio(parentescos)) {
            throw new BusinessRuleException(
                    "ARCHIVO_VACIO", "Selecciona al menos un archivo Excel para importar");
        }
        PersonaDtos.FamilyImportResult resultado = transaccion.execute(status -> {
            Map<String, PersonaDtos.FamilyImportFile> archivos = new LinkedHashMap<>();
            leer(archivos, "estudiantes", estudiantes, alumnos::importStudents);
            leer(archivos, "apoderados", apoderados, familias::importGuardians);
            leer(archivos, "parentescos", parentescos, familias::importRelationships);
            boolean valida = archivos.values().stream().allMatch(CargaMasivaFamiliasService::limpio);
            boolean guardar = confirmar && valida;
            // Rollback local: se deshace sin error aunque alguna fila haya marcado la transaccion.
            if (!guardar) status.setRollbackOnly();
            return new PersonaDtos.FamilyImportResult(
                    valida, guardar, java.util.Collections.unmodifiableMap(archivos));
        });
        if (confirmar && !resultado.saved()) {
            auditoria.registrarRechazo(
                    "IMPORTAR_FAMILIAS",
                    ModuloAuditoria.PERSONAS,
                    "Alumno",
                    null,
                    ResultadoAuditoria.RECHAZADO,
                    "La carga tiene errores; no se guardo ninguna fila");
        }
        return resultado;
    }

    private void leer(
            Map<String, PersonaDtos.FamilyImportFile> archivos,
            String clave,
            MultipartFile archivo,
            Function<MultipartFile, PersonaDtos.ImportResult> importar) {
        if (vacio(archivo)) return;
        try {
            archivos.put(clave, new PersonaDtos.FamilyImportFile(null, importar.apply(archivo)));
        } catch (BusinessRuleException exception) {
            archivos.put(clave, new PersonaDtos.FamilyImportFile(exception.getMessage(), null));
        }
    }

    private static boolean limpio(PersonaDtos.FamilyImportFile archivo) {
        return archivo.error() == null && archivo.result().issues().isEmpty();
    }

    private static boolean vacio(MultipartFile archivo) {
        return archivo == null || archivo.isEmpty();
    }
}
