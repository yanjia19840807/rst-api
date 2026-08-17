package com.cmacgm.gbs.rst.api.cycletime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import org.junit.jupiter.api.Test;

class SystemCycleTimeBaselineWriterTests {

    @Test
    void treatsEachIncludedSessionAsAnIndependentSample() {
        List<Double> samples = SystemCycleTimeBaselineWriter.includedSecondsPerUnit(
                List.of(
                        row(true, "INV-1", "1", 10),
                        row(true, "INV-1", "1", 30),
                        row(true, "INV-2", "1", 50),
                        row(false, "INV-3", "1", 90)),
                false);

        assertThat(samples).containsExactly(10.0, 30.0, 50.0);
        assertThat(SystemCycleTimeBaselineWriter.medianOf(samples))
                .isEqualByComparingTo("30.000000");
    }

    @Test
    void sumsNetDurationForTheSameReferenceBeforeTheMedian() {
        List<Double> samples = SystemCycleTimeBaselineWriter.includedSecondsPerUnit(
                List.of(
                        row(true, "INV-1", "1", 10),
                        row(true, "INV-1", "1", 30),
                        row(true, "INV-2", "1", 50),
                        row(false, "INV-1", "1", 100)),
                true);

        assertThat(samples).containsExactly(40.0, 50.0);
        assertThat(SystemCycleTimeBaselineWriter.medianOf(samples))
                .isEqualByComparingTo("45.000000");
    }

    @Test
    void keepsBlankReferencesAsIndependentSamplesWhenCombining() {
        List<Double> samples = SystemCycleTimeBaselineWriter.includedSecondsPerUnit(
                List.of(
                        row(true, "", "1", 10),
                        row(true, "  ", "1", 30),
                        row(true, "INV-1", "1", 12),
                        row(true, "INV-1", "1", 8)),
                true);

        assertThat(samples).containsExactly(10.0, 30.0, 20.0);
    }

    @Test
    void groupsTrimmedReferencesAndUsesTheLargestVolumeInTheGroup() {
        List<Double> samples = SystemCycleTimeBaselineWriter.includedSecondsPerUnit(
                List.of(
                        row(true, " INV-1 ", "2", 20),
                        row(true, "INV-1", "2", 20)),
                true);

        assertThat(samples).containsExactly(20.0);
    }

    @Test
    void skipsCombinedGroupsThatHaveNoPositiveVolume() {
        List<Double> samples = SystemCycleTimeBaselineWriter.includedSecondsPerUnit(
                List.of(row(true, "INV-1", "0", 40), row(true, "INV-2", "1", 10)),
                true);

        assertThat(samples).containsExactly(10.0);
    }

    @Test
    void averagesTheMiddlePairForAnEvenSampleCount() {
        assertThat(SystemCycleTimeBaselineWriter.medianOf(List.of(10.0, 20.0, 40.0, 50.0)))
                .isCloseTo(new BigDecimal("30.000000"), within(new BigDecimal("0.000001")));
    }

    private static ExerciseTmsSessionRow row(
            boolean included, String reference, String volume, long netDurationSeconds) {
        return new StubRow(included, reference, new BigDecimal(volume), netDurationSeconds);
    }

    private record StubRow(
            boolean included, String reference, BigDecimal processedVolume, long netDurationSeconds)
            implements ExerciseTmsSessionRow {

        @Override
        public UUID getTmsSessionId() {
            return UUID.randomUUID();
        }

        @Override
        public String getSessionNo() {
            return "TMS-TEST";
        }

        @Override
        public String getReference() {
            return reference;
        }

        @Override
        public String getAgentName() {
            return "Agent";
        }

        @Override
        public String getSubtaskName() {
            return "Match";
        }

        @Override
        public BigDecimal getProcessedVolume() {
            return processedVolume;
        }

        @Override
        public long getNetDurationSeconds() {
            return netDurationSeconds;
        }

        @Override
        public boolean getIncluded() {
            return included;
        }

        @Override
        public String getExclusionReason() {
            return null;
        }

        @Override
        public Instant getStartedAt() {
            return Instant.parse("2026-08-05T01:00:00Z");
        }

        @Override
        public Instant getEndedAt() {
            return Instant.parse("2026-08-05T01:01:00Z");
        }
    }
}
