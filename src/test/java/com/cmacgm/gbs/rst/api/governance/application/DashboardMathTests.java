package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardCenterRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardDomainRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardMetric;
import com.cmacgm.gbs.rst.api.governance.application.DashboardMath.AgingBucket;
import com.cmacgm.gbs.rst.api.governance.application.DashboardMath.KpiCoverage;
import org.junit.jupiter.api.Test;

class DashboardMathTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);

    @Test
    void keyRequiresAllParts() {
        assertThat(DashboardMath.key("GBS LEBANON", "SUP-1", "PL3-BANK")).isEqualTo("GBS LEBANON\u0001SUP-1\u0001PL3-BANK");
        assertThat(DashboardMath.key(" ", "SUP-1", "PL3-BANK")).isEmpty();
        assertThat(DashboardMath.key("GBS LEBANON", null, "PL3-BANK")).isEmpty();
    }

    @Test
    void kpiKeyRequiresAllParts() {
        assertThat(DashboardMath.kpiKey("GBS LEBANON", "SUP-1", "PL3-BANK", "CMA", "BEY", "LB"))
                .isEqualTo("GBS LEBANON\u0001SUP-1\u0001PL3-BANK\u0001CMA\u0001BEY\u0001LB");
        assertThat(DashboardMath.kpiKey("GBS LEBANON", "SUP-1", "PL3-BANK", " ", "BEY", "LB")).isEmpty();
        assertThat(DashboardMath.kpiKey("GBS LEBANON", "SUP-1", "PL3-BANK", "CMA", null, "LB")).isEmpty();
    }

    @Test
    void bucketsAreMutuallyExclusive() {
        assertThat(DashboardMath.bucket(LocalDate.of(2026, 7, 1), TODAY)).isEqualTo(AgingBucket.THIS_QUARTER);
        assertThat(DashboardMath.bucket(TODAY, TODAY)).isEqualTo(AgingBucket.THIS_QUARTER);
        assertThat(DashboardMath.bucket(LocalDate.of(2026, 6, 20), TODAY)).isEqualTo(AgingBucket.THREE_TO_SIX);
        assertThat(DashboardMath.bucket(LocalDate.of(2026, 2, 17), TODAY)).isEqualTo(AgingBucket.THREE_TO_SIX);
        assertThat(DashboardMath.bucket(LocalDate.of(2025, 10, 1), TODAY)).isEqualTo(AgingBucket.SIX_TO_TWELVE);
        assertThat(DashboardMath.bucket(LocalDate.of(2025, 8, 1), TODAY)).isEqualTo(AgingBucket.OVER_ONE_YEAR);
        assertThat(DashboardMath.bucket(null, TODAY)).isEqualTo(AgingBucket.NEVER_DONE);
    }

    @Test
    void percentAndOnTrackUseRoundedInteger() {
        assertThat(DashboardMath.percentLabel(bd("354"), bd("546"))).isEqualTo("65%");
        assertThat(DashboardMath.onTrack(bd("354"), bd("546"))).isTrue();
        assertThat(DashboardMath.onTrack(bd("5"), bd("11"))).isFalse();
        assertThat(DashboardMath.percentLabel(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo("—");
        assertThat(DashboardMath.onTrack(BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
        assertThat(DashboardMath.onTrack(bd("1"), bd("2"))).isTrue();
    }

    @Test
    void centersWeightByHcAndSort() {
        List<DashboardCenterRow> rows = DashboardMath.centers(List.of(
                status("GBS INDIA", "FINANCE", AgingBucket.THIS_QUARTER, "100"),
                status("GBS LEBANON", "FINANCE", AgingBucket.THIS_QUARTER, "10"),
                status("GBS LEBANON", "FINANCE", AgingBucket.NEVER_DONE, "20"),
                status("GBS LEBANON", "CUSTOMER CARE", AgingBucket.THREE_TO_SIX, "5")));
        assertThat(rows).extracting(DashboardCenterRow::center)
                .containsExactly("GBS INDIA", "GBS LEBANON");
        DashboardCenterRow lebanon = rows.get(1);
        assertThat(lebanon.applicableHc()).isEqualByComparingTo("35");
        assertThat(lebanon.completedThisQuarter()).isEqualByComparingTo("10");
        assertThat(lebanon.neverDone()).isEqualByComparingTo("20");
        assertThat(lebanon.completed3To6Months()).isEqualByComparingTo("5");
        assertThat(lebanon.completionPct()).isEqualTo("29%");
        assertThat(lebanon.onTrack()).isFalse();
        assertThat(lebanon.completedThisQuarter()
                .add(lebanon.completed3To6Months())
                .add(lebanon.neverDone())
                .add(lebanon.completed6To12Months())
                .add(lebanon.completedOver1Year())).isEqualByComparingTo(lebanon.applicableHc());
    }

    @Test
    void zeroHcDoesNotEnterTotals() {
        List<DashboardCenterRow> rows = DashboardMath.centers(List.of(
                status("GBS INDIA", "FINANCE", AgingBucket.NEVER_DONE, "0"),
                status("GBS INDIA", "FINANCE", AgingBucket.THIS_QUARTER, "8")));
        assertThat(rows.get(0).applicableHc()).isEqualByComparingTo("8");
        assertThat(rows.get(0).neverDone()).isEqualByComparingTo("0");
        assertThat(rows.get(0).completionPct()).isEqualTo("100%");
    }

    @Test
    void domainsGroupInsideCenter() {
        Map<String, List<DashboardDomainRow>> byCenter = DashboardMath.domainsByCenter(List.of(
                status("GBS LEBANON", "FINANCE", AgingBucket.THIS_QUARTER, "10"),
                status("GBS LEBANON", "FINANCE", AgingBucket.NEVER_DONE, "30"),
                status("GBS LEBANON", "CUSTOMER CARE", AgingBucket.THIS_QUARTER, "20")));
        assertThat(byCenter.get("GBS LEBANON")).extracting(DashboardDomainRow::domain)
                .containsExactly("CUSTOMER CARE", "FINANCE");
        DashboardDomainRow finance = byCenter.get("GBS LEBANON").get(1);
        assertThat(finance.applicableHc()).isEqualByComparingTo("40");
        assertThat(finance.completed()).isEqualByComparingTo("10");
        assertThat(finance.neverDone()).isEqualByComparingTo("30");
        assertThat(finance.pct()).isEqualTo("25%");
    }

    @Test
    void metricsUseHcTotalsAndCapacity() {
        List<DashboardMetric> metrics = DashboardMath.metrics(
                List.of(
                        status("GBS LEBANON", "FINANCE", AgingBucket.THIS_QUARTER, "10"),
                        status("GBS LEBANON", "FINANCE", AgingBucket.NEVER_DONE, "30")),
                3,
                new BigDecimal("128.44"),
                new BigDecimal("3000"));
        assertThat(metrics).extracting(DashboardMetric::label).containsExactly(
                "RST completion (%)",
                "Never done",
                "Stuck in validation",
                "Capacity Creation YTD (HC)",
                "YTD vs Actual Delivery HC (%)");
        assertThat(metrics.get(0).value()).isEqualTo("25%");
        assertThat(metrics.get(0).hint()).isEqualTo("10.0 / 40.0 Delivery HC completed this quarter");
        assertThat(metrics.get(1).value()).isEqualTo("30.0");
        assertThat(metrics.get(1).hint()).isEqualTo("Delivery HC not covered by a completed RST");
        assertThat(metrics.get(2).value()).isEqualTo("3");
        assertThat(metrics.get(2).tone()).isEqualTo("warn");
        assertThat(metrics.get(3).value()).isEqualTo("+128.4");
        assertThat(metrics.get(4).value()).isEqualTo("4.3%");
    }

    @Test
    void signedHcAndRatioHandleEmpty() {
        assertThat(DashboardMath.signedHc(null)).isEqualTo("—");
        assertThat(DashboardMath.signedHc(new BigDecimal("-1.25"))).isEqualTo("-1.3");
        assertThat(DashboardMath.ratioPct(new BigDecimal("10"), BigDecimal.ZERO)).isEqualTo("—");
        assertThat(DashboardMath.formatHc(new BigDecimal("1284.44"))).isEqualTo("1,284.4");
    }

    private static KpiCoverage status(String center, String domain, AgingBucket bucket, String hc) {
        return new KpiCoverage(center, domain, bucket, bd(hc));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
