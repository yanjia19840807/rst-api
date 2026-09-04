package com.cmacgm.gbs.rst.api.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Shared XSSF workbook writer for list / template Excel downloads.
 */
public final class ExcelSheets {

    private ExcelSheets() {
    }

    /**
     * Writes a single-sheet workbook.
     *
     * @param sheetName worksheet name
     * @param headers header cells
     * @param rows body rows; each inner list is one row
     * @return xlsx bytes
     */
    public static byte[] write(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            int rowIdx = 1;
            for (List<String> values : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < values.size(); i++) {
                    row.createCell(i).setCellValue(values.get(i) == null ? "" : values.get(i));
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "excel-export-failed",
                    "Unable to export Excel: " + ex.getMessage());
        }
    }
}
