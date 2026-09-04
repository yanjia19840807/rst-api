package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.excel.ExcelSheets;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;

/**
 * Excel export for governance report tables.
 */
@Component
public class GovernanceExcelService {

    private static final List<String> REPOSITORY_HEADERS = List.of(
            "Exercise No",
            "Carrier",
            "GBS Site",
            "GBS Country",
            "Domain",
            "PL1",
            "PL2",
            "PL3",
            "Toolkit",
            "Customer Country",
            "Delivery HC",
            "Right Sizing HC",
            "Production Support",
            "Capacity Creation",
            "Capacity Creation %",
            "Volume Increase % YoY");

    private static final List<String> SUPPORT_HEADERS = List.of(
            "Exercise NO",
            "GBS Center",
            "Domain",
            "PL3",
            "Toolkit",
            "Standard Category",
            "Activity",
            "Frequency",
            "Volume",
            "UOM",
            "FTE",
            "Comments");

    private static final List<String> BENCHMARK_HEADERS = List.of(
            "GBS",
            "Shared KPI Line",
            "Domain",
            "PL3",
            "Cycle time",
            "Daily Production Capacity / Agent",
            "Production Support Ratio",
            "Capacity Creation");

    /**
     * Writes filtered RST Repository rows.
     *
     * @param rows filtered rows in list order
     * @return xlsx bytes
     */
    public byte[] exportRepository(List<RepositoryRow> rows) {
        List<List<String>> body = new ArrayList<>();
        for (RepositoryRow row : rows) {
            body.add(List.of(
                    blank(row.exerciseId()),
                    blank(row.carrier()),
                    blank(row.site()),
                    blank(row.country()),
                    blank(row.domain()),
                    blank(row.pl1()),
                    blank(row.pl2()),
                    blank(row.pl3()),
                    blank(row.toolkit()),
                    blank(row.kpi()),
                    decimal(row.deliveryHc()),
                    decimal(row.rsHc()),
                    decimal(row.support()),
                    decimal(row.capacityCreation()),
                    decimal(row.capacityPct()),
                    blank(row.volumeYoY())));
        }
        return ExcelSheets.write("RST Repository", REPOSITORY_HEADERS, body);
    }

    /**
     * Writes filtered Support Repository activity rows.
     *
     * @param rows filtered rows in list order
     * @return xlsx bytes
     */
    public byte[] exportSupportRepository(List<SupportRepositoryRow> rows) {
        List<List<String>> body = new ArrayList<>();
        for (SupportRepositoryRow row : rows) {
            body.add(List.of(
                    blank(row.exerciseNo()),
                    blank(row.center()),
                    blank(row.domain()),
                    blank(row.pl3()),
                    blank(row.toolkit()),
                    blank(row.standardCategory()),
                    blank(row.activity()),
                    blank(row.frequency()),
                    decimal(row.volume()),
                    blank(row.uom()),
                    decimal(row.fte()),
                    blank(row.comments())));
        }
        return ExcelSheets.write("Support Repository", SUPPORT_HEADERS, body);
    }

    /**
     * Writes filtered Benchmarking rows.
     *
     * @param rows filtered rows in list order
     * @return xlsx bytes
     */
    public byte[] exportBenchmarking(List<BenchmarkRow> rows) {
        List<List<String>> body = new ArrayList<>();
        for (BenchmarkRow row : rows) {
            body.add(List.of(
                    blank(row.gbs()),
                    blank(row.sharedKpiLine()),
                    blank(row.domain()),
                    blank(row.pl3()),
                    decimal(row.cycleTimeSeconds()),
                    decimal(row.dailyCapacityPerAgent()),
                    decimal(row.productionSupportRatioPct()),
                    decimal(row.capacityCreation())));
        }
        return ExcelSheets.write("Benchmarking", BENCHMARK_HEADERS, body);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
