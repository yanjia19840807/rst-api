package com.cmacgm.gbs.rst.api.timesheet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetAlignment;

/**
 * Read-model Timesheet alignment. {@code structuralDrift} is the only red-flag.
 */
public record TimesheetAlignmentView(
        boolean structuralDrift,
        boolean outOfScope,
        LocalDate currentMonthlySyncDate,
        BigDecimal currentDeliveryHc,
        List<LineView> lines) {

    /**
     * Maps a computed alignment.
     *
     * @param alignment computed result
     * @return API view
     */
    public static TimesheetAlignmentView from(TimesheetAlignment alignment) {
        return new TimesheetAlignmentView(
                alignment.structuralDrift(),
                alignment.outOfScope(),
                alignment.currentMonthlySyncDate(),
                alignment.currentDeliveryHc(),
                alignment.lines().stream()
                        .map(line -> new LineView(
                                line.carrier(),
                                line.site(),
                                line.customerCountry(),
                                line.missing(),
                                line.currentDeliveryHc()))
                        .toList());
    }

    public boolean lineMissing(String carrier, String site, String customerCountry) {
        return lines.stream().anyMatch(line ->
                line.missing()
                        && line.carrier().equals(carrier)
                        && line.site().equals(site)
                        && line.customerCountry().equals(customerCountry));
    }

    public record LineView(
            String carrier,
            String site,
            String customerCountry,
            boolean missing,
            BigDecimal currentDeliveryHc) {
    }
}
