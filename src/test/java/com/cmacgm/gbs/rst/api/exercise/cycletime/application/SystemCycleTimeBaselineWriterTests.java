package com.cmacgm.gbs.rst.api.exercise.cycletime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.cycletime.application.SystemCycleTimeBaselineWriter.SystemBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository.ExerciseTmsSessionRow;
import org.junit.jupiter.api.Test;

class SystemCycleTimeBaselineWriterTests {

    @Test
    void treatsEachIncludedSessionAsAnIndependentSample() {
        List<Double> samples = SystemCycleTimeBaselineWriter.includedSecondsPerUnit(List.of(
                row(true, "Match", "1", 10),
                row(true, "Match", "1", 30),
                row(true, "Update", "1", 50),
                row(false, "Update", "1", 90)));

        assertThat(samples).containsExactly(10.0, 30.0, 50.0);
    }

    @Test
    void usesTheMedianOfAllIncludedSessionsWhenCombineSubtaskTimeIsOff() {
        SystemBaseline baseline = SystemCycleTimeBaselineWriter.computeSystemBaseline(
                List.of(
                        row(true, "Match", "1", 10),
                        row(true, "Match", "1", 30),
                        row(true, "Update", "1", 50),
                        row(false, "Update", "1", 90)),
                false).orElseThrow();

        assertThat(baseline.sampleCount()).isEqualTo(3);
        assertThat(baseline.seconds()).isEqualByComparingTo("30.000000");
    }

    @Test
    void sumsEachSubtaskMedianWhenCombineSubtaskTimeIsOn() {
        SystemBaseline baseline = SystemCycleTimeBaselineWriter.computeSystemBaseline(
                List.of(
                        row(true, "Match", "1", 10),
                        row(true, "Match", "1", 30),
                        row(true, "Match", "1", 50),
                        row(true, "Update", "1", 20),
                        row(true, "Update", "1", 40),
                        row(false, "Update", "1", 100)),
                true).orElseThrow();

        assertThat(baseline.sampleCount()).isEqualTo(5);
        assertThat(baseline.seconds()).isEqualByComparingTo("60.000000");
    }

    @Test
    void groupsTrimmedSubtaskNamesWhenCombining() {
        SystemBaseline baseline = SystemCycleTimeBaselineWriter.computeSystemBaseline(
                List.of(
                        row(true, " Match ", "1", 10),
                        row(true, "Match", "1", 30),
                        row(true, "Update", "1", 40)),
                true).orElseThrow();

        assertThat(baseline.seconds()).isEqualByComparingTo("60.000000");
    }

    @Test
    void treatsBlankSubtaskNamesAsOneGroupWhenCombining() {
        SystemBaseline baseline = SystemCycleTimeBaselineWriter.computeSystemBaseline(
                List.of(
                        row(true, "", "1", 10),
                        row(true, "  ", "1", 30),
                        row(true, "Match", "1", 40)),
                true).orElseThrow();

        assertThat(baseline.seconds()).isEqualByComparingTo("60.000000");
    }

    @Test
    void skipsSessionsWithoutAPositiveVolume() {
        SystemBaseline baseline = SystemCycleTimeBaselineWriter.computeSystemBaseline(
                List.of(
                        row(true, "Match", null, 10),
                        row(true, "Match", "0", 40),
                        row(true, "Match", "1", 30)),
                false).orElseThrow();

        assertThat(baseline.sampleCount()).isEqualTo(1);
        assertThat(baseline.seconds()).isEqualByComparingTo("30.000000");
    }

    @Test
    void ignoresBlankAndNonPositiveVolumeWhenAllSamplesAreInvalid() {
        assertThat(SystemCycleTimeBaselineWriter.computeSystemBaseline(
                List.of(
                        row(true, "Match", null, 10),
                        row(true, "Update", "0", 40)),
                true)).isEmpty();
    }

    @Test
    void takesTheMiddleValueForAnOddSampleCount() {
        assertThat(SystemCycleTimeBaselineWriter.medianOf(List.of(10.0, 182.0, 50.0)))
                .isEqualByComparingTo("50.000000");
    }

    @Test
    void averagesTheMiddlePairForAnEvenSampleCount() {
        assertThat(SystemCycleTimeBaselineWriter.medianOf(List.of(10.0, 20.0, 40.0, 50.0)))
                .isCloseTo(new BigDecimal("30.000000"), within(new BigDecimal("0.000001")));
    }

    private static ExerciseTmsSessionRow row(
            boolean included, String subtaskName, String volume, long netDurationSeconds) {
        BigDecimal processedVolume = volume == null ? null : new BigDecimal(volume);
        return new StubRow(included, subtaskName, processedVolume, netDurationSeconds);
    }

    private record StubRow(
            boolean included, String subtaskName, BigDecimal processedVolume, long netDurationSeconds)
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
            return "";
        }

        @Override
        public String getAgentCcgid() {
            return "AGENT001";
        }

        @Override
        public String getToolkitName() {
            return "Demo Toolkit";
        }

        @Override
        public String getSubtaskName() {
            return subtaskName;
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
        public String getRemarks() {
            return "";
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
