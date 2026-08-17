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
import com.cmacgm.gbs.rst.api.governance.application.DashboardMath.ObligationStatus;
import org.junit.jupiter.api.Test;

class DashboardMathTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);

    @Test
    void keyRequiresAllParts() {
        assertThat(DashboardMath.key("GBS China", "SUP-1", "PL3-BANK")).isEqualTo("GBS China\u0001SUP-1\u0001PL3-BANK");
        assertThat(DashboardMath.key(" ", "SUP-1", "PL3-BANK")).isEmpty();
        assertThat(DashboardMath.key("GBS China", null, "PL3-BANK")).isEmpty();
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
        assertThat(DashboardMath.percentLabel(354, 546)).isEqualTo("65%");
        assertThat(DashboardMath.onTrack(354, 546)).isTrue();
        assertThat(DashboardMath.onTrack(5, 11)).isFalse();
        assertThat(DashboardMath.percentLabel(0, 0)).isEqualTo("—");
        assertThat(DashboardMath.onTrack(0, 0)).isFalse();
        assertThat(DashboardMath.onTrack(1, 2)).isTrue();
    }

    @Test
    void centersPartitionAndSort() {
        List<DashboardCenterRow> rows = DashboardMath.centers(List.of(
                status("GBS India", "FINANCE", AgingBucket.THIS_QUARTER),
                status("GBS China", "FINANCE", AgingBucket.THIS_QUARTER),
                status("GBS China", "FINANCE", AgingBucket.NEVER_DONE),
                status("GBS China", "CUSTOMER CARE", AgingBucket.THREE_TO_SIX)));
        assertThat(rows).extracting(DashboardCenterRow::center)
                .containsExactly("GBS China", "GBS India");
        DashboardCenterRow china = rows.getFirst();
        assertThat(china.applicablePl3()).isEqualTo(3);
        assertThat(china.completedThisQuarter()).isEqualTo(1);
        assertThat(china.neverDone()).isEqualTo(1);
        assertThat(china.completed3To6Months()).isEqualTo(1);
        assertThat(china.completionPct()).isEqualTo("33%");
        assertThat(china.onTrack()).isFalse();
        assertThat(china.completedThisQuarter()
                + china.completed3To6Months()
                + china.neverDone()
                + china.completed6To12Months()
                + china.completedOver1Year()).isEqualTo(china.applicablePl3());
    }

    @Test
    void domainsGroupInsideCenter() {
        Map<String, List<DashboardDomainRow>> byCenter = DashboardMath.domainsByCenter(List.of(
                status("GBS China", "FINANCE", AgingBucket.THIS_QUARTER),
                status("GBS China", "FINANCE", AgingBucket.NEVER_DONE),
                status("GBS China", "CUSTOMER CARE", AgingBucket.THIS_QUARTER)));
        assertThat(byCenter.get("GBS China")).extracting(DashboardDomainRow::domain)
                .containsExactly("CUSTOMER CARE", "FINANCE");
        DashboardDomainRow finance = byCenter.get("GBS China").get(1);
        assertThat(finance.applicablePl3()).isEqualTo(2);
        assertThat(finance.completed()).isEqualTo(1);
        assertThat(finance.neverDone()).isEqualTo(1);
        assertThat(finance.pct()).isEqualTo("50%");
    }

    @Test
    void metricsUseTotalsAndCapacity() {
        List<DashboardMetric> metrics = DashboardMath.metrics(
                List.of(
                        status("GBS China", "FINANCE", AgingBucket.THIS_QUARTER),
                        status("GBS China", "FINANCE", AgingBucket.NEVER_DONE)),
                3,
                new BigDecimal("128.44"),
                new BigDecimal("3000"));
        assertThat(metrics).extracting(DashboardMetric::label).containsExactly(
                "RST completion",
                "Never done",
                "Stuck in validation",
                "Capacity Creation YTD",
                "YTD % vs Actual Delivery HC");
        assertThat(metrics.get(0).value()).isEqualTo("50%");
        assertThat(metrics.get(0).hint()).isEqualTo("1 / 2 applicable PL3 completed this quarter");
        assertThat(metrics.get(1).value()).isEqualTo("1");
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
    }

    private static ObligationStatus status(String center, String domain, AgingBucket bucket) {
        return new ObligationStatus(center, domain, bucket);
    }
}
