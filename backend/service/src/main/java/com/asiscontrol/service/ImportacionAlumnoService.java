package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImportacionAlumnoService {

    private static final long MAX_IMPORT_SIZE = 5L * 1024L * 1024L;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "codigoAlumno",
            "nombres",
            "apellidoPaterno",
            "apellidoMaterno",
            "tipoDocumento",
            "numeroDocumento",
            "sexo",
            "fechaNacimiento",
            "correo"
    );

    private final PersonaService personaService;
    private final AuditoriaService auditoria;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public ImportacionAlumnoService(PersonaService personaService, AuditoriaService auditoria) {
        this.personaService = personaService;
        this.auditoria = auditoria;
    }

    public PersonaDtos.ImportResult importStudents(MultipartFile file) {
        validateFile(file);
        List<PersonaDtos.ImportIssue> issues = new ArrayList<>();
        int imported = 0;
        int total = 0;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessRuleException("ARCHIVO_VACIO", "El archivo no contiene filas");
            }
            Map<String, Integer> columns = readHeaders(sheet.getRow(sheet.getFirstRowNum()));
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmpty(row)) {
                    continue;
                }
                total++;
                try {
                    personaService.createAlumno(toRequest(row, columns));
                    imported++;
                } catch (RuntimeException exception) {
                    issues.add(new PersonaDtos.ImportIssue(
                            rowIndex + 1,
                            "fila",
                            safeMessage(exception)
                    ));
                }
            }
        } catch (IOException exception) {
            throw new BusinessRuleException(
                    "ARCHIVO_NO_LEGIBLE",
                    "No fue posible leer el archivo Excel"
            );
        }
        auditarImportacion(file, total, imported, issues);
        return new PersonaDtos.ImportResult(total, imported, total - imported, List.copyOf(issues));
    }

    /**
     * Resume la importacion en un solo registro. Cada alumno creado ya deja su CREAR_ALUMNO, con
     * el mismo correlacionId que este resumen, asi que se pueden relacionar.
     */
    private void auditarImportacion(
            MultipartFile file, int total, int imported, List<PersonaDtos.ImportIssue> issues) {
        String filasConError = issues.stream()
                .map(issue -> String.valueOf(issue.row()))
                .collect(java.util.stream.Collectors.joining(","));
        auditoria.registrar(
                "IMPORTAR_ALUMNOS",
                ModuloAuditoria.PERSONAS,
                "Alumno",
                null,
                imported > 0 || total == 0 ? ResultadoAuditoria.EXITOSO : ResultadoAuditoria.RECHAZADO,
                issues.isEmpty() ? null : "Filas con errores: " + filasConError,
                null,
                "archivo=" + file.getOriginalFilename()
                        + ",filas=" + total
                        + ",importados=" + imported
                        + ",fallidos=" + (total - imported));
    }

    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Alumnos");
            List<String> headers = List.of(
                    "codigoAlumno", "nombres", "apellidoPaterno", "apellidoMaterno",
                    "tipoDocumento", "numeroDocumento", "nacionalidadId", "sexo",
                    "fechaNacimiento", "telefono", "correo", "direccion"
            );
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
                sheet.setColumnWidth(index, 22 * 256);
            }
            Row sample = sheet.createRow(1);
            String[] values = {
                    "EST-0001", "Maria", "Quispe", "Flores", "DNI", "70000001", "",
                    "FEMENINO", "2012-05-15", "999999999", "familia@ejemplo.com", "Lima"
            };
            for (int index = 0; index < values.length; index++) {
                sample.createCell(index).setCellValue(values[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar la plantilla", exception);
        }
    }

    private PersonaDtos.AlumnoCreateRequest toRequest(Row row, Map<String, Integer> columns) {
        String code = value(row, columns, "codigoAlumno");
        String names = value(row, columns, "nombres");
        String paternalSurname = value(row, columns, "apellidoPaterno");
        String maternalSurname = value(row, columns, "apellidoMaterno");
        TipoDocumento documentType = parseEnum(
                TipoDocumento.class,
                value(row, columns, "tipoDocumento"),
                "tipoDocumento"
        );
        String documentNumber = value(row, columns, "numeroDocumento");
        Long nationalityId = parseOptionalLong(optionalValue(row, columns, "nacionalidadId"));
        Sexo sex = parseEnum(Sexo.class, value(row, columns, "sexo"), "sexo");
        LocalDate birthDate = readDate(row.getCell(columns.get("fechaNacimiento")));
        String phone = optionalValue(row, columns, "telefono");
        String email = value(row, columns, "correo");
        String address = optionalValue(row, columns, "direccion");
        PersonaDtos.PersonaRequest person = new PersonaDtos.PersonaRequest(
                names,
                paternalSurname,
                maternalSurname,
                documentType,
                documentNumber,
                nationalityId,
                sex,
                birthDate,
                phone,
                email,
                address
        );
        return new PersonaDtos.AlumnoCreateRequest(person, code);
    }

    private Map<String, Integer> readHeaders(Row headerRow) {
        if (headerRow == null) {
            throw new BusinessRuleException("CABECERA_FALTANTE", "El archivo no tiene cabecera");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            if (!header.isBlank()) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> !columns.containsKey(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "CABECERAS_INCOMPLETAS",
                    "Faltan las columnas: " + String.join(", ", missing)
            );
        }
        return columns;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("ARCHIVO_VACIO", "Debe seleccionar un archivo Excel");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw new BusinessRuleException(
                    "ARCHIVO_DEMASIADO_GRANDE",
                    "El archivo no puede superar 5 MB"
            );
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessRuleException(
                    "FORMATO_NO_PERMITIDO",
                    "La importacion solo acepta archivos .xlsx"
            );
        }
    }

    private String value(Row row, Map<String, Integer> columns, String header) {
        String value = optionalValue(row, columns, header);
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(
                    "CAMPO_OBLIGATORIO",
                    "La columna " + header + " es obligatoria"
            );
        }
        return value.trim();
    }

    private String optionalValue(Row row, Map<String, Integer> columns, String header) {
        Integer index = columns.get(header);
        if (index == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isBlank() ? null : value;
    }

    private LocalDate readDate(Cell cell) {
        if (cell == null) {
            throw new BusinessRuleException(
                    "FECHA_NACIMIENTO_INVALIDA",
                    "La fecha de nacimiento es obligatoria"
            );
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = formatter.formatCellValue(cell).trim();
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );
        for (DateTimeFormatter format : formats) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // Se intenta el siguiente formato permitido.
            }
        }
        throw new BusinessRuleException(
                "FECHA_NACIMIENTO_INVALIDA",
                "Use AAAA-MM-DD o DD/MM/AAAA para la fecha de nacimiento"
        );
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "VALOR_NO_PERMITIDO",
                    "El valor de " + field + " no esta permitido"
            );
        }
    }

    private Long parseOptionalLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.replace(".0", ""));
        } catch (NumberFormatException exception) {
            throw new BusinessRuleException(
                    "NACIONALIDAD_INVALIDA",
                    "nacionalidadId debe ser numerico"
            );
        }
    }

    private boolean isEmpty(Row row) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "Fila no valida" : exception.getMessage();
    }
}
