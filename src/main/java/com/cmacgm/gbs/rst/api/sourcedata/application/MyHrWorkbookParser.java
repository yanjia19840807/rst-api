package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Reads the MyHR workbook: people on {@code MyHR}, positions on {@code Position Report}.
 * The position is the {@code Code} column. The trailing {@code CODE} column is ignored.
 */
@Component
public class MyHrWorkbookParser {

    static final String SHEET_PEOPLE = "MyHR";
    static final String SHEET_POSITIONS = "Position Report";
    static final String PERSON_ID = "Person ID";
    static final String USERNAME = "Username";
    static final String STATUS = "Employee Status";
    static final String LAST_NAME = "Last Name";
    static final String FIRST_NAME = "First Name";
    static final String EMAIL = "Business Email Information Email Address";
    static final String DIRECT_REPORTS = "Direct Reports";
    static final String COMPANY_COUNTRY = "Company Country";
    static final String JOB_ROLE = "Job Profile - Career Level";
    static final String POSITION_CODE = "Code";
    static final String EVENT_DATE = "Event Date";
    static final String REPORT_CODE = "Position Code";
    static final String REPORT_STATUS = "effectiveStatus";
    static final String REPORT_PARENT = "Parent Position code";

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    /**
     * One MyHR person row. {@code positionCode} is {@code Code}, not {@code CODE}.
     *
     * @param sourceRow Excel row number
     * @param personId Person ID
     * @param username CCGID
     * @param status employee status
     * @param lastName last name
     * @param firstName first name
     * @param email email
     * @param directReports direct report count as text
     * @param companyCountry company country
     * @param jobRole job profile
     * @param positionCode position code
     * @param eventDate event date
     */
    public record PersonRow(
            int sourceRow,
            String personId,
            String username,
            String status,
            String lastName,
            String firstName,
            String email,
            String directReports,
            String companyCountry,
            String jobRole,
            String positionCode,
            LocalDate eventDate) {
    }

    /**
     * One Position Report row.
     *
     * @param sourceRow Excel row number
     * @param positionCode position code
     * @param status effective status
     * @param parentCode parent position code
     */
    public record PositionRow(int sourceRow, String positionCode, String status, String parentCode) {
    }

    /**
     * Parsed workbook.
     *
     * @param people MyHR rows
     * @param positions Position Report rows
     */
    public record Parsed(List<PersonRow> people, List<PositionRow> positions) {
    }

    /**
     * @param inputStream xlsx
     * @return people and positions
     */
    public Parsed parse(InputStream inputStream) {
        try (Workbook workbook = ExcelGrid.open(inputStream)) {
            Sheet people = ExcelGrid.sheet(workbook, SHEET_PEOPLE);
            Sheet positions = ExcelGrid.sheet(workbook, SHEET_POSITIONS);
            if (people == null) {
                throw ExcelGrid.missingSheet(SHEET_PEOPLE);
            }
            if (positions == null) {
                throw ExcelGrid.missingSheet(SHEET_POSITIONS);
            }
            return new Parsed(readPeople(people), readPositions(positions));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ExcelGrid.unreadable(ex);
        }
    }

    private List<PersonRow> readPeople(Sheet sheet) {
        Map<String, Integer> headers = ExcelGrid.headers(sheet.getRow(0), formatter);
        ExcelGrid.require(
                headers,
                PERSON_ID,
                USERNAME,
                STATUS,
                LAST_NAME,
                FIRST_NAME,
                EMAIL,
                DIRECT_REPORTS,
                COMPANY_COUNTRY,
                JOB_ROLE,
                POSITION_CODE,
                EVENT_DATE);
        List<PersonRow> rows = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || ExcelGrid.rowBlank(
                    row, headers, formatter, USERNAME, PERSON_ID, STATUS, POSITION_CODE, COMPANY_COUNTRY)) {
                continue;
            }
            rows.add(new PersonRow(
                    index + 1,
                    ExcelGrid.text(row, headers, PERSON_ID, formatter),
                    ExcelGrid.text(row, headers, USERNAME, formatter),
                    ExcelGrid.text(row, headers, STATUS, formatter),
                    ExcelGrid.text(row, headers, LAST_NAME, formatter),
                    ExcelGrid.text(row, headers, FIRST_NAME, formatter),
                    ExcelGrid.text(row, headers, EMAIL, formatter),
                    ExcelGrid.text(row, headers, DIRECT_REPORTS, formatter),
                    ExcelGrid.text(row, headers, COMPANY_COUNTRY, formatter),
                    ExcelGrid.text(row, headers, JOB_ROLE, formatter),
                    ExcelGrid.text(row, headers, POSITION_CODE, formatter),
                    ExcelGrid.date(row, headers, EVENT_DATE, formatter)));
        }
        return rows;
    }

    private List<PositionRow> readPositions(Sheet sheet) {
        Map<String, Integer> headers = ExcelGrid.headers(sheet.getRow(0), formatter);
        ExcelGrid.require(headers, REPORT_CODE, REPORT_STATUS, REPORT_PARENT);
        List<PositionRow> rows = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || ExcelGrid.rowBlank(row, headers, formatter, REPORT_CODE)) {
                continue;
            }
            rows.add(new PositionRow(
                    index + 1,
                    ExcelGrid.text(row, headers, REPORT_CODE, formatter),
                    ExcelGrid.text(row, headers, REPORT_STATUS, formatter),
                    ExcelGrid.text(row, headers, REPORT_PARENT, formatter)));
        }
        return rows;
    }
}
