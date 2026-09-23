package com.cmacgm.gbs.rst.api.tms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse.SharedKpiResponse;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse.SubtaskResponse;
import org.junit.jupiter.api.Test;

class TmsToolkitScopeTests {

    private static final UUID BOOKING = UUID.fromString("679d30a7-cc77-4396-b6c6-f4631b509677");
    private static final UUID STANDARD = UUID.fromString("b74ddd51-d031-419b-a4fb-95f41cb9e3b4");

    @Test
    void countryTokenMatchesCombinedLabel() {
        List<UUID> ids = TmsToolkitScope.matchingIds(
                List.of(booking(), standard()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "CHINA");
        assertThat(ids).containsExactly(BOOKING, STANDARD);
    }

    @Test
    void hongKongTokenDoesNotRequireExactCountryString() {
        List<UUID> ids = TmsToolkitScope.matchingIds(
                List.of(booking()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "HONG KONG SAR");
        assertThat(ids).containsExactly(BOOKING);
    }

    @Test
    void scopedIdsAndPl3RestrictMatches() {
        List<UUID> ids = TmsToolkitScope.matchingIds(
                List.of(booking(), standard()),
                List.of(STANDARD),
                null,
                "GBS CHINA",
                "CUSTOMER CARE",
                "768",
                "CMA CGM",
                "CNCKG",
                null);
        assertThat(ids).containsExactly(STANDARD);
    }

    private static ToolkitResponse booking() {
        return toolkit(BOOKING, "BOOKING AMENDMENTS", "367");
    }

    private static ToolkitResponse standard() {
        return toolkit(STANDARD, "STANDARD BOOKINGS", "768");
    }

    private static ToolkitResponse toolkit(UUID id, String pl3Name, String pl3Code) {
        return new ToolkitResponse(
                id,
                pl3Name,
                null,
                "175344",
                "GBS CHINA",
                "CUSTOMER CARE",
                "BOOKING",
                "BOOKING",
                pl3Code,
                pl3Name,
                false,
                true,
                0,
                List.<SubtaskResponse>of(),
                List.of(
                        new SharedKpiResponse(UUID.randomUUID(), "CMA CGM", "CNCKG", "CHINA"),
                        new SharedKpiResponse(
                                UUID.randomUUID(), "CMA CGM", "CNCKG", "HONG KONG SAR, CHINA")),
                false,
                0,
                0,
                0,
                false,
                null,
                null,
                null,
                null);
    }
}
