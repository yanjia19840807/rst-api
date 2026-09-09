-- Approver Reject is gone. Leftover REJECTED visits become RETURNED (same as V59 actors).
-- Finished-only-because-of-reject processes reopen as OPEN so they match Return.

UPDATE task_actor SET status = 'RETURNED' WHERE status = 'REJECTED';
UPDATE process_task SET status = 'RETURNED' WHERE status = 'REJECTED';

UPDATE process_instance p
SET status = 'OPEN',
    current_step = NULL
WHERE p.status = 'FINISHED'
  AND EXISTS (
      SELECT 1
      FROM rst_exercise e
      WHERE e.id = p.exercise_id
        AND e.deleted_at IS NULL
  )
  AND NOT EXISTS (
      SELECT 1
      FROM process_task t
      WHERE t.instance_id = p.id
        AND t.node_code = 'LTH'
        AND t.status = 'APPROVED'
  )
  AND EXISTS (
      SELECT 1
      FROM process_task t
      WHERE t.instance_id = p.id
        AND t.status = 'RETURNED'
  );

ALTER TABLE process_task DROP CONSTRAINT IF EXISTS process_task_status_check;
ALTER TABLE process_task ADD CONSTRAINT process_task_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'WITHDRAWN'));

ALTER TABLE task_actor DROP CONSTRAINT IF EXISTS task_actor_status_check;
ALTER TABLE task_actor ADD CONSTRAINT task_actor_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'WITHDRAWN', 'CANCELLED'));
