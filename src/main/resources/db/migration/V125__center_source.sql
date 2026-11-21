-- Which source a center reads. One center, one mode.
-- Import uses this to decide which centers to take from the global files.

CREATE TABLE center_source (
    center VARCHAR(120) PRIMARY KEY,
    source_mode VARCHAR(16) NOT NULL,
    CONSTRAINT center_source_mode_check CHECK (source_mode IN ('TIMESHEET', 'MYHR_SKPI'))
);

INSERT INTO center_source (center, source_mode) VALUES
    ('GBS CHINA', 'TIMESHEET'),
    ('GBS COSTA RICA', 'TIMESHEET'),
    ('GBS INDIA', 'MYHR_SKPI'),
    ('GBS LEBANON', 'MYHR_SKPI'),
    ('GBS ESTONIA', 'MYHR_SKPI'),
    ('GBS PHILIPPINES', 'MYHR_SKPI'),
    ('GBS PORTUGAL', 'MYHR_SKPI');

ALTER TABLE gbs_site_code ADD COLUMN center VARCHAR(120);

UPDATE gbs_site_code SET center = 'GBS INDIA'
WHERE gbs_site IN ('CHENNAI-1', 'CHENNAI-3', 'CHENNAI-4', 'MUMBAI-2', 'MUMBAI-3');
UPDATE gbs_site_code SET center = 'GBS CHINA'
WHERE gbs_site IN ('CHONGQING', 'QINGDAO');
UPDATE gbs_site_code SET center = 'GBS COSTA RICA'
WHERE gbs_site = 'SAN JOSE';
UPDATE gbs_site_code SET center = 'GBS LEBANON'
WHERE gbs_site = 'BEIRUT';
UPDATE gbs_site_code SET center = 'GBS ESTONIA'
WHERE gbs_site = 'TALLINN';
UPDATE gbs_site_code SET center = 'GBS PHILIPPINES'
WHERE gbs_site IN ('MANILA-1', 'MANILA-2');
UPDATE gbs_site_code SET center = 'GBS PORTUGAL'
WHERE gbs_site = 'LISBOA';

ALTER TABLE gbs_site_code ALTER COLUMN center SET NOT NULL;

ALTER TABLE org_person ADD COLUMN organizational_unit_id VARCHAR(80);

ALTER TABLE ogu_delivery
    ADD CONSTRAINT ogu_delivery_coverage_check
        CHECK (delivery_coverage IN ('DELIVERY', 'NON-DELIVERY'));
