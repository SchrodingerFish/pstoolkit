package cn.pstoolkit.poi;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public final class ExcelReader {
    private ExcelReader() {}

    public static List<Map<String, String>> readFirstSheet(InputStream in, boolean firstRowAsHeader) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) return Collections.emptyList();
            return readSheet(sheet, firstRowAsHeader);
        }
    }

    public static List<Map<String, String>> readSheet(Sheet sheet, boolean firstRowAsHeader) {
        List<Map<String, String>> result = new ArrayList<>();
        if (sheet == null) return result;
        Iterator<Row> it = sheet.iterator();
        List<String> headers = new ArrayList<>();
        if (firstRowAsHeader && it.hasNext()) {
            Row headerRow = it.next();
            int last = headerRow.getLastCellNum();
            for (int i = 0; i < last; i++) {
                Cell c = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                headers.add(c.toString().trim());
            }
        }
        int rowIndex = 0;
        while (it.hasNext()) {
            Row row = it.next();
            if (!firstRowAsHeader && rowIndex == 0) {
                int last = row.getLastCellNum();
                headers.clear();
                for (int i = 0; i < last; i++) {
                    headers.add(CellReference.convertNumToColString(i));
                }
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                Cell c = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                m.put(headers.get(i), getCellAsString(c));
            }
            result.add(m);
            rowIndex++;
        }
        return result;
    }

    private static String getCellAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                long l = (long) d;
                if (Math.abs(d - l) < 1e-9) {
                    return Long.toString(l);
                }
                return Double.toString(d);
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    try {
                        double dv = cell.getNumericCellValue();
                        return Double.toString(dv);
                    } catch (Exception ex) {
                        return cell.toString();
                    }
                }
            case BLANK:
            case _NONE:
            case ERROR:
            default:
                return "";
        }
    }
}
