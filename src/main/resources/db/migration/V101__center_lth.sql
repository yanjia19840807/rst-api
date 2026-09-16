CREATE TABLE center_lth (
    center VARCHAR(120) NOT NULL,
    position_id VARCHAR(80) NOT NULL,
    updated_by VARCHAR(32),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (center)
);
