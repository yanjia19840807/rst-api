package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;

/**
 * Same-PL3 benchmarking filters. Rows are returned only when {@code pl3Code} is set.
 */
public record BenchmarkingQuery(
        String exerciseCode,
        String center,
        String domain,
        String pl1,
        String pl2,
        String pl3Code,
        String carrier,
        String site,
        String customerCountry,
        String sizingMonth,
        LocalDate validatedFrom,
        LocalDate validatedTo) {
}
