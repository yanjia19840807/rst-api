package com.cmacgm.gbs.rst.api.common.excel;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelSheetsTests {

    @Test
    void writeSheet_appendsMultipleWorksheets() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelSheets.writeSheet(workbook, "Summary", List.of("Field", "Value"), List.of(List.of("A", "1")));
            ExcelSheets.writeSheet(workbook, "Shared KPI", List.of("Carrier"), List.of());
            byte[] bytes = ExcelSheets.bytes(workbook);
            try (Workbook read = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals(2, read.getNumberOfSheets());
                assertEquals("Summary", read.getSheetName(0));
                assertEquals("Shared KPI", read.getSheetName(1));
                assertEquals("A", read.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            }
        }
    }
}
