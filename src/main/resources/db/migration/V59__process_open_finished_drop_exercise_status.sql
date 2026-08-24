-- Process is only OPEN | FINISHED. Outcomes live on process_task / task_actor.
-- Exercise.workflow_status is derived from the process and is no longer stored.

UPDATE task_actor SET status = 'RETURNED' WHERE status = 'REJECTED';

UPDATE process_task SET status = 'APPROVED' WHERE status = 'COMPLETED';

UPDATE process_task t
SET status = 'RETURNED'
FROM task_actor a
WHERE a.task_id = t.id AND a.status = 'RETURNED';

UPDATE process_task t
SET status = 'WITHDRAWN'
FROM task_actor a
WHERE a.task_id = t.id AND a.status = 'WITHDRAWN';

UPDATE process_task SET status = 'WITHDRAWN' WHERE status = 'CANCELLED';

UPDATE process_instance
SET status = 'FINISHED'
WHERE status IN ('RETURNED', 'WITHDRAWN', 'APPROVED');

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class t ON t.oid = con.conrelid
        WHERE con.contype = 'c' AND t.relname = 'process_instance'
    LOOP
        EXECUTE format('ALTER TABLE process_instance DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE process_instance ADD CONSTRAINT process_instance_status_check
    CHECK (status IN ('OPEN', 'FINISHED'));

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class t ON t.oid = con.conrelid
        WHERE con.contype = 'c' AND t.relname = 'process_task'
    LOOP
        EXECUTE format('ALTER TABLE process_task DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE process_task ADD CONSTRAINT process_task_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'REJECTED', 'WITHDRAWN'));
ALTER TABLE process_task ADD CONSTRAINT process_task_node_check
    CHECK (node_code IN ('SUBMIT', 'MANAGER', 'CDH', 'LTH'));
ALTER TABLE process_task ADD CONSTRAINT process_task_strategy_check
    CHECK (completion_strategy IN ('OR', 'AND'));

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class t ON t.oid = con.conrelid
        WHERE con.contype = 'c' AND t.relname = 'task_actor'
    LOOP
        EXECUTE format('ALTER TABLE task_actor DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE task_actor ADD CONSTRAINT task_actor_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'REJECTED', 'WITHDRAWN', 'CANCELLED'));
ALTER TABLE task_actor ADD CONSTRAINT task_actor_type_check
    CHECK (actor_type IN ('INITIATOR', 'APPROVER', 'DELEGATE'));

DO $$
DECLARE
    index_name text;
BEGIN
    FOR index_name IN
        SELECT i.relname
        FROM pg_index x
        JOIN pg_class i ON i.oid = x.indexrelid
        JOIN pg_class t ON t.oid = x.indrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY (x.indkey)
        WHERE t.relname = 'rst_exercise' AND a.attname = 'workflow_status'
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', index_name);
    END LOOP;
END $$;

ALTER TABLE rst_exercise DROP CONSTRAINT IF EXISTS rst_exercise_workflow_status_check;
ALTER TABLE rst_exercise DROP COLUMN IF EXISTS workflow_status;
