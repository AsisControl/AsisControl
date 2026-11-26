package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.persona.PersonaDtos.ImportIssue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OBS-05: base común de la carga masiva de cada módulo.
 *
 * <ul>
 *   <li>Sin confirmar, todo el archivo se procesa con las reglas reales dentro de una transacción
 *       que siempre se deshace: se detectan cupos, cruces y duplicados sin guardar nada.
 *   <li>Al confirmar, cada fila se guarda en la transacción de su propio servicio; una fila
 *       rechazada no impide guardar las demás.
 *   <li>Cada fila tiene una clave natural: si ya está registrada o se repite en el archivo, se
 *       omite. Así, volver a cargar el mismo archivo no duplica registros.
 * </ul>
 */
@Component
public class ImportadorExcel {

    /**
     * Lo que pide una fila ya leída y validada.
     *
     * @param clave clave natural de la fila (sin distinguir mayúsculas)
     * @param campoClave columna que se informa cuando la fila se omite
     * @param existe la clave ya está registrada en el sistema
     * @param registrar alta con el mismo servicio que el registro manual
     */
    record Paso(String clave, String campoClave, boolean existe, Runnable registrar) {}

    /** Lee una fila y decide su paso; si la fila no es válida, lanza el error de esa fila. */
    @FunctionalInterface
    interface Procesador {
        Paso analizar(LectorExcel.Fila fila);
    }

    private final TransactionTemplate transaccion;

    public ImportadorExcel(PlatformTransactionManager transactionManager) {
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    CargaMasivaDto.Resultado importar(
            MultipartFile archivo,
            boolean confirmar,
            List<String> columnasObligatorias,
            Procesador procesador) {
        if (confirmar) {
            return procesar(archivo, columnasObligatorias, procesador, true);
        }
        return transaccion.execute(estado -> {
            estado.setRollbackOnly();
            return procesar(archivo, columnasObligatorias, procesador, false);
        });
    }

    private CargaMasivaDto.Resultado procesar(
            MultipartFile archivo,
            List<String> columnasObligatorias,
            Procesador procesador,
            boolean guardar) {
        Map<String, Integer> clavesRegistradas = new HashMap<>();
        List<ImportIssue> omitidas = new ArrayList<>();
        var resumen = LectorExcel.recorrer(archivo, columnasObligatorias, fila -> {
            Paso paso = procesador.analizar(fila);
            String clave = paso.clave().trim().toUpperCase(Locale.ROOT);
            Integer filaPrevia = clavesRegistradas.get(clave);
            if (filaPrevia != null) {
                omitidas.add(new ImportIssue(fila.numero(), paso.campoClave(),
                        "Repite la fila " + filaPrevia + " del archivo; no se duplica"));
                return;
            }
            if (paso.existe()) {
                omitidas.add(new ImportIssue(fila.numero(), paso.campoClave(),
                        "Ya está registrado; no se duplica"));
                return;
            }
            paso.registrar().run();
            clavesRegistradas.put(clave, fila.numero());
        });
        int validas = resumen.importadas() - omitidas.size();
        return new CargaMasivaDto.Resultado(
                resumen.total(), validas, guardar ? validas : 0,
                List.copyOf(omitidas), resumen.issues());
    }
}
