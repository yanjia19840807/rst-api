package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;
import org.junit.jupiter.api.Test;

class RepositoryListMathTests {

    @Test
    void emptyRowsYieldZeroDeliveryAndNullDerived() {
        RepositoryListMath.Totals totals = RepositoryListMath.summarize(List.of());
        assertThat(totals.deliveryHc()).isEqualByComparingTo("0.00");
        assertThat(totals.rightSizingHc()).isNull();
        assertThat(totals.support()).isNull();
        assertThat(totals.capacityCreation()).isNull();
    }

    @Test
    void sumsAdditiveColumnsAndIgnoresNullDerived() {
        RepositoryListMath.Totals totals = RepositoryListMath.summarize(List.of(
                row("10", "4", "1", "5"),
                row("5", null, "0.5", null)));

        assertThat(totals.deliveryHc()).isEqualByComparingTo("15.00");
        assertThat(totals.rightSizingHc()).isEqualByComparingTo("4.00");
        assertThat(totals.support()).isEqualByComparingTo("1.50");
        assertThat(totals.capacityCreation()).isEqualByComparingTo("5.00");
    }

    private static RepositoryRow row(String delivery, String rs, String support, String capacity) {
        return new RepositoryRow(
                "EX-1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "CMA",
                "SHA",
                "GBS CHINA",
                "Finance",
                "PL1",
                "PL2",
                "BANK REC",
                "Bank Rec",
                "CN",
                new BigDecimal(delivery),
                rs == null ? null : new BigDecimal(rs),
                support == null ? null : new BigDecimal(support),
                capacity == null ? null : new BigDecimal(capacity),
                new BigDecimal("10.0"),
                "",
                "2026-03",
                "2026-03-10");
    }
}
