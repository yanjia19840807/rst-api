-- One workflow switch: workflow.notification.
-- Promote leftover approval.requested / submission.outcome (and aliases) then drop them.

INSERT INTO mail_preference (ccgid, mail_type, enabled)
SELECT legacy.ccgid,
       'workflow.notification',
       bool_and(legacy.enabled)
FROM mail_preference legacy
WHERE legacy.mail_type IN (
        'approval.requested',
        'APPROVAL_REQUESTED',
        'submission.outcome',
        'SUBMISSION_OUTCOME',
        'submission.returned',
        'SUBMISSION_RETURNED',
        'submission.approved',
        'SUBMISSION_APPROVED',
        'WORKFLOW')
  AND NOT EXISTS (
        SELECT 1
        FROM mail_preference unified
        WHERE unified.ccgid = legacy.ccgid
          AND unified.mail_type = 'workflow.notification')
GROUP BY legacy.ccgid;

DELETE FROM mail_preference
WHERE mail_type IN (
        'approval.requested',
        'APPROVAL_REQUESTED',
        'submission.outcome',
        'SUBMISSION_OUTCOME',
        'submission.returned',
        'SUBMISSION_RETURNED',
        'submission.approved',
        'SUBMISSION_APPROVED',
        'WORKFLOW');
