package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.IssueView;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
import org.junit.jupiter.api.Test;

class TimesheetSyncAdminIssueMergeTests {

    @Test
    void mergesMissingFieldsOnTheSameSourceRow() {
        UUID runId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        List<IssueView> merged = TimesheetSyncAdminService.mergeBySourceRow(List.of(
                missing(runId, "emp_ccgid", 12, now),
                missing(runId, "emp_name", 12, now),
                missing(runId, "supervisor_ccgid", 12, now),
                missing(runId, "emp_ccgid", 13, now)));

        assertThat(merged)
                .extracting(IssueView::sourceRow, IssueView::code, IssueView::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                12,
                                "MISSING_FIELD",
                                "Missing emp_ccgid, emp_name, supervisor_ccgid."),
                        org.assertj.core.groups.Tuple.tuple(13, "MISSING_FIELD", "Missing emp_ccgid."));
    }

    @Test
    void keepsFileLevelIssuesSeparate() {
        UUID runId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        List<IssueView> merged = TimesheetSyncAdminService.mergeBySourceRow(List.of(
                TimesheetSyncIssue.error(
                        runId,
                        TimesheetSyncErrorCode.EMPTY_FILE,
                        "Daily file produced no person or position rows.",
                        null,
                        null,
                        null,
                        null,
                        null,
                        now),
                TimesheetSyncIssue.error(
                        runId,
                        TimesheetSyncErrorCode.HIERARCHY_CONFLICT,
                        "position_id 174050 role SUPERVISOR maps to multiple parent_position_id: 1, 2",
                        null,
                        null,
                        "174050",
                        null,
                        null,
                        now)));

        assertThat(merged).hasSize(2);
        assertThat(merged).extracting(IssueView::sourceRow).containsOnly((Integer) null);
        assertThat(merged).extracting(IssueView::code).containsExactly("EMPTY_FILE", "HIERARCHY_CONFLICT");
    }

    @Test
    void joinsDifferentCodesOnTheSameRow() {
        UUID runId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        List<IssueView> merged = TimesheetSyncAdminService.mergeBySourceRow(List.of(
                missing(runId, "hc", 8, now),
                TimesheetSyncIssue.error(
                        runId,
                        TimesheetSyncErrorCode.INVALID_HC,
                        "hc is not a valid number.",
                        null,
                        "S00000001",
                        "EMP-POS-1",
                        "PL3",
                        8,
                        now)));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).code()).isEqualTo("MISSING_FIELD, INVALID_HC");
        assertThat(merged.get(0).message()).isEqualTo("Missing hc. hc is not a valid number.");
        assertThat(merged.get(0).empCcgid()).isEqualTo("S00000001");
    }

    private static TimesheetSyncIssue missing(UUID runId, String field, int sourceRow, Instant now) {
        return TimesheetSyncIssue.error(
                runId,
                TimesheetSyncErrorCode.MISSING_FIELD,
                "Missing " + field + ".",
                null,
                null,
                null,
                null,
                sourceRow,
                now);
    }
}
