-- Fixed Customer Country catalog, seeded once from current Timesheet values.
-- Later syncs do not insert or remove countries.

CREATE TABLE customer_country (
    name VARCHAR(120) PRIMARY KEY
);

INSERT INTO customer_country (name)
SELECT DISTINCT btrim(customer_country)
FROM timesheet_kpi
WHERE customer_country IS NOT NULL
  AND btrim(customer_country) <> ''
ON CONFLICT (name) DO NOTHING;

CREATE TABLE geo_region (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(64),
    CONSTRAINT uk_geo_region_name UNIQUE (name)
);

CREATE TABLE geo_region_country (
    region_id UUID NOT NULL REFERENCES geo_region (id) ON DELETE CASCADE,
    country_name VARCHAR(120) NOT NULL REFERENCES customer_country (name),
    PRIMARY KEY (region_id, country_name),
    CONSTRAINT uk_geo_region_country UNIQUE (country_name)
);

CREATE TABLE geo_cluster (
    id UUID PRIMARY KEY,
    owner_ccgid VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(64),
    CONSTRAINT uk_geo_cluster_owner_name UNIQUE (owner_ccgid, name)
);

CREATE TABLE geo_cluster_country (
    cluster_id UUID NOT NULL REFERENCES geo_cluster (id) ON DELETE CASCADE,
    country_name VARCHAR(120) NOT NULL REFERENCES customer_country (name),
    PRIMARY KEY (cluster_id, country_name)
);

CREATE INDEX ix_geo_cluster_owner ON geo_cluster (owner_ccgid);
