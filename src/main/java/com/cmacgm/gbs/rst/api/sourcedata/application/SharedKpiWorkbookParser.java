package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.io.InputStream;
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
 * Reads the first sheet of an SKPI workbook. The office name is mapped later.
 */
@Component
public class SharedKpiWorkbookParser {

    static final String PERIOD = "Period";
    static final String CARRIER = "CARRIER";
    static final String SITE = "GBS SITE";
    static final String DOMAIN = "Domain";
    static final String PROCESS = "PROCESS";
    static final String COUNTRY = "COUNTRY";
    static final String HC = "Delivery HC";

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    /**
     * One SKPI row before site mapping and process filtering.
     *
     * @param sourceRow Excel row number
     * @param period period text
     * @param carrier carrier
     * @param gbsSite office name
     * @param domain domain
     * @param process process name
     * @param country country
     * @param deliveryHc Delivery HC text
     */
    public record KpiRow(
            int sourceRow,
            String period,
            String carrier,
            String gbsSite,
            String domain,
            String process,
            String country,
            String deliveryHc) {
    }

    /**
     * @param inputStream xlsx
     * @return data rows
     */
    public List<KpiRow> parse(InputStream inputStream) {
        try (Workbook workbook = ExcelGrid.open(inputStream)) {
            Sheet sheet = ExcelGrid.firstSheet(workbook);
            if (sheet == null) {
                throw ExcelGrid.missingSheet("SKPI");
            }
            Map<String, Integer> headers = ExcelGrid.headers(sheet.getRow(0), formatter);
            ExcelGrid.require(headers, PERIOD, CARRIER, SITE, DOMAIN, PROCESS, COUNTRY, HC);
            List<KpiRow> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || ExcelGrid.rowBlank(row, headers, formatter, SITE, PROCESS, CARRIER, COUNTRY, HC)) {
                    continue;
                }
                rows.add(new KpiRow(
                        index + 1,
                        ExcelGrid.text(row, headers, PERIOD, formatter),
                        ExcelGrid.text(row, headers, CARRIER, formatter),
                        ExcelGrid.text(row, headers, SITE, formatter),
                        ExcelGrid.text(row, headers, DOMAIN, formatter),
                        ExcelGrid.text(row, headers, PROCESS, formatter),
                        ExcelGrid.text(row, headers, COUNTRY, formatter),
                        ExcelGrid.text(row, headers, HC, formatter)));
            }
            return rows;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ExcelGrid.unreadable(ex);
        }
    }
}
