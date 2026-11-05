package com.asiscontrol.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Plantillas de importacion: cabecera destacada y fija, una fila de ejemplo y columnas en
 * formato texto para que Excel no altere documentos ni codigos (por ejemplo, ceros a la izquierda).
 */
final class PlantillaExcel {

    private static final int COLUMN_WIDTH = 24 * 256;
    /** Filas con lista desplegable: mas que suficientes para una carga escolar. */
    private static final int LAST_LIST_ROW = 2000;

    private PlantillaExcel() {}

    /**
     * @param lists valores permitidos por nombre de columna; Excel los muestra como lista
     *     desplegable y rechaza otros valores.
     */
    static byte[] build(
            String sheetName,
            List<String> headers,
            List<String> example,
            Map<String, List<String>> lists) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeader(workbook, sheet, headers);
            writeExample(workbook, sheet, example);
            lists.forEach((column, values) -> addList(sheet, headers.indexOf(column), values));
            sheet.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar la plantilla", exception);
        }
    }

    private static void writeHeader(Workbook workbook, Sheet sheet, List<String> headers) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            Cell headerCell = headerRow.createCell(columnIndex);
            headerCell.setCellValue(headers.get(columnIndex));
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(columnIndex, COLUMN_WIDTH);
        }
    }

    private static void writeExample(Workbook workbook, Sheet sheet, List<String> example) {
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
        Row exampleRow = sheet.createRow(1);
        for (int columnIndex = 0; columnIndex < example.size(); columnIndex++) {
            sheet.setDefaultColumnStyle(columnIndex, textStyle);
            Cell exampleCell = exampleRow.createCell(columnIndex);
            exampleCell.setCellValue(example.get(columnIndex));
            exampleCell.setCellStyle(textStyle);
        }
    }

    private static void addList(Sheet sheet, int columnIndex, List<String> values) {
        if (columnIndex < 0) {
            throw new IllegalArgumentException("La lista no corresponde a ninguna columna");
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation validation =
                helper.createValidation(
                        helper.createExplicitListConstraint(values.toArray(String[]::new)),
                        new CellRangeAddressList(1, LAST_LIST_ROW, columnIndex, columnIndex));
        validation.setShowErrorBox(true);
        validation.createErrorBox("Valor no permitido", "Elige un valor de la lista");
        sheet.addValidationData(validation);
    }
}
