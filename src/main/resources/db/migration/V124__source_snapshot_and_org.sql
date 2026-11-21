-- One snapshot header for Timesheet and later uploads.
-- Center stays NOT NULL. A global snapshot uses '' so the active
-- unique index still allows only one current row per kind.

ALTER TABLE timesheet_sync_run RENAME TO source_snapshot;
ALTER TABLE timesheet_sync_issue RENAME TO source_issue;
ALTER TABLE timesheet_sync_alert RENAME TO source_alert;
ALTER TABLE timesheet_person RENAME TO org_person;
ALTER TABLE timesheet_position RENAME TO org_position;
ALTER TABLE timesheet_position_parent RENAME TO org_position_parent;
ALTER TABLE timesheet_person_position_role RENAME TO org_seat;
ALTER TABLE timesheet_kpi RENAME TO supervisor_delivery_hc;

DO $$
DECLARE
    cname text;
BEGIN
    FOR cname IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'source_snapshot'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%kind%'
    LOOP
        EXECUTE format('ALTER TABLE source_snapshot DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE source_snapshot
    ADD CONSTRAINT source_snapshot_kind_check
        CHECK (kind IN ('DAILY', 'MONTHLY', 'MYHR', 'SHARED_KPI', 'OGU_DELIVERY'));

ALTER TABLE source_snapshot
    ADD COLUMN period VARCHAR(6);

ALTER TABLE source_issue RENAME COLUMN sync_run_id TO source_snapshot_id;
ALTER TABLE org_person RENAME COLUMN sync_run_id TO source_snapshot_id;
ALTER TABLE org_position RENAME COLUMN sync_run_id TO source_snapshot_id;
ALTER TABLE org_position_parent RENAME COLUMN sync_run_id TO source_snapshot_id;
ALTER TABLE org_seat RENAME COLUMN sync_run_id TO source_snapshot_id;
ALTER TABLE supervisor_delivery_hc RENAME COLUMN sync_run_id TO source_snapshot_id;
ALTER TABLE exercise_toolkit_snapshot RENAME COLUMN timesheet_sync_run_id TO source_snapshot_id;
ALTER TABLE exercise_shared_kpi_line RENAME COLUMN timesheet_sync_run_id TO source_snapshot_id;

CREATE TABLE process_scope (
    source_snapshot_id UUID NOT NULL REFERENCES source_snapshot(id) ON DELETE CASCADE,
    center VARCHAR(120) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    pl3_name VARCHAR(200) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    pl1 VARCHAR(200) NOT NULL,
    pl2 VARCHAR(200) NOT NULL,
    PRIMARY KEY (source_snapshot_id, center, pl3_code)
);

CREATE TABLE supervisor_process (
    source_snapshot_id UUID NOT NULL,
    supervisor_position_id VARCHAR(80) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    center VARCHAR(120) NOT NULL,
    PRIMARY KEY (source_snapshot_id, supervisor_position_id, pl3_code, center),
    CONSTRAINT fk_supervisor_process_scope
        FOREIGN KEY (source_snapshot_id, center, pl3_code)
        REFERENCES process_scope (source_snapshot_id, center, pl3_code) ON DELETE CASCADE,
    CONSTRAINT fk_supervisor_process_snapshot
        FOREIGN KEY (source_snapshot_id)
        REFERENCES source_snapshot(id) ON DELETE CASCADE
);

INSERT INTO process_scope (
    source_snapshot_id, center, pl3_code, pl3_name, domain, pl1, pl2)
SELECT DISTINCT ON (sync_run_id, center, pl3_code)
    sync_run_id, center, pl3_code, pl3_name, domain, pl1, pl2
FROM timesheet_scope
ORDER BY sync_run_id, center, pl3_code;

INSERT INTO supervisor_process (
    source_snapshot_id, supervisor_position_id, pl3_code, center)
SELECT sync_run_id, supervisor_position_id, pl3_code, center
FROM timesheet_scope;

DROP TABLE timesheet_scope;

CREATE INDEX ix_supervisor_process_pl3
    ON supervisor_process (source_snapshot_id, supervisor_position_id, pl3_code);

CREATE TABLE center_delivery_hc (
    source_snapshot_id UUID NOT NULL REFERENCES source_snapshot(id) ON DELETE CASCADE,
    center VARCHAR(120) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    carrier VARCHAR(120) NOT NULL,
    site VARCHAR(80) NOT NULL,
    customer_country VARCHAR(120) NOT NULL,
    hc NUMERIC(18, 6) NOT NULL CHECK (hc >= 0),
    PRIMARY KEY (
        source_snapshot_id, center, pl3_code, carrier, site, customer_country)
);

CREATE TABLE ogu_delivery (
    source_snapshot_id UUID NOT NULL REFERENCES source_snapshot(id) ON DELETE CASCADE,
    organizational_unit_id VARCHAR(80) NOT NULL,
    ou_label VARCHAR(300),
    delivery_coverage VARCHAR(20) NOT NULL,
    PRIMARY KEY (source_snapshot_id, organizational_unit_id)
);

CREATE TABLE gbs_site_code (
    gbs_site VARCHAR(80) PRIMARY KEY,
    site_code VARCHAR(80) NOT NULL
);

INSERT INTO gbs_site_code (gbs_site, site_code) VALUES
    ('CHENNAI-1', 'INMAA'),
    ('CHENNAI-3', 'INMAA'),
    ('CHENNAI-4', 'INMAA'),
    ('MUMBAI-2', 'INBOM'),
    ('MUMBAI-3', 'INBOM'),
    ('CHONGQING', 'CNCKG'),
    ('QINGDAO', 'CNTAO'),
    ('SAN JOSE', 'CRHER'),
    ('BEIRUT', 'LBBRT'),
    ('TALLINN', 'EETLL'),
    ('MANILA-1', 'PHMNL'),
    ('MANILA-2', 'PHMNL'),
    ('LISBOA', 'PTELG');

ALTER INDEX timesheet_kpi_by_center_pkey RENAME TO supervisor_delivery_hc_pkey;
ALTER INDEX ix_timesheet_kpi_scope RENAME TO ix_supervisor_delivery_hc_scope;
ALTER INDEX timesheet_person_pkey RENAME TO org_person_pkey;
ALTER INDEX ix_timesheet_person_center RENAME TO ix_org_person_center;
ALTER INDEX uk_timesheet_person_emp RENAME TO uk_org_person_emp;
ALTER INDEX timesheet_person_position_role_pkey RENAME TO org_seat_pkey;
ALTER INDEX ix_timesheet_seat_ccgid RENAME TO ix_org_seat_ccgid;
ALTER INDEX ix_timesheet_seat_position RENAME TO ix_org_seat_position;
ALTER INDEX timesheet_position_pkey RENAME TO org_position_pkey;
ALTER INDEX timesheet_position_parent_pkey RENAME TO org_position_parent_pkey;
ALTER INDEX ix_timesheet_position_parent_parent RENAME TO ix_org_position_parent_parent;
ALTER INDEX timesheet_sync_alert_pkey RENAME TO source_alert_pkey;
ALTER INDEX timesheet_sync_issue_pkey RENAME TO source_issue_pkey;
ALTER INDEX ix_timesheet_sync_issue_run RENAME TO ix_source_issue_run;
ALTER INDEX timesheet_sync_run_pkey RENAME TO source_snapshot_pkey;
ALTER INDEX uk_timesheet_one_active_run_per_kind_center RENAME TO uk_source_snapshot_one_active;
ALTER INDEX uk_timesheet_sync_attempt RENAME TO uk_source_snapshot_attempt;
