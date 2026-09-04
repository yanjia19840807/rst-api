package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SupportItemView;

class SupportExcelServiceTest {

    private final SupportExcelService service = new SupportExcelService();

    @Test
    void parse_rejectsDuplicateBusinessKeysWithRowNumbers() throws Exception {
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Production Support");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Category");
            header.createCell(1).setCellValue("Activity");
            header.createCell(2).setCellValue("Frequency");
            header.createCell(3).setCellValue("Volume");
            header.createCell(4).setCellValue("UOM");
            header.createCell(5).setCellValue("Minutes");
            writeRow(sheet, 1, "Training", "Coaching", "MONTHLY", "2", "Sessions", "30");
            writeRow(sheet, 2, "training", "Coaching", "MONTHLY", "3", "Sessions", "45");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        ApiException ex = assertThrows(
                ApiException.class, () -> service.parse(new ByteArrayInputStream(bytes)));
        assertEquals("invalid-excel", ex.code());
        assertEquals(
                "Rows 2 and 3: Category + Activity + Frequency must be unique.",
                ex.getMessage());
    }

    @Test
    void export_roundTripsCurrentRows() {
        SupportItemView item = new SupportItemView(
                null,
                null,
                null,
                "Training",
                "Coaching",
                "MONTHLY",
                new BigDecimal("2"),
                "Sessions",
                new BigDecimal("30"),
                null,
                null,
                null,
                "note");
        List<SupportExcelService.ParsedRow> parsed =
                service.parse(new ByteArrayInputStream(service.export(List.of(item))));
        assertEquals(1, parsed.size());
        assertEquals("Training", parsed.get(0).categoryName());
        assertEquals("Coaching", parsed.get(0).activity());
        assertEquals("MONTHLY", parsed.get(0).frequencyCode());
        assertEquals(0, parsed.get(0).volume().compareTo(new BigDecimal("2")));
        assertEquals("Sessions", parsed.get(0).unitOfMeasure());
        assertEquals(0, parsed.get(0).workloadPerUnitMinutes().compareTo(new BigDecimal("30")));
        assertEquals("note", parsed.get(0).comments());
    }

    private static void writeRow(
            Sheet sheet,
            int index,
            String category,
            String activity,
            String frequency,
            String volume,
            String uom,
            String minutes) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(category);
        row.createCell(1).setCellValue(activity);
        row.createCell(2).setCellValue(frequency);
        row.createCell(3).setCellValue(volume);
        row.createCell(4).setCellValue(uom);
        row.createCell(5).setCellValue(minutes);
    }
}
