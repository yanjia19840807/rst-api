package com.cmacgm.gbs.rst.api.exercise.application;

import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;

/**
 * Stable business identity used to map Shared KPI lines between snapshots.
 */
public record KpiBusinessKey(String carrier, String site, String customerCountry) {

    public KpiBusinessKey {
        carrier = value(carrier);
        site = value(site);
        customerCountry = value(customerCountry);
    }

    public static KpiBusinessKey of(ExerciseSharedKpiLine line) {
        return new KpiBusinessKey(
                line.getCarrier(), line.getSite(), line.getCustomerCountry());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
