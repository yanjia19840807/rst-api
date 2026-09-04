package com.cmacgm.gbs.rst.api.tms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;
import org.junit.jupiter.api.Test;

class TmsSessionTests {

    private static final Instant START = Instant.parse("2026-08-05T01:00:00Z");

    @Test
    void tracksElapsedTimeAcrossPauseAndResume() {
        TmsSession session = newSession();

        session.pause(START.plusSeconds(10));
        assertThat(session.getStatus()).isEqualTo(TmsSessionStatus.PAUSED);
        assertThat(session.elapsedSeconds(START.plusSeconds(20))).isEqualTo(10);

        session.resume(START.plusSeconds(20));
        session.end(START.plusSeconds(25));

        assertThat(session.getStatus()).isEqualTo(TmsSessionStatus.COMPLETED);
        assertThat(session.getNetDurationSeconds()).isEqualTo(15);
        assertThat(session.getEndedAt()).isEqualTo(START.plusSeconds(25));
    }

    @Test
    void defaultsMissingVolumeToOneAndAllowsEditsBeforeEnd() {
        Toolkit toolkit = Toolkit.create(
                "Bank Reconciliation", null, "POS-SUP-1", "Center", "Finance",
                "Accounting", "Record to Report", "BANK_REC", "Bank Reconciliation",
                false, "AGENT001", START);
        ToolkitSubtask subtask = toolkit.addSubtask("Manual match", null, 1, START);
        TmsSession session = TmsSession.start(
                "TMS-AGENT001-20260805-0002",
                "AGENT001",
                toolkit,
                null,
                null,
                "",
                "",
                START);

        assertThat(session.getProcessedVolume()).isEqualByComparingTo(BigDecimal.ONE);

        session.updateDetails(subtask, BigDecimal.valueOf(8), "INV-200", "filled before end", START.plusSeconds(5));
        session.end(START.plusSeconds(10));

        assertThat(session.getToolkitSubtask()).isEqualTo(subtask);
        assertThat(session.getProcessedVolume()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(session.getReference()).isEqualTo("INV-200");
        assertThat(session.getRemarks()).isEqualTo("filled before end");
        assertThat(session.getStatus()).isEqualTo(TmsSessionStatus.COMPLETED);
    }

    @Test
    void rejectsFractionalOrSubUnitVolume() {
        Toolkit toolkit = Toolkit.create(
                "Bank Reconciliation", null, "POS-SUP-1", "Center", "Finance",
                "Accounting", "Record to Report", "BANK_REC", "Bank Reconciliation",
                false, "AGENT001", START);
        ToolkitSubtask subtask = toolkit.addSubtask("Manual match", null, 1, START);

        assertThatThrownBy(() -> TmsSession.start(
                "TMS-AGENT001-20260805-0003",
                "AGENT001",
                toolkit,
                subtask,
                new BigDecimal("1.5"),
                "INV-1",
                "",
                START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Volume must be a whole number of at least 1.");

        TmsSession session = newSession();
        assertThatThrownBy(() ->
                session.updateDetails(subtask, new BigDecimal("0.5"), "INV-1", "", START.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Volume must be a whole number of at least 1.");
    }

    @Test
    void rejectsInvalidStateTransitions() {
        TmsSession session = newSession();

        assertThatThrownBy(() -> session.resume(START.plusSeconds(1)))
                .isInstanceOf(TmsStateException.class)
                .hasMessage("Only a paused session can be resumed.");
    }

    @Test
    void discardsWithoutDeletingPausedHistory() {
        TmsSession session = newSession();
        session.pause(START.plusSeconds(10));

        session.discard("Invalid sample", START.plusSeconds(20));

        assertThat(session.getStatus()).isEqualTo(TmsSessionStatus.DISCARDED);
        assertThat(session.getDiscardReason()).isEqualTo("Invalid sample");
        assertThat(session.getNetDurationSeconds()).isEqualTo(10);
    }

    private static TmsSession newSession() {
        Toolkit toolkit = Toolkit.create(
                "Bank Reconciliation", null, "POS-SUP-1", "Center", "Finance",
                "Accounting", "Record to Report", "BANK_REC", "Bank Reconciliation",
                false, "AGENT001", START);
        ToolkitSubtask subtask = toolkit.addSubtask("Manual match", null, 1, START);
        return TmsSession.start(
                "TMS-AGENT001-20260805-0001",
                "AGENT001",
                toolkit,
                subtask,
                BigDecimal.valueOf(25),
                "INV-1",
                "",
                START);
    }
}
