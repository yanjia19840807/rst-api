package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SlotVolumeJsonTests {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void slotJsonRoundTripKeepsCivilWallClockWithoutZ() {
        String json = """
                {"slotStartAt":"2026-03-15T08:00:00","slotEndAt":"2026-03-15T08:30:00","actualVolume":10}
                """;
        SlotVolumeRequest parsed = mapper.readValue(json, SlotVolumeRequest.class);
        assertThat(parsed.slotStartAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 8, 0));
        assertThat(parsed.slotEndAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 8, 30));
        String written = mapper.writeValueAsString(new SlotVolumeRequest(
                parsed.slotStartAt(), parsed.slotEndAt(), BigDecimal.TEN));
        assertThat(written).contains("2026-03-15T08:00:00");
        assertThat(written).doesNotContain("Z");
    }
}
