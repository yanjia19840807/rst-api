-- Position coverage: a parent assigns one or more people to a direct child position.
-- All roles on that position are covered together. The operator is recorded separately
-- from the position's current occupant.

ALTER TABLE rst_delegation
    ADD COLUMN subject_position_id VARCHAR(80),
    ADD COLUMN assigned_by_ccgid VARCHAR(64),
    ADD COLUMN assigned_by_name VARCHAR(200);

UPDATE rst_delegation
SET assigned_by_ccgid = delegator_ccgid,
    assigned_by_name = delegator_name
WHERE assigned_by_ccgid IS NULL;

CREATE UNIQUE INDEX ux_rst_delegation_open_position_delegate
    ON rst_delegation (subject_position_id, upper(delegate_ccgid))
    WHERE status IN ('PENDING', 'ACTIVE')
      AND subject_position_id IS NOT NULL;
