package com.cmacgm.gbs.rst.api.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstCenters;

class CenterZonesTests {

    @Test
    void catalogListsEachCenterWithIanaZone() {
        assertThat(CenterZones.catalog())
                .extracting(CenterZones.CenterZone::center, CenterZones.CenterZone::timeZone)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_CHINA, "Asia/Shanghai"),
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_INDIA, "Asia/Kolkata"),
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_LEBANON, "Asia/Beirut"),
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_ESTONIA, "Europe/Tallinn"),
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_COSTA_RICA, "America/Costa_Rica"),
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_PHILIPPINES, "Asia/Manila"),
                        org.assertj.core.groups.Tuple.tuple(RstCenters.GBS_PORTUGAL, "Europe/Lisbon"));
    }

    @Test
    void mapsEachCanonicalCenter() {
        assertThat(CenterZones.of(RstCenters.GBS_CHINA)).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(CenterZones.of(RstCenters.GBS_INDIA)).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(CenterZones.of("gbs portugal")).isEqualTo(ZoneId.of("Europe/Lisbon"));
    }

    @Test
    void rejectsUnknownCenter() {
        assertThatThrownBy(() -> CenterZones.of("GBS UNKNOWN"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("GBS UNKNOWN");
    }

    @Test
    void sameInstantCanFallOnDifferentCenterDays() {
        Instant lateUtc = Instant.parse("2026-03-15T18:30:00Z");
        assertThat(CenterDates.dateOf(lateUtc, RstCenters.GBS_INDIA))
                .isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(CenterDates.dateOf(lateUtc, RstCenters.GBS_PORTUGAL))
                .isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(CenterDates.dateOf(lateUtc, RstCenters.GBS_CHINA))
                .isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(CenterDates.dateTimeOf(lateUtc, RstCenters.GBS_INDIA))
                .isEqualTo(LocalDateTime.of(2026, 3, 16, 0, 0));
        assertThat(CenterDates.civilDateTime(lateUtc, RstCenters.GBS_INDIA))
                .isEqualTo("2026-03-16T00:00:00");
        assertThat(CenterDates.civilDateOf("2026-03-16T00:00:00"))
                .isEqualTo(LocalDate.of(2026, 3, 16));
    }
}
