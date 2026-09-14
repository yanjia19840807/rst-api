-- Supervisor Withdraw is gone. Delete WITHDRAWN visits instead of remapping them.
-- If the latest review is WITHDRAWN, drop that unfinished submit cycle so the
-- previous hop cannot look like a final APPROVED.

DELETE FROM task_actor
WHERE task_id IN (
    SELECT t.id
    FROM process_task t
    WHERE t.node_code <> 'SUBMIT'
      AND t.instance_id IN (
          SELECT latest.instance_id
          FROM (
              SELECT DISTINCT ON (instance_id)
                  instance_id,
                  status
              FROM process_task
              WHERE node_code <> 'SUBMIT'
              ORDER BY instance_id, created_at DESC, id DESC
          ) latest
          WHERE latest.status = 'WITHDRAWN'
      )
      AND t.created_at >= (
          SELECT MAX(s.created_at)
          FROM process_task s
          WHERE s.instance_id = t.instance_id
            AND s.node_code = 'SUBMIT'
      )
);

DELETE FROM process_task t
WHERE t.node_code <> 'SUBMIT'
  AND t.instance_id IN (
      SELECT latest.instance_id
      FROM (
          SELECT DISTINCT ON (instance_id)
              instance_id,
              status
          FROM process_task
          WHERE node_code <> 'SUBMIT'
          ORDER BY instance_id, created_at DESC, id DESC
      ) latest
      WHERE latest.status = 'WITHDRAWN'
  )
  AND t.created_at >= (
      SELECT MAX(s.created_at)
      FROM process_task s
      WHERE s.instance_id = t.instance_id
        AND s.node_code = 'SUBMIT'
  );

DELETE FROM task_actor
WHERE status = 'WITHDRAWN'
   OR task_id IN (SELECT id FROM process_task WHERE status = 'WITHDRAWN');

DELETE FROM process_task WHERE status = 'WITHDRAWN';

ALTER TABLE process_task DROP CONSTRAINT IF EXISTS process_task_status_check;
ALTER TABLE process_task ADD CONSTRAINT process_task_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED'));

ALTER TABLE task_actor DROP CONSTRAINT IF EXISTS task_actor_status_check;
ALTER TABLE task_actor ADD CONSTRAINT task_actor_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'CANCELLED'));
