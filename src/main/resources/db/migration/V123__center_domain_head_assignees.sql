CREATE TABLE center_domain_head_assignee (
    center VARCHAR(120) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    position_id VARCHAR(80) NOT NULL,
    sort_order INTEGER NOT NULL,
    PRIMARY KEY (center, domain, position_id),
    CONSTRAINT fk_center_domain_head_assignee
        FOREIGN KEY (center, domain) REFERENCES center_domain_head (center, domain)
);

INSERT INTO center_domain_head_assignee (center, domain, position_id, sort_order)
SELECT center, domain, position_id, 0
FROM center_domain_head
WHERE position_id IS NOT NULL
  AND btrim(position_id) <> '';

ALTER TABLE center_domain_head
    DROP COLUMN position_id;
