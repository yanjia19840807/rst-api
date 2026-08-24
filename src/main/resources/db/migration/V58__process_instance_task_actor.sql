-- Replace Step+Action with process_task + task_actor (Activiti-lite, no process definition).

CREATE TABLE process_instance (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL UNIQUE REFERENCES rst_exercise(id),
    submitted_by_ccgid VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    remarks TEXT,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('OPEN', 'RETURNED', 'WITHDRAWN', 'APPROVED')),
    current_step SMALLINT,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO process_instance (
    id, exercise_id, submitted_by_ccgid, submitted_at, remarks, status, current_step, version)
SELECT id, exercise_id, submitted_by_ccgid, submitted_at, remarks, status, current_step, version
FROM workflow_instance;

CREATE TABLE process_task (
    id UUID PRIMARY KEY,
    instance_id UUID NOT NULL REFERENCES process_instance(id),
    node_code VARCHAR(20) NOT NULL
        CHECK (node_code IN ('SUBMIT', 'MANAGER', 'CDH', 'LTH')),
    node_order SMALLINT NOT NULL,
    completion_strategy VARCHAR(10) NOT NULL
        CHECK (completion_strategy IN ('OR', 'AND')),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX ix_process_task_instance ON process_task(instance_id);

CREATE TABLE task_actor (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES process_task(id),
    actor_type VARCHAR(20) NOT NULL
        CHECK (actor_type IN ('INITIATOR', 'APPROVER', 'DELEGATE')),
    position_id VARCHAR(80),
    ccgid VARCHAR(64),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')),
    comments TEXT,
    acted_at TIMESTAMPTZ,
    request_id UUID
);

CREATE INDEX ix_task_actor_task ON task_actor(task_id);
CREATE INDEX ix_task_actor_pending ON task_actor(position_id, status);
CREATE UNIQUE INDEX uk_task_actor_request ON task_actor(request_id) WHERE request_id IS NOT NULL;

-- First submit as a completed SUBMIT node.
INSERT INTO process_task (id, instance_id, node_code, node_order, completion_strategy, status, created_at, completed_at)
SELECT gen_random_uuid(), w.id, 'SUBMIT', 0, 'OR', 'COMPLETED', w.submitted_at, w.submitted_at
FROM workflow_instance w;

INSERT INTO task_actor (id, task_id, actor_type, position_id, ccgid, status, comments, acted_at, request_id)
SELECT gen_random_uuid(), t.id, 'INITIATOR', NULL, w.submitted_by_ccgid, 'APPROVED', w.remarks, w.submitted_at,
       (SELECT a.request_id FROM workflow_action a
        WHERE a.workflow_instance_id = w.id AND a.action_type = 'SUBMIT'
        ORDER BY a.action_seq LIMIT 1)
FROM process_task t
JOIN workflow_instance w ON w.id = t.instance_id
WHERE t.node_code = 'SUBMIT';

-- Review hops from the current step assignment (one row per hop).
INSERT INTO process_task (id, instance_id, node_code, node_order, completion_strategy, status, created_at, completed_at)
SELECT s.id,
       s.workflow_instance_id,
       CASE s.step_no WHEN 1 THEN 'MANAGER' WHEN 2 THEN 'CDH' WHEN 3 THEN 'LTH' END,
       s.step_no,
       'OR',
       CASE s.routing_status
           WHEN 'READY' THEN 'PENDING'
           WHEN 'ACTED' THEN 'COMPLETED'
           ELSE 'CANCELLED'
       END,
       COALESCE(s.resolved_at, CURRENT_TIMESTAMP),
       CASE WHEN s.routing_status IN ('ACTED', 'INVALIDATED') THEN s.resolved_at ELSE NULL END
FROM workflow_step_assignment s;

INSERT INTO task_actor (id, task_id, actor_type, position_id, ccgid, status, comments, acted_at, request_id)
SELECT gen_random_uuid(), s.id, 'APPROVER', s.assignee_position_id, s.assignee_ccgid, 'PENDING', NULL, NULL, NULL
FROM workflow_step_assignment s
WHERE s.routing_status = 'READY';

INSERT INTO task_actor (id, task_id, actor_type, position_id, ccgid, status, comments, acted_at, request_id)
SELECT a.id, s.id, 'APPROVER', s.assignee_position_id, a.actor_ccgid,
       CASE a.action_type WHEN 'APPROVE' THEN 'APPROVED' WHEN 'RETURN' THEN 'REJECTED' END,
       a.comments, a.action_at, a.request_id
FROM workflow_action a
JOIN workflow_step_assignment s
  ON s.workflow_instance_id = a.workflow_instance_id
 AND s.step_no = a.step_no
WHERE a.action_type IN ('APPROVE', 'RETURN');

INSERT INTO task_actor (id, task_id, actor_type, position_id, ccgid, status, comments, acted_at, request_id)
SELECT a.id, COALESCE(
           (SELECT s.id FROM workflow_step_assignment s
            WHERE s.workflow_instance_id = a.workflow_instance_id
              AND s.step_no = a.step_no
            LIMIT 1),
           (SELECT t.id FROM process_task t
            WHERE t.instance_id = a.workflow_instance_id AND t.node_code = 'SUBMIT' LIMIT 1)
       ),
       'INITIATOR', NULL, a.actor_ccgid, 'WITHDRAWN', a.comments, a.action_at, a.request_id
FROM workflow_action a
WHERE a.action_type = 'WITHDRAW'
  AND COALESCE(
           (SELECT s.id FROM workflow_step_assignment s
            WHERE s.workflow_instance_id = a.workflow_instance_id AND s.step_no = a.step_no LIMIT 1),
           (SELECT t.id FROM process_task t
            WHERE t.instance_id = a.workflow_instance_id AND t.node_code = 'SUBMIT' LIMIT 1)
       ) IS NOT NULL;

ALTER TABLE submission_scope ADD COLUMN IF NOT EXISTS process_instance_id UUID;
UPDATE submission_scope SET process_instance_id = workflow_instance_id WHERE process_instance_id IS NULL;
DELETE FROM submission_scope WHERE process_instance_id IS NULL;
ALTER TABLE submission_scope ALTER COLUMN process_instance_id SET NOT NULL;
ALTER TABLE submission_scope DROP CONSTRAINT IF EXISTS uk_submission_scope;
ALTER TABLE submission_scope DROP CONSTRAINT IF EXISTS submission_scope_workflow_instance_id_fkey;
ALTER TABLE submission_scope DROP COLUMN IF EXISTS workflow_instance_id;
ALTER TABLE submission_scope ADD CONSTRAINT uk_submission_scope UNIQUE (process_instance_id, scope_key);
ALTER TABLE submission_scope ADD CONSTRAINT submission_scope_process_instance_id_fkey
    FOREIGN KEY (process_instance_id) REFERENCES process_instance(id);

DROP TABLE IF EXISTS workflow_action;
DROP TABLE IF EXISTS workflow_step_assignment;
DROP TABLE IF EXISTS workflow_instance;
