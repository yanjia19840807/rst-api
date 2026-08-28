package com.cmacgm.gbs.rst.api.timesheet.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.mail.application.MailNotificationService;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncAlert;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncAlertRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;

/**
 * Sends a short failure mail after a Timesheet sync run is persisted as FAILED.
 * Delivery errors are logged and never change the run status.
 */
@Service
public class TimesheetSyncAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncAlertNotifier.class);

    private final TimesheetSyncAlertRepository alerts;
    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetSyncIssueRepository issues;
    private final MicrosoftGraphService graph;
    private final MailNotificationService mail;

    /**
     * @param alerts alert config
     * @param syncRuns run headers
     * @param issues issue rows
     * @param graph Graph sendMail
     * @param mail LTH SSO recipients
     */
    public TimesheetSyncAlertNotifier(
            TimesheetSyncAlertRepository alerts,
            TimesheetSyncRunRepository syncRuns,
            TimesheetSyncIssueRepository issues,
            MicrosoftGraphService graph,
            MailNotificationService mail) {
        this.alerts = alerts;
        this.syncRuns = syncRuns;
        this.issues = issues;
        this.graph = graph;
        this.mail = mail;
    }

    /**
     * Reads the saved FAILED run and emails configured recipients off the sync thread.
     *
     * @param runId failed run
     */
    public void notifyFailed(UUID runId) {
        if (runId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                send(runId);
            } catch (RuntimeException ex) {
                log.warn("Timesheet fail email was not sent for {}: {}", runId, ex.getMessage());
            }
        });
    }

    private void send(UUID runId) {
        TimesheetSyncRun run = syncRuns.findById(runId).orElse(null);
        if (run == null || !"FAILED".equals(run.getStatus())) {
            return;
        }
        TimesheetSyncAlert config = alerts.findById(TimesheetSyncAlert.SINGLETON_ID)
                .orElseGet(TimesheetSyncAlert::disabled);
        Set<String> recipients = new LinkedHashSet<>();
        if (config.isEnabled()) {
            recipients.addAll(TimesheetSyncAdminService.parseRecipients(config.getRecipients()));
        }
        if (mail != null) {
            recipients.addAll(mail.timesheetSyncFailedAddresses());
        }
        if (recipients.isEmpty()) {
            return;
        }
        long issueCount = issues.countBySyncRunId(runId);
        String kind = blankToDash(run.getKind());
        String date = run.getSyncDate() == null ? "—" : run.getSyncDate().toString();
        String subject = "RST Timesheet sync failed: " + kind + " " + date;
        graph.sendMail(subject, html(run, issueCount), List.copyOf(recipients));
    }

    private static String html(TimesheetSyncRun run, long issueCount) {
        String[][] rows = {
            {"Kind", blankToDash(run.getKind())},
            {"Date", run.getSyncDate() == null ? "—" : run.getSyncDate().toString()},
            {"Status", blankToDash(run.getStatus())},
            {"Error Code", blankToDash(run.getErrorCode())},
            {"Message", blankToDash(run.getErrorMessage())},
            {"File", blankToDash(run.getSourceFileName())},
            {"Source", blankToDash(run.getSourceType())},
            {"Run id", run.getId() == null ? "—" : run.getId().toString()},
            {"Issue count", Long.toString(issueCount)},
        };
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<body>\n")
                .append("<div style=\"font-size: 16px;\">A Timesheet sync run failed.</div>")
                .append("<table style=\"border-width: 1px;border-collapse: collapse;margin: 12px 0;width: 100%;\">\n");
        for (String[] row : rows) {
            html.append("    <tr>\n")
                    .append("        <th style=\"border-width:1px;padding:8px;border-style:solid;background-color:#dedede;text-align:left;width:28%;\">")
                    .append(escape(row[0]))
                    .append("</th>\n")
                    .append("        <td style=\"border-width:1px;padding:8px;border-style:solid;\">")
                    .append(escape(row[1]))
                    .append("</td>\n")
                    .append("    </tr>\n");
        }
        html.append("</table>\n</body>\n</html>");
        return html.toString();
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
