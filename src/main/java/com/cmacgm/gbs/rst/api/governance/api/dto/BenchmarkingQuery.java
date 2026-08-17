package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;

/**
 * Same-PL3 benchmarking filters. Rows are returned only when {@code pl3Code} is set.
 */
public record BenchmarkingQuery(
        String center,
        String domain,
        String pl1,
        String pl2,
        String pl3Code,
        LocalDate submittedFrom,
        LocalDate submittedTo) {
}
