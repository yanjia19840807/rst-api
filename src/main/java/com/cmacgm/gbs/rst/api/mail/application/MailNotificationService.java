package com.cmacgm.gbs.rst.api.mail.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.mail.domain.MailType;
import com.cmacgm.gbs.rst.api.mail.domain.SsoProfile;
import com.cmacgm.gbs.rst.api.mail.persistence.SsoProfileRepository;

/**
 * Resolves SSO addresses, honors switches, and sends Graph mail off the request thread.
 */
@Service
public class MailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);

    private final SsoProfileRepository profiles;
    private final MailPreferenceService preferences;
    private final MailAddressLookup addresses;
    private final MicrosoftGraphService graph;

    /**
     * @param profiles LTH / ADMIN directory
     * @param preferences switches
     * @param addresses Timesheet emp_email
     * @param graph sendMail
     */
    public MailNotificationService(
            SsoProfileRepository profiles,
            MailPreferenceService preferences,
            MailAddressLookup addresses,
            MicrosoftGraphService graph) {
        this.profiles = profiles;
        this.preferences = preferences;
        this.addresses = addresses;
        this.graph = graph;
    }

    /**
     * Awaiting-approval mail to a Timesheet occupant (Manager / CDH).
     *
     * @param ccgid occupant
     * @param exercise case
     */
    public void notifyApprovalRequested(String ccgid, RstExercise exercise) {
        sendToCcgids(
                MailType.APPROVAL_REQUESTED,
                List.of(ccgid),
                subject("RST approval needed", exercise),
                body("An Exercise is waiting for your approval.", exercise, null));
    }

    /**
     * Awaiting-approval mail to the single LTH of the Exercise Center.
     *
     * @param center toolkit center
     * @param exercise case
     */
    public void notifyLthApprovalRequested(String center, RstExercise exercise) {
        SsoProfile lth = latestLth(center);
        if (lth == null) {
            log.info("LTH approval mail skipped: no SSO profile for center {}", center);
            return;
        }
        sendToCcgids(
                MailType.APPROVAL_REQUESTED,
                List.of(lth.getCcgid()),
                subject("RST approval needed", exercise),
                body("An Exercise is waiting for your approval.", exercise, null));
    }

    /**
     * Returned / rejected / approved mail. One preference switch covers all three.
     *
     * @param outcome returned / rejected / approved
     * @param ownerCcgid supervisor
     * @param exercise case
     * @param comments decision comments
     */
    public void notifyOwner(OwnerOutcome outcome, String ownerCcgid, RstExercise exercise, String comments) {
        if (outcome == null) {
            return;
        }
        sendToCcgids(
                MailType.SUBMISSION_OUTCOME,
                List.of(ownerCcgid),
                subject(outcome.subject(), exercise),
                body(outcome.headline(), exercise, comments));
    }

    /**
     * Supervisor outcome copy. Preference is always {@link MailType#SUBMISSION_OUTCOME}.
     */
    public enum OwnerOutcome {
        RETURNED("RST Exercise returned", "Your Exercise was returned."),
        REJECTED("RST Exercise rejected", "Your Exercise was rejected."),
        APPROVED("RST Exercise approved", "Your Exercise was approved.");

        private final String subject;
        private final String headline;

        OwnerOutcome(String subject, String headline) {
            this.subject = subject;
            this.headline = headline;
        }

        String subject() {
            return subject;
        }

        String headline() {
            return headline;
        }
    }

    /**
     * Timesheet addresses of LTHs and Admins who still want sync-failure mail.
     *
     * @return emails
     */
    public List<String> timesheetSyncFailedAddresses() {
        Set<String> emails = new LinkedHashSet<>();
        for (SsoProfile profile : profiles.findByRole("LTH")) {
            addIfSendable(emails, MailType.TIMESHEET_SYNC_FAILED, profile.getCcgid());
        }
        for (SsoProfile profile : profiles.findByRole("ADMIN")) {
            addIfSendable(emails, MailType.TIMESHEET_SYNC_FAILED, profile.getCcgid());
        }
        return List.copyOf(emails);
    }

    /**
     * Sends to opted-in CCGIDs that have a Timesheet email.
     *
     * @param type mail type
     * @param ccgids recipients
     * @param subject subject
     * @param html body
     */
    public void sendToCcgids(MailType type, List<String> ccgids, String subject, String html) {
        if (type == null || ccgids == null || ccgids.isEmpty()) {
            return;
        }
        Set<String> emails = new LinkedHashSet<>();
        for (String raw : ccgids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            addIfSendable(emails, type, raw.trim().toUpperCase(Locale.ROOT));
        }
        dispatch(subject, html, List.copyOf(emails));
    }

    /**
     * @param subject subject
     * @param html body
     * @param to addresses
     */
    public void dispatch(String subject, String html, List<String> to) {
        if (to == null || to.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                graph.sendMail(subject, html, to);
            } catch (RuntimeException ex) {
                log.warn("RST mail was not sent: {}", ex.getMessage());
            }
        });
    }

    private void addIfSendable(Set<String> emails, MailType type, String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            log.info("Mail {} skipped: no CCGID", type == null ? "?" : type.id());
            return;
        }
        String email = addresses.emailOf(ccgid);
        if (email == null || email.isBlank()) {
            log.info("Mail {} skipped: {} has no Timesheet email", type.id(), ccgid);
            return;
        }
        if (!preferences.isEnabled(ccgid, type)) {
            return;
        }
        emails.add(email.trim());
    }

    private SsoProfile latestLth(String center) {
        if (center == null || center.isBlank()) {
            return null;
        }
        List<SsoProfile> matches = profiles.findByRoleAndCenterIgnoreCaseOrderBySeenAtDesc("LTH", center.trim());
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private static String subject(String prefix, RstExercise exercise) {
        String code = exercise == null || exercise.getExerciseCode() == null ? "" : exercise.getExerciseCode();
        return code.isBlank() ? prefix : prefix + ": " + code;
    }

    static String body(String headline, RstExercise exercise, String comments) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Exercise", exercise == null ? "—" : dash(exercise.getExerciseCode())});
        if (exercise != null && exercise.getToolkitSnapshot() != null) {
            rows.add(new String[] {"Center", dash(exercise.getToolkitSnapshot().getCenter())});
            rows.add(new String[] {"Domain", dash(exercise.getToolkitSnapshot().getDomain())});
        }
        if (comments != null && !comments.isBlank()) {
            rows.add(new String[] {"Comments", comments});
        }
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<body>\n")
                .append("<div style=\"font-size: 16px;\">")
                .append(escape(headline))
                .append("</div>")
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

    private static String dash(String value) {
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
