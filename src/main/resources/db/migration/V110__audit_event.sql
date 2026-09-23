CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    subject_ccgid VARCHAR(64) NOT NULL,
    subject_name VARCHAR(200),
    actor_ccgid VARCHAR(64) NOT NULL,
    actor_name VARCHAR(200),
    subject_position_id VARCHAR(80),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_audit_event_entity
    ON audit_event (entity_type, entity_id, occurred_at);

CREATE INDEX ix_audit_event_actor
    ON audit_event (upper(actor_ccgid), occurred_at);

ALTER TABLE tms_session
    ALTER COLUMN agent_ccgid DROP NOT NULL,
    ADD COLUMN position_id VARCHAR(80),
    ADD COLUMN latest_audit_event_id UUID;

ALTER TABLE rst_exercise
    ADD COLUMN latest_audit_event_id UUID;

ALTER TABLE toolkit
    ADD COLUMN latest_audit_event_id UUID;
