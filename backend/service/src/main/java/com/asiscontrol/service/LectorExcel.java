package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lectura de las plantillas de importacion con las mismas reglas que la de alumnos: solo .xlsx
 * de hasta 5 MB, cabecera en la primera fila y filas vacias omitidas. Cada fila se procesa por
 * separado: una fila con error no impide registrar las demas.
 */
final class LectorExcel {

    private static final long MAX_IMPORT_SIZE = 5L * 1024L * 1024L;
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    private LectorExcel() {}

    /** Fila del Excel con acceso a sus columnas por nombre de cabecera. */
    record Fila(Row row, Map<String, Integer> columns) {
        /** Numero de fila tal como lo muestra Excel (la cabecera es la 1). */
        int numero() {
            return row.getRowNum() + 1;
        }

        String obligatorio(String header) {
            String value = opcional(header);
            if (value == null) {
                throw new BusinessRuleException(
                        "CAMPO_OBLIGATORIO", "La columna " + header + " es obligatoria");
            }
            return value;
        }

        String opcional(String header) {
            Integer index = columns.get(header);
            if (index == null) {
                return null;
            }
            String value = FORMATTER.formatCellValue(row.getCell(index)).trim();
            return value.isBlank() ? null : value;
        }

        <T extends Enum<T>> T enumerado(Class<T> type, String header, List<T> allowed) {
            String value = obligatorio(header).toUpperCase(Locale.ROOT);
            return allowed.stream()
                    .filter(option -> option.name().equals(value))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new BusinessRuleException(
                                            "VALOR_NO_PERMITIDO",
                                            "El valor de " + header + " no esta permitido"));
        }

        boolean siNo(String header) {
            String value = obligatorio(header).toUpperCase(Locale.ROOT);
            return switch (value) {
                case "SI", "SÍ" -> true;
                case "NO" -> false;
                default -> throw new BusinessRuleException(
                        "VALOR_NO_PERMITIDO", "La columna " + header + " debe ser SI o NO");
            };
        }

        LocalDate fecha(String header) {
            Integer index = columns.get(header);
            Cell cell = index == null ? null : row.getCell(index);
            if (cell == null || FORMATTER.formatCellValue(cell).isBlank()) {
                throw new BusinessRuleException(
                        "CAMPO_OBLIGATORIO", "La columna " + header + " es obligatoria");
            }
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            String text = FORMATTER.formatCellValue(cell).trim();
            for (DateTimeFormatter format : List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("dd/MM/uuuu")
                            .withResolverStyle(ResolverStyle.STRICT))) {
                try {
                    return LocalDate.parse(text, format);
                } catch (DateTimeParseException ignored) {
                    // Se intenta el siguiente formato permitido.
                }
            }
            throw new BusinessRuleException(
                    "FECHA_INVALIDA", "Use AAAA-MM-DD o DD/MM/AAAA en " + header);
        }
    }

    /** Resultado de recorrer el archivo: filas leidas, registradas y problemas por fila. */
    record Resumen(int total, int importadas, List<PersonaDtos.ImportIssue> issues) {
        PersonaDtos.ImportResult resultado() {
            return new PersonaDtos.ImportResult(total, importadas, total - importadas, issues);
        }
    }

    /**
     * Lee el archivo y llama a {@code procesar} con cada fila no vacia. Si la fila lanza una
     * excepcion, se registra como problema de esa fila y se continua con la siguiente.
     */
    static Resumen recorrer(
            MultipartFile file, List<String> requiredHeaders, Consumer<Fila> procesar) {
        validarArchivo(file);
        List<PersonaDtos.ImportIssue> issues = new ArrayList<>();
        int total = 0;
        int importadas = 0;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessRuleException("ARCHIVO_VACIO", "El archivo no contiene filas");
            }
            Map<String, Integer> columns =
                    cabeceras(sheet.getRow(sheet.getFirstRowNum()), requiredHeaders);
            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || vacia(row)) {
                    continue;
                }
                total++;
                try {
                    procesar.accept(new Fila(row, columns));
                    importadas++;
                } catch (RuntimeException exception) {
                    issues.add(new PersonaDtos.ImportIssue(index + 1, "fila", mensaje(exception)));
                }
            }
        } catch (IOException exception) {
            throw new BusinessRuleException(
                    "ARCHIVO_NO_LEGIBLE", "No fue posible leer el archivo Excel");
        } catch (org.apache.poi.EncryptedDocumentException
                | org.apache.poi.ooxml.POIXMLException
                | IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "ARCHIVO_NO_LEGIBLE", "El archivo no es un Excel válido sin contraseña");
        }
        return new Resumen(total, importadas, List.copyOf(issues));
    }

    private static void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("ARCHIVO_VACIO", "Debe seleccionar un archivo Excel");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw new BusinessRuleException(
                    "ARCHIVO_DEMASIADO_GRANDE", "El archivo no puede superar 5 MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessRuleException(
                    "FORMATO_NO_PERMITIDO", "La importacion solo acepta archivos .xlsx");
        }
    }

    private static Map<String, Integer> cabeceras(Row headerRow, List<String> required) {
        if (headerRow == null) {
            throw new BusinessRuleException("CABECERA_FALTANTE", "El archivo no tiene cabecera");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = FORMATTER.formatCellValue(cell).trim();
            if (!header.isBlank()) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        List<String> missing = required.stream().filter(h -> !columns.containsKey(h)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "CABECERAS_INCOMPLETAS", "Faltan las columnas: " + String.join(", ", missing));
        }
        return columns;
    }

    private static boolean vacia(Row row) {
        for (Cell cell : row) {
            if (!FORMATTER.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Solo los errores de negocio llegan al usuario; los tecnicos no exponen detalles. */
    private static String mensaje(RuntimeException exception) {
        if (exception instanceof BusinessRuleException
                || exception instanceof ConflictException
                || exception instanceof ResourceNotFoundException) {
            return exception.getMessage();
        }
        return "Fila no valida; revise los datos o consulte al administrador";
    }
}
