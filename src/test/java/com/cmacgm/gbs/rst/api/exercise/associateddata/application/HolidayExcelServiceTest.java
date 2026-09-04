package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.HolidayRequest;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;

class HolidayExcelServiceTest {

    private final HolidayExcelService service = new HolidayExcelService();

    @Test
    void parse_acceptsPhDatesHeadersAndPaddedTypes() throws Exception {
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("PH Dates");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Date");
            header.createCell(1).setCellValue("Type");
            header.createCell(2).setCellValue("Description");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(LocalDate.of(2026, 1, 1));
            row.createCell(1).setCellValue("  Holiday  ");
            row.createCell(2).setCellValue("New Year");
            Row makeup = sheet.createRow(2);
            makeup.createCell(0).setCellValue("2026-01-04");
            makeup.createCell(1).setCellValue("Normal");
            makeup.createCell(2).setCellValue("Makeup");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        List<HolidayRequest> rows = service.parse(new ByteArrayInputStream(bytes));
        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2026, 1, 1), rows.get(0).holidayDate());
        assertEquals(HolidayDayKind.HOLIDAY, rows.get(0).holidayType());
        assertEquals("New Year", rows.get(0).holidayName());
        assertEquals(LocalDate.of(2026, 1, 4), rows.get(1).holidayDate());
        assertEquals(HolidayDayKind.NORMAL, rows.get(1).holidayType());
    }

    @Test
    void parse_rejectsDuplicateDates() throws Exception {
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Holidays");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("date");
            header.createCell(1).setCellValue("type");
            sheet.createRow(1).createCell(0).setCellValue("2026-01-01");
            sheet.getRow(1).createCell(1).setCellValue("HOLIDAY");
            sheet.createRow(2).createCell(0).setCellValue("2026-01-01");
            sheet.getRow(2).createCell(1).setCellValue("WEEKEND");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        ApiException ex = assertThrows(
                ApiException.class, () -> service.parse(new ByteArrayInputStream(bytes)));
        assertEquals("invalid-excel", ex.code());
    }

    @Test
    void export_roundTripsCurrentRows() {
        List<HolidayRequest> source = List.of(
                new HolidayRequest(LocalDate.of(2026, 2, 17), "Lunar NY", HolidayDayKind.HOLIDAY));
        List<HolidayRequest> parsed = service.parse(new ByteArrayInputStream(service.export(source)));
        assertEquals(1, parsed.size());
        assertEquals(source.get(0), parsed.get(0));
    }
}
