-- V59 on already-applied databases still allows process_task.CANCELLED.
-- TaskStatus no longer has CANCELLED; historical rows were remapped to WITHDRAWN.

UPDATE process_task SET status = 'WITHDRAWN' WHERE status = 'CANCELLED';

ALTER TABLE process_task DROP CONSTRAINT IF EXISTS process_task_status_check;
ALTER TABLE process_task ADD CONSTRAINT process_task_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'REJECTED', 'WITHDRAWN'));
