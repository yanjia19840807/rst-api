DELETE FROM mail_preference
WHERE mail_type IN ('submission.returned', 'submission.rejected', 'submission.approved');
