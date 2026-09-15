package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeRequest;

class VolumeExcelServiceTests {

    private final VolumeExcelService excel = new VolumeExcelService();

    @Test
    void slotRoundTripKeepsCivilWallClock() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 15, 8, 0);
        List<SlotVolumeRequest> rows = List.of(
                new SlotVolumeRequest(start, start.plusMinutes(30), BigDecimal.TEN));
        byte[] bytes = excel.exportSlot(rows);
        List<SlotVolumeRequest> parsed = excel.parseSlot(new ByteArrayInputStream(bytes));
        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().slotStartAt()).isEqualTo(start);
        assertThat(parsed.getFirst().slotEndAt()).isEqualTo(start.plusMinutes(30));
    }

    @Test
    void slotTextCellKeepsCivilWallClock() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("slot_start");
            header.createCell(1).setCellValue("actual_volume");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("2026-03-15 08:00");
            row.createCell(1).setCellValue(10);
            workbook.write(out);
            List<SlotVolumeRequest> parsed = excel.parseSlot(new ByteArrayInputStream(out.toByteArray()));
            assertThat(parsed).hasSize(1);
            assertThat(parsed.getFirst().slotStartAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 8, 0));
            assertThat(parsed.getFirst().slotEndAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 8, 30));
        }
    }
}
