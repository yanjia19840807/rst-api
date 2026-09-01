-- Delivery HC is Supervisor × PL3 × Center × carrier × site × country.

CREATE TABLE timesheet_kpi_by_center (
    sync_run_id UUID NOT NULL REFERENCES timesheet_sync_run(id) ON DELETE CASCADE,
    supervisor_position_id VARCHAR(80) NOT NULL,
    pl3_code VARCHAR(80) NOT NULL,
    center VARCHAR(120) NOT NULL,
    carrier VARCHAR(120) NOT NULL,
    site VARCHAR(80) NOT NULL,
    customer_country VARCHAR(120) NOT NULL,
    hc NUMERIC(18, 6) NOT NULL CHECK (hc >= 0),
    PRIMARY KEY (
        sync_run_id,
        supervisor_position_id,
        pl3_code,
        center,
        carrier,
        site,
        customer_country
    )
);

INSERT INTO timesheet_kpi_by_center
SELECT DISTINCT ON (
        k.sync_run_id,
        k.supervisor_position_id,
        k.pl3_code,
        k.carrier,
        k.site,
        k.customer_country
    )
    k.sync_run_id,
    k.supervisor_position_id,
    k.pl3_code,
    COALESCE(s.center, ''),
    k.carrier,
    k.site,
    k.customer_country,
    k.hc
FROM timesheet_kpi k
LEFT JOIN timesheet_scope s
  ON s.sync_run_id = k.sync_run_id
 AND s.supervisor_position_id = k.supervisor_position_id
 AND s.pl3_code = k.pl3_code
ORDER BY
    k.sync_run_id,
    k.supervisor_position_id,
    k.pl3_code,
    k.carrier,
    k.site,
    k.customer_country,
    s.center;

DROP TABLE timesheet_kpi;

ALTER TABLE timesheet_kpi_by_center RENAME TO timesheet_kpi;

CREATE INDEX ix_timesheet_kpi_scope
    ON timesheet_kpi (sync_run_id, supervisor_position_id, pl3_code);
