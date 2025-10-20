package cn.pstoolkit.poi;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public final class ExcelWriter {
    private ExcelWriter() {}

    // -------------------- Basic single-sheet APIs (backward compatible) --------------------

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

    // -------------------- Extended/advanced APIs --------------------

    public static void writeXlsx(OutputStream out, List<SheetSpec> sheets) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            for (SheetSpec spec : sheets) {
                writeSheetAdvanced(wb, spec.getSheetName(), spec.getRows(), spec.getHeaderMapping(),
                        spec.getDatePattern(), spec.getDateTimePattern(), spec.isAutoSize(), spec.isFreezeHeader());
            }
            wb.write(out);
        }
    }

    public static void writeXlsxToFile(Path file, List<SheetSpec> sheets) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            writeXlsx(out, sheets);
        }
    }

    public static void streamingWriteXlsx(OutputStream out, List<SheetSpec> sheets, int rowAccessWindowSize) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(rowAccessWindowSize <= 0 ? 100 : rowAccessWindowSize);
        try {
            for (SheetSpec spec : sheets) {
                writeSheetAdvanced(wb, spec.getSheetName(), spec.getRows(), spec.getHeaderMapping(),
                        spec.getDatePattern(), spec.getDateTimePattern(), spec.isAutoSize(), spec.isFreezeHeader());
            }
            wb.write(out);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    public static void streamingWriteXlsxToFile(Path file, List<SheetSpec> sheets, int rowAccessWindowSize) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            streamingWriteXlsx(out, sheets, rowAccessWindowSize);
        }
    }

    public static <T> void writeBeansXlsx(OutputStream out, String sheetName, List<T> beans,
                                          LinkedHashMap<String, String> headerMapping) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>(beans == null ? 0 : beans.size());
        if (beans != null) {
            for (T bean : beans) {
                rows.add(beanToRow(bean, headerMapping.keySet()));
            }
        }
        writeXlsx(out, sheetName, rows, headerMapping);
    }

    public static <T> void writeBeansXlsxToFile(Path file, String sheetName, List<T> beans,
                                                LinkedHashMap<String, String> headerMapping) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            writeBeansXlsx(out, sheetName, beans, headerMapping);
        }
    }

    public static <T> void streamingWriteBeansXlsx(OutputStream out, String sheetName, Iterable<T> beans,
                                                   LinkedHashMap<String, String> headerMapping, int rowAccessWindowSize) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(rowAccessWindowSize <= 0 ? 100 : rowAccessWindowSize);
        try {
            Iterable<Map<String, Object>> it = () -> new Iterator<Map<String, Object>>() {
                private final Iterator<T> delegate = beans.iterator();
                @Override public boolean hasNext() { return delegate.hasNext(); }
                @Override public Map<String, Object> next() { return beanToRow(delegate.next(), headerMapping.keySet()); }
            };
            writeSheet(wb, sheetName, it, headerMapping);
            wb.write(out);
        } finally {
            wb.dispose();
            wb.close();
        }
    }

    public static <T> void streamingWriteBeansXlsxToFile(Path file, String sheetName, Iterable<T> beans,
                                                         LinkedHashMap<String, String> headerMapping, int rowAccessWindowSize) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            streamingWriteBeansXlsx(out, sheetName, beans, headerMapping, rowAccessWindowSize);
        }
    }

    public static void writeXlsx(OutputStream out, String sheetName, List<Map<String, Object>> rows,
                                 LinkedHashMap<String, String> headerMapping,
                                 String datePattern, String dateTimePattern,
                                 boolean autoSize, boolean freezeHeader) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            writeSheetAdvanced(wb, sheetName, rows, headerMapping, datePattern, dateTimePattern, autoSize, freezeHeader);
            wb.write(out);
        }
    }

    // -------------------- Internal helpers --------------------

    private static void writeSheet(Workbook wb, String sheetName, Iterable<Map<String, Object>> rows,
                                   LinkedHashMap<String, String> headerMapping) {
        writeSheetAdvanced(wb, sheetName, rows, headerMapping,
                "yyyy-mm-dd", "yyyy-mm-dd hh:mm:ss", true, true);
    }

    private static void writeSheetAdvanced(Workbook wb, String sheetName, Iterable<Map<String, Object>> rows,
                                           LinkedHashMap<String, String> headerMapping,
                                           String datePattern, String dateTimePattern,
                                           boolean autoSize, boolean freezeHeader) {
        String name = (sheetName == null || sheetName.isEmpty()) ? "Sheet1" : sheetName;
        Sheet sheet = wb.createSheet(name);

        DataFormat dataFormat = wb.createDataFormat();
        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(dataFormat.getFormat(datePattern == null || datePattern.isEmpty() ? "yyyy-mm-dd" : datePattern));
        CellStyle dateTimeStyle = wb.createCellStyle();
        dateTimeStyle.setDataFormat(dataFormat.getFormat(dateTimePattern == null || dateTimePattern.isEmpty() ? "yyyy-mm-dd hh:mm:ss" : dateTimePattern));

        int rowIdx = 0;
        Row headerRow = sheet.createRow(rowIdx++);
        int colIdx = 0;
        String[] keys = new String[headerMapping.size()];
        for (Map.Entry<String, String> e : headerMapping.entrySet()) {
            keys[colIdx] = e.getKey();
            Cell cell = headerRow.createCell(colIdx++);
            cell.setCellValue(e.getValue());
        }

        if (rows != null) {
            for (Map<String, Object> rowMap : rows) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < keys.length; i++) {
                    Object v = rowMap.get(keys[i]);
                    createCell(r, i, v, dateStyle, dateTimeStyle);
                }
            }
        }

        if (freezeHeader) {
            sheet.createFreezePane(0, 1);
        }

        if (autoSize) {
            for (int i = 0; i < keys.length; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(width, 3000), 12000));
            }
            if (sheet instanceof SXSSFSheet) {
                ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            }
        }
    }

    private static void createCell(Row row, int col, Object v, CellStyle dateStyle, CellStyle dateTimeStyle) {
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
        } else if (v instanceof LocalTime) {
            cell.setCellValue(v.toString());
        } else {
            cell.setCellValue(v.toString());
        }
    }

    private static Map<String, Object> beanToRow(Object bean, Collection<String> keys) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (bean == null) return row;
        for (String k : keys) {
            row.put(k, readProperty(bean, k));
        }
        return row;
    }

    private static Object readProperty(Object bean, String name) {
        if (bean instanceof Map) {
            return ((Map<?, ?>) bean).get(name);
        }
        Class<?> c = bean.getClass();
        String capitalized = name == null || name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String[] getters = new String[]{"get" + capitalized, "is" + capitalized};
        for (String g : getters) {
            try {
                Method m = c.getMethod(g);
                return m.invoke(bean);
            } catch (Exception ignored) {
            }
        }
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(bean);
        } catch (Exception ignored) {
        }
        return null;
    }

    // -------------------- SheetSpec --------------------

    public static final class SheetSpec {
        private final String sheetName;
        private final Iterable<Map<String, Object>> rows;
        private final LinkedHashMap<String, String> headerMapping;
        private final String datePattern;
        private final String dateTimePattern;
        private final boolean autoSize;
        private final boolean freezeHeader;

        private SheetSpec(Builder b) {
            this.sheetName = b.sheetName;
            this.rows = b.rows;
            this.headerMapping = b.headerMapping;
            this.datePattern = b.datePattern;
            this.dateTimePattern = b.dateTimePattern;
            this.autoSize = b.autoSize;
            this.freezeHeader = b.freezeHeader;
        }

        public String getSheetName() { return sheetName; }
        public Iterable<Map<String, Object>> getRows() { return rows; }
        public LinkedHashMap<String, String> getHeaderMapping() { return headerMapping; }
        public String getDatePattern() { return datePattern; }
        public String getDateTimePattern() { return dateTimePattern; }
        public boolean isAutoSize() { return autoSize; }
        public boolean isFreezeHeader() { return freezeHeader; }

        public static Builder builder(String sheetName, LinkedHashMap<String, String> headerMapping, Iterable<Map<String, Object>> rows) {
            return new Builder(sheetName, headerMapping, rows);
        }

        public static final class Builder {
            private final String sheetName;
            private final LinkedHashMap<String, String> headerMapping;
            private final Iterable<Map<String, Object>> rows;
            private String datePattern = "yyyy-mm-dd";
            private String dateTimePattern = "yyyy-mm-dd hh:mm:ss";
            private boolean autoSize = true;
            private boolean freezeHeader = true;

            private Builder(String sheetName, LinkedHashMap<String, String> headerMapping, Iterable<Map<String, Object>> rows) {
                this.sheetName = sheetName;
                this.headerMapping = headerMapping;
                this.rows = rows;
            }

            public Builder datePattern(String datePattern) { this.datePattern = datePattern; return this; }
            public Builder dateTimePattern(String dateTimePattern) { this.dateTimePattern = dateTimePattern; return this; }
            public Builder autoSize(boolean autoSize) { this.autoSize = autoSize; return this; }
            public Builder freezeHeader(boolean freezeHeader) { this.freezeHeader = freezeHeader; return this; }
            public SheetSpec build() { return new SheetSpec(this); }
        }
    }
}
