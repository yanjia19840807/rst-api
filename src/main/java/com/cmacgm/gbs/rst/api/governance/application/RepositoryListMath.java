package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;

/**
 * Totals for filtered RST Repository rows (all matches, not the current page).
 */
public final class RepositoryListMath {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private RepositoryListMath() {
    }

    /**
     * Sums additive HC columns. Capacity % and Volume YoY are not totaled.
     *
     * @param items filtered repository rows
     * @return 2 dp sums; RS / Support / Capacity stay null when every row is empty
     */
    public static Totals summarize(List<RepositoryRow> items) {
        if (items == null || items.isEmpty()) {
            return new Totals(ZERO, null, null, null);
        }
        BigDecimal delivery = BigDecimal.ZERO;
        BigDecimal rs = BigDecimal.ZERO;
        BigDecimal support = BigDecimal.ZERO;
        BigDecimal capacity = BigDecimal.ZERO;
        boolean anyRs = false;
        boolean anySupport = false;
        boolean anyCapacity = false;
        for (RepositoryRow row : items) {
            delivery = delivery.add(row.deliveryHc() == null ? BigDecimal.ZERO : row.deliveryHc());
            if (row.rsHc() != null) {
                rs = rs.add(row.rsHc());
                anyRs = true;
            }
            if (row.support() != null) {
                support = support.add(row.support());
                anySupport = true;
            }
            if (row.capacityCreation() != null) {
                capacity = capacity.add(row.capacityCreation());
                anyCapacity = true;
            }
        }
        return new Totals(
                delivery.setScale(2, RoundingMode.HALF_UP),
                anyRs ? rs.setScale(2, RoundingMode.HALF_UP) : null,
                anySupport ? support.setScale(2, RoundingMode.HALF_UP) : null,
                anyCapacity ? capacity.setScale(2, RoundingMode.HALF_UP) : null);
    }

    /**
     * Additive HC totals for one filtered row set.
     */
    public record Totals(
            BigDecimal deliveryHc,
            BigDecimal rightSizingHc,
            BigDecimal support,
            BigDecimal capacityCreation) {
    }
}
