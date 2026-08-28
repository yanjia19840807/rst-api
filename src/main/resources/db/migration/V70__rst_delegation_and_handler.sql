CREATE TABLE rst_delegation (
    id UUID PRIMARY KEY,
    delegator_ccgid VARCHAR(64) NOT NULL,
    delegator_name VARCHAR(200),
    delegate_ccgid VARCHAR(64) NOT NULL,
    delegate_name VARCHAR(200),
    delegator_roles VARCHAR(200) NOT NULL,
    delegator_center VARCHAR(120),
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(500),
    CONSTRAINT rst_delegation_status_chk CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT rst_delegation_not_self_chk CHECK (upper(delegator_ccgid) <> upper(delegate_ccgid)),
    CONSTRAINT rst_delegation_range_chk CHECK (valid_until > valid_from)
);

CREATE UNIQUE INDEX ux_rst_delegation_open_pair
    ON rst_delegation (upper(delegator_ccgid), upper(delegate_ccgid))
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE INDEX ix_rst_delegation_delegator ON rst_delegation (upper(delegator_ccgid), status);
CREATE INDEX ix_rst_delegation_delegate ON rst_delegation (upper(delegate_ccgid), status);

ALTER TABLE process_instance
    ADD COLUMN submitted_by_name VARCHAR(200),
    ADD COLUMN submitted_by_actor_ccgid VARCHAR(64),
    ADD COLUMN submitted_by_actor_name VARCHAR(200);

ALTER TABLE task_actor
    ADD COLUMN subject_name VARCHAR(200),
    ADD COLUMN acted_by_ccgid VARCHAR(64),
    ADD COLUMN acted_by_name VARCHAR(200);
