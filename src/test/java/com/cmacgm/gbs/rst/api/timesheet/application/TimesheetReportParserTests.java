package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TimesheetReportParserTests {

    private static final String HEADERS =
            "month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,"
                    + "supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,"
                    + "sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,"
                    + "domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,"
                    + "center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,"
                    + "management_or_production,cost_type";

    private static final String BODY =
            ",EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,"
                    + "SUP-1,S00000002,Supervisor One,POS-SUP-1,"
                    + "SRM-1,S00000003,Manager One,POS-SRM-1,"
                    + "DH-1,S00000004,Head One,POS-DH-1,"
                    + "Kuala Lumpur,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,1,"
                    + "production,productive";

    private final TimesheetReportParser parser = new TimesheetReportParser();

    @Test
    void readsCompactYearMonthInsteadOfExcelSerial() {
        String csv = HEADERS + "\n202607" + BODY + "\n";

        TimesheetReportParser.ReportRow row = parser.parse(
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "monthly.csv")
                .getFirst();

        assertThat(row.month()).isEqualTo(YearMonth.of(2026, 7).atDay(1));
        assertThat(TimesheetRowValidator.rowDate(row)).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void readsNumericYearMonthCellFromExcel() throws Exception {
        byte[] xlsx;
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Original Raw Data");
            Row header = sheet.createRow(0);
            String[] names = HEADERS.split(",");
            for (int i = 0; i < names.length; i++) {
                header.createCell(i).setCellValue(names[i]);
            }
            Row data = sheet.createRow(1);
            String[] values = ("202607" + BODY).split(",", -1);
            for (int i = 0; i < values.length; i++) {
                if (i == 0) {
                    data.createCell(i).setCellValue(202607);
                } else if ("hc".equals(names[i])) {
                    data.createCell(i).setCellValue(1);
                } else {
                    data.createCell(i).setCellValue(values[i]);
                }
            }
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        TimesheetReportParser.ReportRow row = parser.parse(
                        new ByteArrayInputStream(xlsx), "Monthly Report of 202607(GBS CHINA).xlsx")
                .getFirst();

        assertThat(row.month()).isEqualTo(YearMonth.of(2026, 7).atDay(1));
        assertThat(TimesheetRowValidator.rowDate(row)).isEqualTo(LocalDate.of(2026, 7, 31));
    }
}
