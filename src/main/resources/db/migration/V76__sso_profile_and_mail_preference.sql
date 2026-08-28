CREATE TABLE sso_profile (
    ccgid VARCHAR(32) PRIMARY KEY,
    email VARCHAR(254),
    center VARCHAR(120),
    role VARCHAR(16) NOT NULL,
    seen_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_sso_profile_lth_center ON sso_profile (center)
    WHERE role = 'LTH';

CREATE TABLE mail_preference (
    ccgid VARCHAR(32) NOT NULL,
    mail_type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (ccgid, mail_type)
);
