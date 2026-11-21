package com.cmacgm.gbs.rst.api.sourcedata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

class MyHrWorkbookParserTests {

    private final MyHrWorkbookParser parser = new MyHrWorkbookParser();

    @Test
    void positionComesFromCodeNotTheTrailingCodeColumn() throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var people = workbook.createSheet("MyHR");
            var header = people.createRow(0);
            String[] columns = {
                    "Person ID", "Username", "Employee Status", "Last Name", "First Name",
                    "Business  Email Information Email Address", "Direct Reports", "Company Country",
                    "Job Profile - Career Level", "Code", "Event Date", "CODE"
            };
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            var row = people.createRow(1);
            row.createCell(0).setCellValue("100");
            row.createCell(1).setCellValue("S100");
            row.createCell(2).setCellValue("Active");
            row.createCell(3).setCellValue("Lee");
            row.createCell(4).setCellValue("Ann");
            row.createCell(9).setCellValue("POS-REAL");
            row.createCell(11).setCellValue("POS-OTHER");
            var positions = workbook.createSheet("Position Report");
            var positionHeader = positions.createRow(0);
            positionHeader.createCell(0).setCellValue("Position Code");
            positionHeader.createCell(1).setCellValue("effectiveStatus");
            positionHeader.createCell(2).setCellValue("Parent Position code");
            var position = positions.createRow(1);
            position.createCell(0).setCellValue("POS-REAL");
            position.createCell(1).setCellValue("A");
            workbook.write(out);
            file = out.toByteArray();
        }

        MyHrWorkbookParser.Parsed parsed = parser.parse(new ByteArrayInputStream(file));

        assertThat(parsed.people()).hasSize(1);
        assertThat(parsed.people().getFirst().positionCode()).isEqualTo("POS-REAL");
        assertThat(parsed.positions()).extracting(MyHrWorkbookParser.PositionRow::positionCode)
                .containsExactly("POS-REAL");
    }

    @Test
    void missingPeopleSheetFailsTheFile() throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Other");
            workbook.write(out);
            file = out.toByteArray();
        }
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(file)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MyHR");
    }
}
