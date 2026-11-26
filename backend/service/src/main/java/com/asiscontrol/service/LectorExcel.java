package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
import java.util.stream.Collectors;

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
                        "CAMPO_OBLIGATORIO", "La columna " + header + " es obligatoria", header);
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
                                            "El valor de " + header + " no esta permitido",
                                            header));
        }

        boolean siNo(String header) {
            String value = obligatorio(header).toUpperCase(Locale.ROOT);
            return switch (value) {
                case "SI", "SÍ" -> true;
                case "NO" -> false;
                default -> throw new BusinessRuleException(
                        "VALOR_NO_PERMITIDO", "La columna " + header + " debe ser SI o NO", header);
            };
        }

        /** Entero obligatorio; Excel puede guardar "40" como texto o como número 40. */
        int entero(String header) {
            String value = obligatorio(header);
            try {
                return new java.math.BigDecimal(value).intValueExact();
            } catch (NumberFormatException | ArithmeticException exception) {
                throw new BusinessRuleException(
                        "NUMERO_INVALIDO", "La columna " + header + " debe ser un número entero", header);
            }
        }

        LocalDate fecha(String header) {
            Integer index = columns.get(header);
            Cell cell = index == null ? null : row.getCell(index);
            if (cell == null || FORMATTER.formatCellValue(cell).isBlank()) {
                throw new BusinessRuleException(
                        "CAMPO_OBLIGATORIO", "La columna " + header + " es obligatoria", header);
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
                    "FECHA_INVALIDA", "Use AAAA-MM-DD o DD/MM/AAAA en " + header, header);
        }
    }

    /** Resultado de recorrer el archivo: filas leidas, registradas y problemas por fila. */
    record Resumen(int total, int importadas, List<PersonaDtos.ImportIssue> issues) {
        PersonaDtos.ImportResult resultado() {
            return new PersonaDtos.ImportResult(total, importadas, total - importadas, issues);
        }

        PersonaDtos.ImportResult resultado(
                List<PersonaDtos.CredencialAcceso> credenciales,
                List<PersonaDtos.ImportIssue> observaciones) {
            return new PersonaDtos.ImportResult(
                    total, importadas, total - importadas, issues,
                    List.copyOf(credenciales), List.copyOf(observaciones));
        }

        /** Un registro por archivo; cada alta o vinculo ya deja su propio registro de auditoria. */
        void auditar(
                AuditoriaService auditoria, String accion, String entidad, MultipartFile file) {
            auditar(auditoria, accion, entidad, file, List.of());
        }

        /**
         * Igual, con el número de accesos creados y reutilizados. Nunca se registran contraseñas:
         * cada cuenta nueva ya deja su CREAR_USUARIO con el mismo correlacionId.
         */
        void auditar(
                AuditoriaService auditoria,
                String accion,
                String entidad,
                MultipartFile file,
                List<PersonaDtos.CredencialAcceso> credenciales) {
            long creados = credenciales.stream().filter(c -> !c.existing()).count();
            String accesos = credenciales.isEmpty()
                    ? ""
                    : ",accesosCreados=" + creados
                            + ",accesosExistentes=" + (credenciales.size() - creados);
            String filasConError = issues.stream()
                    .map(issue -> String.valueOf(issue.row()))
                    .collect(Collectors.joining(","));
            auditoria.registrar(
                    accion,
                    ModuloAuditoria.PERSONAS,
                    entidad,
                    null,
                    importadas > 0 || total == 0
                            ? ResultadoAuditoria.EXITOSO
                            : ResultadoAuditoria.RECHAZADO,
                    issues.isEmpty() ? null : "Filas con errores: " + filasConError,
                    null,
                    "archivo=" + file.getOriginalFilename()
                            + ",filas=" + total
                            + ",importados=" + importadas
                            + ",fallidos=" + (total - importadas)
                            + accesos);
        }
    }

    /**
     * Aplica las validaciones de la ficha a la fila; los motivos se unen en un solo mensaje. Si
     * todos corresponden al mismo campo, ese campo se informa junto al error.
     */
    static <T> void validar(Validator validator, T request) {
        var errors = validator.validate(request);
        if (!errors.isEmpty()) {
            var campos = errors.stream()
                    .map(error -> error.getPropertyPath().toString())
                    .collect(Collectors.toSet());
            throw new BusinessRuleException("FILA_INVALIDA", errors.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; ")),
                    campos.size() == 1 ? campos.iterator().next() : null);
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
                    issues.add(new PersonaDtos.ImportIssue(
                            index + 1, campo(exception), mensaje(exception)));
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

    /** Columna que causó el error cuando se conoce; si no, el error es de toda la fila. */
    private static String campo(RuntimeException exception) {
        return exception instanceof BusinessRuleException regla && regla.getField() != null
                ? regla.getField()
                : "fila";
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
