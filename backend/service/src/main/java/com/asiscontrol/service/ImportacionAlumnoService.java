package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import com.asiscontrol.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
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
    private static final List<String> TEMPLATE_HEADERS = List.of(
            "codigoAlumno", "nombres", "apellidoPaterno", "apellidoMaterno",
            "tipoDocumento", "numeroDocumento", "sexo", "fechaNacimiento",
            "telefono", "correo", "direccion");
    private static final List<String> TEMPLATE_EXAMPLE = List.of(
            "EST-0001", "Maria", "Quispe", "Flores", "DNI", "70000001",
            "FEMENINO", "2012-05-15", "999999999", "familia@ejemplo.com", "Lima");
    private static final int TEMPLATE_COLUMN_WIDTH = 24 * 256;
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
    private final jakarta.validation.Validator validator;
    private final AuditoriaService auditoria;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public ImportacionAlumnoService(
            PersonaService personaService,
            jakarta.validation.Validator validator,
            AuditoriaService auditoria) {
        this.personaService = personaService;
        this.validator = validator;
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
                    var request = toRequest(row, columns);
                    var errors = validator.validate(request);
                    if (!errors.isEmpty()) {
                        throw new BusinessRuleException("FILA_INVALIDA", errors.stream()
                                .map(jakarta.validation.ConstraintViolation::getMessage)
                                .sorted().collect(java.util.stream.Collectors.joining("; ")));
                    }
                    personaService.createAlumno(request);
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
        } catch (org.apache.poi.EncryptedDocumentException
                | org.apache.poi.ooxml.POIXMLException | IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "ARCHIVO_NO_LEGIBLE", "El archivo no es un Excel válido sin contraseña");
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
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Alumnos");
            createTemplateHeader(workbook, sheet);
            createTemplateExample(workbook, sheet);
            sheet.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar la plantilla", exception);
        }
    }

    private void createTemplateHeader(Workbook workbook, Sheet sheet) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int columnIndex = 0; columnIndex < TEMPLATE_HEADERS.size(); columnIndex++) {
            Cell headerCell = headerRow.createCell(columnIndex);
            headerCell.setCellValue(TEMPLATE_HEADERS.get(columnIndex));
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(columnIndex, TEMPLATE_COLUMN_WIDTH);
        }
    }

    private void createTemplateExample(Workbook workbook, Sheet sheet) {
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
        Row exampleRow = sheet.createRow(1);
        for (int columnIndex = 0; columnIndex < TEMPLATE_EXAMPLE.size(); columnIndex++) {
            sheet.setDefaultColumnStyle(columnIndex, textStyle);
            Cell exampleCell = exampleRow.createCell(columnIndex);
            exampleCell.setCellValue(TEMPLATE_EXAMPLE.get(columnIndex));
            exampleCell.setCellStyle(textStyle);
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
                DateTimeFormatter.ofPattern("dd/MM/uuuu")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT)
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
            if (!value.matches("[0-9]+(?:\\.0+)?")) throw new NumberFormatException();
            long id = Long.parseLong(value.split("\\.", 2)[0]);
            if (id < 1) throw new NumberFormatException();
            return id;
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
        if (exception instanceof BusinessRuleException
                || exception instanceof com.asiscontrol.exception.ConflictException
                || exception instanceof com.asiscontrol.exception.ResourceNotFoundException) {
            return exception.getMessage();
        }
        return "Fila no valida; revise los datos o consulte al administrador";
    }
}
