package cn.pstoolkit.poi;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExcelWriter {
    private ExcelWriter() {}

    public static void writeXlsxToFile(Path file, String sheetName, List<Map<String, Object>> rows,
                                       LinkedHashMap<String, String> headerMapping) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            writeXlsx(out, sheetName, rows, headerMapping);
        }
    }

    public static void writeXlsx(OutputStream out, String sheetName, List<Map<String, Object>> rows,
                                 LinkedHashMap<String, String> headerMapping) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            writeSheet(wb, sheetName, rows, headerMapping);
            wb.write(out);
        }
    }

    public static void streamingWriteXlsxToFile(Path file, String sheetName, Iterable<Map<String, Object>> rows,
                                                LinkedHashMap<String, String> headerMapping, int rowAccessWindowSize) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            streamingWriteXlsx(out, sheetName, rows, headerMapping, rowAccessWindowSize);
        }
    }

    public static void streamingWriteXlsx(OutputStream out, String sheetName, Iterable<Map<String, Object>> rows,
                                          LinkedHashMap<String, String> headerMapping, int rowAccessWindowSize) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(rowAccessWindowSize <= 0 ? 100 : rowAccessWindowSize);
        try {
            writeSheet(wb, sheetName, rows, headerMapping);
            wb.write(out);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    private static void writeSheet(Workbook wb, String sheetName, Iterable<Map<String, Object>> rows,
                                   LinkedHashMap<String, String> headerMapping) {
        String name = (sheetName == null || sheetName.isEmpty()) ? "Sheet1" : sheetName;
        Sheet sheet = wb.createSheet(name);

        CreationHelper helper = wb.getCreationHelper();
        DataFormat dataFormat = wb.createDataFormat();
        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
        CellStyle dateTimeStyle = wb.createCellStyle();
        dateTimeStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm:ss"));

        int rowIdx = 0;
        Row headerRow = sheet.createRow(rowIdx++);
        int colIdx = 0;
        String[] keys = new String[headerMapping.size()];
        for (Map.Entry<String, String> e : headerMapping.entrySet()) {
            keys[colIdx] = e.getKey();
            Cell cell = headerRow.createCell(colIdx++);
            cell.setCellValue(e.getValue());
        }

        for (Map<String, Object> rowMap : rows) {
            Row r = sheet.createRow(rowIdx++);
            for (int i = 0; i < keys.length; i++) {
                Object v = rowMap.get(keys[i]);
                createCell(r, i, v, helper, dateStyle, dateTimeStyle);
            }
        }

        for (int i = 0; i < keys.length; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(width, 3000), 12000));
        }

        if (sheet instanceof SXSSFSheet) {
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
        }
    }

    private static void createCell(Row row, int col, Object v, CreationHelper helper, CellStyle dateStyle, CellStyle dateTimeStyle) {
        Cell cell = row.createCell(col);
        if (v == null) {
            cell.setBlank();
            return;
        }
        if (v instanceof Number) {
            cell.setCellValue(((Number) v).doubleValue());
        } else if (v instanceof Boolean) {
            cell.setCellValue((Boolean) v);
        } else if (v instanceof Date) {
            cell.setCellValue((Date) v);
            cell.setCellStyle(dateTimeStyle);
        } else if (v instanceof LocalDateTime) {
            Date d = Date.from(((LocalDateTime) v).atZone(ZoneId.systemDefault()).toInstant());
            cell.setCellValue(d);
            cell.setCellStyle(dateTimeStyle);
        } else if (v instanceof LocalDate) {
            Date d = Date.from(((LocalDate) v).atStartOfDay(ZoneId.systemDefault()).toInstant());
            cell.setCellValue(d);
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue(v.toString());
        }
    }
}
